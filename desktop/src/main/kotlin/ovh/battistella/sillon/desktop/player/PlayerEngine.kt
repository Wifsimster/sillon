package ovh.battistella.sillon.desktop.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.TrackGain
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cbrt
import kotlin.math.roundToLong

enum class RepeatMode { OFF, ALL, ONE }

data class EngineState(
    val queue: List<PlayableTrack> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1f,
) {
    val current: PlayableTrack? get() = queue.getOrNull(index)
}

data class Transition(
    val previous: PlayableTrack?,
    val previousPositionMs: Long,
    val current: PlayableTrack?,
    val automatic: Boolean,
)

interface EngineListener {
    fun onTransition(transition: Transition) {}
    fun onFailed(track: PlayableTrack) {}
}

class PlayerEngine(
    private val mpv: Mpv,
    private val listener: EngineListener = object : EngineListener {},
    private val skip: (PlayableTrack) -> Boolean = { false },
    audioOutput: String? = null,
) {
    private class Entry(val track: PlayableTrack, var id: Long? = null)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    @Volatile private var running = true

    private val queue = mutableListOf<Entry>()
    private var unshuffled: List<Entry>? = null
    private var index = -1
    private var loaded = false
    private var pendingSeekMs: Long? = null
    private var chosen = false
    private var repeat = RepeatMode.OFF
    private var userVolume = 1f
    private var lastPositionMs = 0L
    private var restoredPositionMs = 0L
    private val announced = mutableListOf<Transition>()
    private var startPending = false
    private var startRetries = 0

    private val thread: Thread

    init {
        mpv.option("config", "no")
        mpv.option("terminal", "no")
        mpv.option("load-scripts", "no")
        mpv.option("ytdl", "no")
        mpv.option("input-default-bindings", "no")
        mpv.option("vo", "null")
        mpv.option("video", "no")
        mpv.option("audio-display", "no")
        mpv.option("idle", "yes")
        mpv.option("gapless-audio", "weak")
        mpv.option("prefetch-playlist", "yes")
        mpv.option("audio-client-name", "Sillon")
        mpv.option("title", "Sillon")
        mpv.option("network-timeout", "30")
        mpv.option("volume-max", "100")
        mpv.option("media-controls", "yes")
        audioOutput?.let { mpv.option("ao", it) }
        mpv.initialize()
        listOf("pause", "core-idle", "idle-active", "paused-for-cache", "duration").forEach(mpv::observe)
        thread = Thread(::loop, "sillon-mpv").apply {
            isDaemon = true
            start()
        }
    }

    fun play(tracks: List<PlayableTrack>, startIndex: Int = 0, shuffle: Boolean = false, positionMs: Long = 0, paused: Boolean = false) = post {
        if (tracks.isEmpty()) return@post
        val previous = current()
        val previousPosition = lastPositionMs
        val entries = tracks.map { Entry(it) }
        val start = if (shuffle) entries.indices.random() else startIndex.coerceIn(0, entries.lastIndex)
        val order = if (shuffle) listOf(entries[start]) + entries.filterIndexed { i, _ -> i != start }.shuffled() else entries
        mpv.command("stop")
        queue.clear()
        order.forEach(::load)
        unshuffled = if (shuffle) entries else null
        index = if (shuffle) 0 else start
        lastPositionMs = positionMs
        restoredPositionMs = positionMs
        if (paused) {
            loaded = false
            pendingSeekMs = positionMs.takeIf { it > 0 }
            mpv.set("pause", "yes")
        } else {
            startAt(index, positionMs.takeIf { it > 0 })
        }
        announce(Transition(previous, previousPosition, current(), automatic = false))
        publish()
    }

    fun playPause() = post {
        if (queue.isEmpty()) return@post
        when {
            !loaded -> startAt(index.coerceAtLeast(0), pendingSeekMs)
            mpv.flag("pause") == true -> mpv.set("pause", "no")
            else -> mpv.set("pause", "yes")
        }
        publish()
    }

    fun next() = post { step(forward = true) }

    fun previous() = post {
        if (queue.isEmpty()) return@post
        when {
            !loaded -> if (index > 0) moveIdle(index - 1)
            lastPositionMs > RESTART_AFTER_MS || index <= 0 -> mpv.command("seek", "0", "absolute")
            else -> goTo(index - 1)
        }
        publish()
    }

    fun jumpTo(target: Int) = post {
        if (target !in queue.indices) return@post
        if (loaded && target == index) {
            mpv.command("seek", "0", "absolute")
            mpv.set("pause", "no")
        } else {
            startAt(target, null)
        }
        publish()
    }

    fun seekTo(positionMs: Long) = post {
        if (queue.isEmpty()) return@post
        if (loaded) {
            mpv.command("seek", (positionMs / 1000.0).toString(), "absolute")
        } else {
            pendingSeekMs = positionMs
            restoredPositionMs = positionMs
        }
        lastPositionMs = positionMs
        publish()
    }

    fun setVolume(volume: Float) = post {
        userVolume = volume.coerceIn(0f, 1f)
        applyVolume()
        publish()
    }

    fun setRepeat(mode: RepeatMode) = post {
        repeat = mode
        mpv.set("loop-playlist", if (mode == RepeatMode.ALL) "inf" else "no")
        mpv.set("loop-file", if (mode == RepeatMode.ONE) "inf" else "no")
        publish()
    }

    fun setShuffle(enabled: Boolean) = post {
        if (queue.isEmpty() || enabled == (unshuffled != null)) return@post
        val playing = queue.getOrNull(index) ?: return@post
        if (enabled) {
            val original = queue.toList()
            reorder(listOf(playing) + queue.filter { it !== playing }.shuffled(), 0)
            unshuffled = original
        } else {
            val present = queue.toSet()
            val known = unshuffled.orEmpty().filter { it in present }
            val restored = known + queue.filter { it !in known }
            reorder(restored, restored.indexOf(playing).coerceAtLeast(0))
            unshuffled = null
        }
        publish()
    }

    fun removeAt(target: Int) = post {
        if (target !in queue.indices) return@post
        val wasCurrent = target == index
        val removed = queue[target]
        val position = lastPositionMs
        if (wasCurrent) chosen = true
        mpv.command("playlist-remove", target.toString())
        queue.removeAt(target)
        unshuffled = unshuffled?.filter { it !== removed }
        when {
            queue.isEmpty() -> {
                index = -1
                loaded = false
                lastPositionMs = 0
                announce(Transition(removed.track, position, null, automatic = false))
            }
            target < index -> index--
            wasCurrent -> {
                val playing = mpv.long("playlist-pos")?.toInt()?.takeIf { loaded && it in queue.indices }
                if (playing == null) {
                    loaded = false
                    index = index.coerceAtMost(queue.lastIndex)
                    pendingSeekMs = null
                    restoredPositionMs = 0
                } else {
                    index = playing
                }
                lastPositionMs = 0
                applyVolume()
                announce(Transition(removed.track, position, current(), automatic = false))
            }
        }
        publish()
    }

    fun move(from: Int, to: Int) = post {
        if (from !in queue.indices || to !in queue.indices || from == to) return@post
        moveEntry(from, to)
        publish()
    }

    fun insertNext(tracks: List<PlayableTrack>) = post {
        if (tracks.isEmpty()) return@post
        if (queue.isEmpty()) {
            adopt(tracks)
            return@post
        }
        val added = tracks.map { Entry(it) }
        added.forEachIndexed { offset, entry ->
            load(entry)
            val target = index + 1 + offset
            if (target < queue.lastIndex) moveEntry(queue.lastIndex, target)
        }
        unshuffled = unshuffled?.plus(added)
        publish()
    }

    fun append(tracks: List<PlayableTrack>) = post {
        if (tracks.isEmpty()) return@post
        if (queue.isEmpty()) {
            adopt(tracks)
            return@post
        }
        val added = tracks.map { Entry(it) }
        added.forEach(::load)
        unshuffled = unshuffled?.plus(added)
        publish()
    }

    fun replaceUpcoming(tracks: List<PlayableTrack>) = post {
        val playing = current()
        val fresh = tracks.filter { it.ratingKey != playing?.ratingKey }
        if (fresh.isEmpty()) return@post
        if (queue.isEmpty()) {
            adopt(fresh)
            return@post
        }
        for (i in queue.lastIndex downTo index + 1) {
            mpv.command("playlist-remove", i.toString())
            queue.removeAt(i)
        }
        fresh.forEach { load(Entry(it)) }
        unshuffled = null
        publish()
    }

    fun replace(old: PlayableTrack, track: PlayableTrack, play: Boolean) = post {
        val target = queue.indexOfFirst { it.track === old }
        if (target < 0) return@post
        val entry = Entry(track)
        load(entry)
        if (target < queue.lastIndex - 1) moveEntry(queue.lastIndex, target + 1)
        val stale = queue[target]
        val wasCurrent = index == target
        mpv.command("playlist-remove", target.toString())
        queue.removeAt(target)
        unshuffled = unshuffled?.map { if (it === stale) entry else it }
        if (index > target) index--
        if (play) {
            if (!wasCurrent) index = target
            startAt(target, null)
        }
        publish()
    }

    fun clear() = post {
        val previous = current()
        mpv.command("stop")
        queue.clear()
        unshuffled = null
        index = -1
        loaded = false
        if (previous != null) announce(Transition(previous, lastPositionMs, null, automatic = false))
        lastPositionMs = 0
        publish()
    }

    fun release() {
        running = false
        mpv.wakeup()
        thread.join(2_000)
        mpv.destroy()
    }

    private fun current(): PlayableTrack? = queue.getOrNull(index)?.track

    private fun load(entry: Entry) {
        mpv.command("loadfile", entry.track.streamUrl, "append")
        entry.id = mpv.long("playlist/${queue.size}/id")
        queue.add(entry)
    }

    private fun moveEntry(from: Int, to: Int) {
        val playing = queue.getOrNull(index)
        mpv.command("playlist-move", from.toString(), (if (from < to) to + 1 else to).toString())
        queue.add(to, queue.removeAt(from))
        if (playing != null) index = queue.indexOf(playing)
    }

    private fun adopt(tracks: List<PlayableTrack>) {
        tracks.forEach { load(Entry(it)) }
        index = 0
        startAt(0, null)
        announce(Transition(null, 0, current(), automatic = false))
        publish()
    }

    private fun step(forward: Boolean) {
        if (queue.isEmpty()) return
        val candidates = if (forward) (index + 1..queue.lastIndex) else (index - 1 downTo 0)
        val target = candidates.firstOrNull { !skip(queue[it].track) }
            ?: if (forward && repeat == RepeatMode.ALL) queue.indices.firstOrNull { !skip(queue[it].track) } else null
        if (target == null || target == index) return
        if (loaded) goTo(target) else moveIdle(target)
        publish()
    }

    private fun moveIdle(target: Int) {
        val previous = current()
        index = target
        pendingSeekMs = null
        restoredPositionMs = 0
        lastPositionMs = 0
        announce(Transition(previous, 0, current(), automatic = false))
    }

    private fun goTo(target: Int) {
        chosen = true
        mpv.set("playlist-pos", target.toString())
        arrive(target)
    }

    private fun startAt(target: Int, seekMs: Long?) {
        pendingSeekMs = seekMs
        loaded = true
        chosen = true
        mpv.set("pause", "no")
        mpv.set("playlist-pos", target.toString())
        startPending = true
        startRetries = 0
        if (target == index) {
            chosen = false
            applyVolume()
        } else {
            arrive(target)
        }
    }

    private fun reorder(order: List<Entry>, currentAt: Int) {
        for (i in queue.lastIndex downTo 0) {
            if (i != index) mpv.command("playlist-remove", i.toString())
        }
        val playing = queue[index]
        queue.clear()
        queue.add(playing)
        index = 0
        for (i in currentAt + 1..order.lastIndex) load(order[i])
        for (i in 0 until currentAt) {
            load(order[i])
            moveEntry(queue.lastIndex, i)
        }
    }

    private fun post(action: () -> Unit) {
        commands.add(action)
        mpv.wakeup()
    }

    private fun loop() {
        while (running) {
            while (true) {
                val command = commands.poll() ?: break
                runCatching(command)
            }
            val signal = runCatching { mpv.await(POLL_SECONDS) }.getOrNull()
            if (!running) break
            runCatching { handle(signal) }
        }
    }

    private fun handle(signal: MpvSignal?) {
        when (signal) {
            null -> if (loaded) {
                retryStart()
                publish()
            }
            MpvSignal.Shutdown -> running = false
            is MpvSignal.FileStarted -> {
                startPending = false
                val started = queue.indexOfFirst { it.id != null && it.id == signal.entryId }
                if (loaded && started >= 0 && mpv.long("playlist-pos")?.toInt() == started) arrive(started)
                publish()
            }
            MpvSignal.FileLoaded -> {
                pendingSeekMs?.let { mpv.command("seek", (it / 1000.0).toString(), "absolute") }
                pendingSeekMs = null
                publish()
            }
            is MpvSignal.FileEnded -> {
                val entry = queue.firstOrNull { it.id != null && it.id == signal.entryId }
                if (entry != null && signal.reason == EndReason.FAILED) listener.onFailed(entry.track)
                val over = signal.reason == EndReason.FINISHED || signal.reason == EndReason.FAILED
                if (loaded && over && entry != null && entry === queue.lastOrNull() && repeat == RepeatMode.OFF) ended()
                publish()
            }
            is MpvSignal.PropertyChanged -> publish()
            MpvSignal.Wakeup -> Unit
        }
    }

    private fun retryStart() {
        if (!startPending || index !in queue.indices) return
        if (mpv.flag("idle-active") != true || startRetries >= MAX_START_RETRIES) {
            startPending = false
            return
        }
        startRetries++
        val entries = queue.toList()
        mpv.command("stop")
        queue.clear()
        entries.forEach(::load)
        mpv.set("pause", "no")
        mpv.set("playlist-pos", index.toString())
    }

    private fun arrive(position: Int) {
        if (position !in queue.indices) return
        if (position == index) {
            chosen = false
            return
        }
        val previous = current()
        val previousPosition = lastPositionMs
        val automatic = !chosen
        val forward = position > index
        chosen = false
        index = position
        lastPositionMs = 0
        if (automatic && forward && skip(queue[position].track)) {
            val target = (position + 1..queue.lastIndex).firstOrNull { !skip(queue[it].track) }
            if (target != null) {
                index = target
                mpv.set("playlist-pos", target.toString())
            } else {
                mpv.set("pause", "yes")
            }
        }
        applyVolume()
        announce(Transition(previous, previousPosition, current(), automatic))
    }

    private fun ended() {
        val previous = current()
        loaded = false
        pendingSeekMs = null
        restoredPositionMs = 0
        announce(Transition(previous, lastPositionMs, null, automatic = true))
        index = if (queue.isEmpty()) -1 else 0
        lastPositionMs = 0
    }

    private fun announce(transition: Transition) {
        announced.add(transition)
    }

    private fun applyVolume() {
        val track = current()
        val linear = TrackGain.volumeFor(track?.gainDb) * userVolume
        mpv.set("volume", (cbrt(linear.toDouble()) * 100).toString())
        if (track != null) mpv.set("force-media-title", listOfNotNull(track.title, track.artist).joinToString(" · "))
    }

    private fun publish() {
        val track = current()
        val paused = mpv.flag("pause") ?: true
        val idle = mpv.flag("idle-active") ?: true
        val active = loaded && !idle
        val position = if (active) mpv.double("time-pos")?.let { (it * 1000).roundToLong() } else null
        val duration = if (active) mpv.double("duration")?.let { (it * 1000).roundToLong() } else null
        if (position != null && mpv.long("playlist-pos")?.toInt() == index) lastPositionMs = position.coerceAtLeast(0)
        _state.value = EngineState(
            queue = queue.map { it.track },
            index = index,
            playing = loaded && !paused,
            buffering = loaded && !paused && (!active || mpv.flag("paused-for-cache") == true || position == null),
            positionMs = if (loaded) lastPositionMs else restoredPositionMs,
            durationMs = track?.durationMs?.takeIf { it > 0 } ?: duration ?: 0,
            shuffle = unshuffled != null,
            repeat = repeat,
            volume = userVolume,
        )
        if (announced.isNotEmpty()) {
            val transitions = announced.toList()
            announced.clear()
            transitions.forEach(listener::onTransition)
        }
    }

    companion object {
        private const val POLL_SECONDS = 0.25
        private const val RESTART_AFTER_MS = 3_000L
        private const val MAX_START_RETRIES = 4

        fun searchDirs(): List<File> = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let(::File),
            System.getProperty("sillon.mpv.dir")?.let(::File),
        )
    }
}

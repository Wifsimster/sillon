package ovh.battistella.sillon.desktop.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ovh.battistella.sillon.data.plex.TimelineState
import ovh.battistella.sillon.data.rating.Rater
import ovh.battistella.sillon.data.rating.ServerRatings
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SavedQueue
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.StreamUrls

class Playback(
    private val repository: PlexRepository,
    private val settings: SettingsStore,
    private val saved: PlaybackStateStore,
    private val rater: Rater,
    private val scope: CoroutineScope,
    engineFactory: (EngineListener, (PlayableTrack) -> Boolean) -> PlayerEngine,
) {
    private data class Report(val key: String, val state: TimelineState, val timeMs: Long, val durationMs: Long, val continuing: Boolean)

    private val reports = Channel<Report>(Channel.UNLIMITED)
    private val transitions = Channel<Transition>(Channel.UNLIMITED)
    private val fellBack = mutableSetOf<String>()
    @Volatile private var skipOneStar = true
    private var lastReported: Pair<String, TimelineState>? = null
    private var reporting: String? = null
    private var reported: String? = null

    private val _unavailable = MutableStateFlow<String?>(null)
    private val _radio = MutableStateFlow(false)
    val radio: StateFlow<Boolean> = _radio.asStateFlow()
    private var more: (suspend () -> List<PlayableTrack>)? = null
    private var refilling: Job? = null

    private val listener = object : EngineListener {
        override fun onTransition(transition: Transition) {
            transitions.trySend(transition)
        }

        override fun onFailed(track: PlayableTrack) {
            if (!StreamUrls.isTranscode(track.streamUrl) || !fellBack.add(track.ratingKey)) return
            scope.launch {
                val original = runCatching {
                    repository.metadata(track.ratingKey)?.let { repository.streamUrl(repository.endpoint(), it) }
                }.getOrNull() ?: return@launch
                engine?.replace(track, track.copy(streamUrl = original), play = true)
            }
        }
    }

    private val engine: PlayerEngine? = runCatching { engineFactory(listener, ::shouldSkip) }
        .onFailure { _unavailable.value = it.message ?: it.javaClass.simpleName }
        .getOrNull()

    val state: StateFlow<EngineState> = engine?.state ?: MutableStateFlow(EngineState())

    val unavailable: StateFlow<String?> = _unavailable.asStateFlow()

    val ratings: StateFlow<ServerRatings> = rater.state

    private var ticker: Job? = null

    init {
        scope.launch { settings.skipOneStar.collect { skipOneStar = it } }
        scope.launch(Dispatchers.IO) {
            for (report in reports) {
                runCatching {
                    repository.reportPlayback(repository.endpoint(), report.key, report.state, report.timeMs, report.durationMs, report.continuing)
                }
            }
        }
        scope.launch {
            for (transition in transitions) {
                val previous = transition.previous
                if (previous != null && previous.ratingKey == reported && previous.ratingKey != transition.current?.ratingKey) {
                    val duration = previous.durationMs ?: 0
                    val played = transition.previousPositionMs
                    val time = if (transition.automatic && duration > 0 && played >= duration - NEAR_END_MS) duration else played
                    report(previous, TimelineState.Stopped, time, continuing = transition.current != null)
                    reported = null
                }
                reporting = transition.current?.ratingKey
                lastReported = null
                reportCurrent()
                persist()
                if (transition.current == null) endRadio() else refillIfLow()
            }
        }
        scope.launch {
            state.map { Triple(it.current?.ratingKey, it.playing, it.buffering) }.distinctUntilChanged().collect { (_, playing, _) ->
                reportCurrent()
                if (playing) startTicker() else stopTicker()
                if (!playing) persist()
            }
        }
    }

    private fun shouldSkip(track: PlayableTrack): Boolean =
        skipOneStar && rater.state.value.book.isOneStar(track.ratingKey, track.userRating)

    fun play(
        tracks: List<PlayableTrack>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        pickStart: Boolean = false,
        more: (suspend () -> List<PlayableTrack>)? = null,
    ) {
        if (tracks.isEmpty()) return
        startRadio(more)
        val start = if (pickStart && !shuffle) {
            (startIndex..tracks.lastIndex).firstOrNull { !shouldSkip(tracks[it]) } ?: startIndex
        } else {
            startIndex
        }
        engine?.play(tracks, start, shuffle)
    }

    fun playPause() = engine?.playPause()

    fun next() = engine?.next()

    fun previous() = engine?.previous()

    fun seekTo(positionMs: Long) = engine?.seekTo(positionMs)

    fun jumpTo(index: Int) = engine?.jumpTo(index)

    fun removeAt(index: Int) = engine?.removeAt(index)

    fun move(from: Int, to: Int) = engine?.move(from, to)

    fun playNext(tracks: List<PlayableTrack>) = engine?.insertNext(tracks)

    fun addToQueue(tracks: List<PlayableTrack>) = engine?.append(tracks)

    fun replaceUpcoming(tracks: List<PlayableTrack>, more: (suspend () -> List<PlayableTrack>)? = null) {
        if (tracks.isEmpty()) return
        startRadio(more)
        engine?.replaceUpcoming(tracks)
    }

    private fun startRadio(source: (suspend () -> List<PlayableTrack>)?) {
        refilling?.cancel()
        more = source
        _radio.value = source != null
    }

    private fun endRadio() = startRadio(null)

    private fun refillIfLow() {
        val source = more ?: return
        val now = state.value
        if (now.queue.size - now.index - 1 > REFILL_BELOW || refilling?.isActive == true) return
        refilling = scope.launch {
            val fresh = RadioRefill.fresh(state.value.queue, runCatching { source() }.getOrDefault(emptyList()))
            if (more !== source) return@launch
            if (fresh.isEmpty()) endRadio() else engine?.append(fresh)
        }
    }

    fun setVolume(volume: Float) = engine?.setVolume(volume)

    fun toggleShuffle() = engine?.setShuffle(!state.value.shuffle)

    fun cycleRepeat() = engine?.setRepeat(
        when (state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        },
    )

    fun rate(key: String, stars: Int?) {
        rater.rate(key, stars)
        if (stars == 1 && state.value.current?.ratingKey == key) engine?.next()
    }

    fun stop() {
        endRadio()
        engine?.clear()
        scope.launch { runCatching { saved.clear() } }
    }

    suspend fun restore() {
        if (state.value.queue.isNotEmpty()) return
        val queue = runCatching { saved.load() }.getOrNull() ?: return
        val tracks = runCatching { repository.restoreQueue(queue.keys) }.getOrNull().orEmpty()
        if (tracks.isEmpty() || state.value.queue.isNotEmpty()) return
        val wanted = queue.keys.getOrNull(queue.index)
        val index = tracks.indexOfFirst { it.ratingKey == wanted }
        engine?.play(tracks, index.coerceAtLeast(0), positionMs = if (index >= 0) queue.positionMs else 0, paused = true)
    }

    fun release() {
        val now = state.value
        now.current?.let { track ->
            runBlocking {
                withTimeoutOrNull(RELEASE_TIMEOUT_MS) {
                    runCatching { saved.save(snapshot(now) ?: return@runCatching) }
                    if (reported == track.ratingKey) {
                        runCatching {
                            repository.reportPlayback(repository.endpoint(), track.ratingKey, TimelineState.Stopped, now.positionMs, now.durationMs, false)
                        }
                    }
                }
            }
        }
        reports.close()
        transitions.close()
        engine?.release()
    }

    private fun reportCurrent(force: Boolean = false) {
        val now = state.value
        val track = now.current ?: return
        if (track.ratingKey != reporting) return
        val timeline = when {
            now.playing && !now.buffering -> TimelineState.Playing
            now.buffering -> TimelineState.Buffering
            else -> TimelineState.Paused
        }
        val key = track.ratingKey to timeline
        if (!force && key == lastReported) return
        if (timeline == TimelineState.Paused && lastReported == null) return
        lastReported = key
        reported = track.ratingKey
        report(track, timeline, now.positionMs, continuing = false)
    }

    private fun report(track: PlayableTrack, timeline: TimelineState, timeMs: Long, continuing: Boolean) {
        if (track.ratingKey.isBlank()) return
        reports.trySend(Report(track.ratingKey, timeline, timeMs.coerceAtLeast(0), track.durationMs ?: 0, continuing))
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var sinceSave = 0L
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                if (!state.value.playing) break
                reportCurrent(force = true)
                sinceSave += REPORT_INTERVAL_MS
                if (sinceSave >= QUEUE_SAVE_INTERVAL_MS) {
                    sinceSave = 0
                    persist()
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private suspend fun persist() {
        val queue = snapshot(state.value) ?: return
        runCatching { saved.save(queue) }
    }

    private fun snapshot(now: EngineState): SavedQueue? {
        if (now.queue.isEmpty()) return null
        val first = (now.index - MAX_SAVED_TRACKS / 2).coerceAtLeast(0).coerceAtMost((now.queue.size - MAX_SAVED_TRACKS).coerceAtLeast(0))
        val window = now.queue.drop(first).take(MAX_SAVED_TRACKS)
        val keys = window.map { it.ratingKey }.filter { it.isNotBlank() }
        if (keys.isEmpty()) return null
        return SavedQueue(
            keys = keys,
            index = (now.index - first).coerceIn(0, keys.lastIndex),
            positionMs = now.positionMs.coerceAtLeast(0),
            title = now.current?.title.orEmpty(),
            artist = now.current?.artist.orEmpty(),
        )
    }

    private companion object {
        const val REPORT_INTERVAL_MS = 10_000L
        const val QUEUE_SAVE_INTERVAL_MS = 20_000L
        const val MAX_SAVED_TRACKS = 500
        const val RELEASE_TIMEOUT_MS = 2_000L
        const val NEAR_END_MS = 3_000L
        const val REFILL_BELOW = 5
    }
}

object RadioRefill {
    fun fresh(queue: List<PlayableTrack>, drawn: List<PlayableTrack>): List<PlayableTrack> {
        val known = queue.mapTo(HashSet()) { it.ratingKey }
        return drawn.filter { known.add(it.ratingKey) }
    }
}

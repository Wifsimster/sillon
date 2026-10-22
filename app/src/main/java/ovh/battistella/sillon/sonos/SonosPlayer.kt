package ovh.battistella.sillon.sonos

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why a Sonos session ended without the user asking. */
enum class SonosLoss {
    /** The speaker stopped answering. */
    UNREACHABLE,

    /** The speaker never started: most often, it can't reach the server. */
    COULD_NOT_PLAY,

    /** Someone played something else on the speaker (the Sonos app, another controller). */
    TAKEN_OVER,
}

/**
 * The player behind the media session while music plays on a Sonos group. Every command goes to
 * the group's coordinator over UPnP (AVTransport); the state comes back from it every second.
 * The notification, the lock screen, the player screen and the volume keys all drive the speaker.
 */
@OptIn(UnstableApi::class)
class SonosPlayer(
    val group: SonosGroup,
    private val client: SoapCaller,
    private val scope: CoroutineScope,
    /** The speaker's copy of each item (same order), or null for one it can't play. */
    private val resolve: suspend (List<MediaItem>) -> List<SonosTrack?>,
    /** Items that must not reach the speaker (a hidden mystery sleeve). */
    private val refuse: (List<MediaItem>) -> Boolean,
    /** Called instead of loading refused items: they go back to the phone. */
    private val onRefused: suspend (items: List<MediaItem>, index: Int, positionMs: Long) -> Unit,
    private val onLost: suspend (SonosLoss) -> Unit,
    /** Called when refused items are kept off the speaker's queue. */
    private val onRefusedAdd: () -> Unit = {},
    now: () -> Long = SystemClock::elapsedRealtime,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private class Entry(val uid: Long, val item: MediaItem, val track: SonosTrack)

    private val base = group.coordinator.baseUrl
    private val commands = Mutex()
    private var entries: List<Entry> = emptyList()
    private var nextUid = 0L
    private var status: SonosStatus? = null
    private var position: PositionSupplier = PositionSupplier.ZERO
    private var shuffle = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var volume = 0
    private var poller: Job? = null
    private val health = SonosHealth(now)
    private var ticks = 0
    private var lost = false

    /** Sends [items] to the speaker and starts following it. Throws when the speaker refuses. */
    suspend fun start(items: List<MediaItem>, index: Int, positionMs: Long, play: Boolean, shuffle: Boolean, repeatMode: Int) {
        this.shuffle = shuffle
        this.repeatMode = repeatMode
        commands.withLock {
            runCatching { volume = client.call(base, AvTransport.groupVolume())["CurrentVolume"]?.toIntOrNull() ?: 0 }
            load(items, index, positionMs, play)
            poll()
        }
        invalidateState()
        poller = scope.launch { follow() }
    }

    /** Where the speaker is, for the phone to carry on from there. */
    fun handBack(): Triple<List<MediaItem>, Int, Long> {
        val index = (status?.index ?: 0).coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        return Triple(entries.map { it.item }, index, position.get())
    }

    val wasPlaying: Boolean get() = status?.playWhenReady == true

    /** Pauses the speaker before the phone takes over; quiet when it is already stopped. */
    suspend fun pauseSpeaker() {
        poller?.cancel()
        health.pauseAsked()
        commands.withLock {
            runCatching { poll() }
            if (status?.playWhenReady == true) runCatching { client.call(base, AvTransport.pause()) }
        }
    }

    private suspend fun follow() {
        while (scope.isActive && !lost) {
            delay(health.nextPollMs(status?.playWhenReady == true))
            commands.withLock {
                runCatching { poll() }.onFailure {
                    Log.w(TAG, "poll failed: ${it.message}")
                    health.pollFailed()?.let(::lose)
                }
            }
            if (!lost) invalidateState()
        }
    }

    private suspend fun poll() {
        val transport = client.call(base, AvTransport.transportInfo())
        val positionInfo = client.call(base, AvTransport.positionInfo())
        val media = client.call(base, AvTransport.mediaInfo())
        if (ticks++ % SLOW_POLL_EVERY == 0) {
            runCatching {
                val mode = PlayMode.parse(client.call(base, AvTransport.transportSettings())["PlayMode"])
                shuffle = mode.first
                repeatMode = mode.second
            }
            runCatching { volume = client.call(base, AvTransport.groupVolume())["CurrentVolume"]?.toIntOrNull() ?: volume }
        }
        val now = SonosStatus.of(transport, positionInfo, media)
        status = now
        position = if (now.isPlaying) PositionSupplier.getExtrapolating(now.positionMs, 1f) else PositionSupplier.getConstant(now.positionMs)
        val ours = entries.isEmpty() || now.playsOurs(group.coordinator.uuid, entries.mapNotNullTo(HashSet()) { trackKey(it.track.uri) })
        health.polled(now, ours)?.let(::lose)
    }

    private fun lose(reason: SonosLoss) {
        if (lost) return
        lost = true
        Log.w(TAG, "Sonos session lost: $reason")
        scope.launch { onLost(reason) }
    }

    private suspend fun load(items: List<MediaItem>, index: Int, positionMs: Long, play: Boolean) {
        val window = SonosQueue.window(items.size, index)
        val sent = items.slice(window)
        val resolved = resolve(sent)
        val kept = sent.indices.filter { resolved.getOrNull(it) != null }
        if (kept.isEmpty()) throw SonosException(null, "nothing the speaker can play")
        val wanted = index - window.first
        val start = SonosQueue.realign(kept, wanted)
        entries = kept.map { Entry(nextUid++, sent[it], resolved[it]!!) }
        client.call(base, AvTransport.removeAllTracks())
        entries.chunked(AvTransport.BATCH).forEach { batch -> client.call(base, AvTransport.addTracks(batch.map { it.track }, null)) }
        client.call(base, AvTransport.playFromQueue(group.coordinator.uuid))
        client.call(base, AvTransport.setPlayMode(shuffle, repeatMode))
        client.call(base, AvTransport.seekToTrack(start))
        if (positionMs > 0 && kept[start] == wanted) client.call(base, AvTransport.seekTo(positionMs))
        if (play) startPlaying()
    }

    private suspend fun startPlaying() {
        client.call(base, AvTransport.play())
        health.playAsked()
    }

    /** Runs [block] after the commands already sent, then reads the speaker back. */
    private fun send(block: suspend () -> Unit): ListenableFuture<*> {
        // Once the session is over, late commands (a play() right after a refused queue) stay off the speaker.
        if (lost) return Futures.immediateVoidFuture()
        return queued(block)
    }

    private fun queued(block: suspend () -> Unit): ListenableFuture<*> = scope.future {
        commands.withLock {
            runCatching { block() }.onFailure { Log.w(TAG, "command failed: ${it.message}") }
            runCatching { poll() }
        }
        Unit
    }

    override fun getState(): State {
        val now = status
        val index = (now?.index ?: 0).coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        val playlist = entries.mapIndexed { i, entry ->
            MediaItemData.Builder(entry.uid)
                .setMediaItem(entry.item)
                .setIsSeekable(true)
                .setDurationUs(if (i == index) now?.durationMs?.let { it * 1000 } ?: C.TIME_UNSET else C.TIME_UNSET)
                .build()
        }
        return State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) C.INDEX_UNSET else index)
            .setContentPositionMs(position)
            .setPlayWhenReady(now?.playWhenReady == true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaybackState(if (playlist.isEmpty() || now == null) Player.STATE_IDLE else now.playbackState)
            .setShuffleModeEnabled(shuffle)
            .setRepeatMode(repeatMode)
            .setDeviceInfo(DEVICE_INFO)
            .setDeviceVolume(volume)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) = send {
        if (playWhenReady) startPlaying() else pauseSpeakerNow()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop() = send { pauseSpeakerNow() }

    private suspend fun pauseSpeakerNow() {
        // A pause before the speaker reported playing must not end the session as "couldn't play".
        health.pauseAsked()
        client.call(base, AvTransport.pause())
    }

    override fun handleRelease(): ListenableFuture<*> {
        poller?.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int) = send {
        val current = status?.index ?: 0
        when {
            seekCommand == Player.COMMAND_SEEK_TO_NEXT || seekCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                client.call(base, AvTransport.next())
            // The speaker knows its own shuffle order; Sillon's would point elsewhere.
            (seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS || seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
                mediaItemIndex != current -> client.call(base, AvTransport.previous())
            else -> {
                if (mediaItemIndex != current && mediaItemIndex != C.INDEX_UNSET) client.call(base, AvTransport.seekToTrack(mediaItemIndex))
                val target = if (positionMs == C.TIME_UNSET) 0L else positionMs
                if (target > 0 || mediaItemIndex == current) client.call(base, AvTransport.seekTo(target))
            }
        }
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        if (refuse(mediaItems)) {
            lost = true
            poller?.cancel()
            val items = mediaItems.toList()
            scope.launch { onRefused(items, startIndex.coerceAtLeast(0), startPositionMs.coerceAtLeast(0)) }
            return Futures.immediateVoidFuture()
        }
        val items = mediaItems.toList()
        return send {
            val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            load(items, index, if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs, play = status?.playWhenReady == true)
        }
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        if (refuse(mediaItems)) {
            onRefusedAdd()
            return Futures.immediateVoidFuture()
        }
        val items = mediaItems.toList()
        return send {
            val resolved = resolve(items)
            val added = items.indices.mapNotNull { i -> resolved.getOrNull(i)?.let { Entry(nextUid++, items[i], it) } }
            if (added.isEmpty()) return@send
            val at = index.coerceIn(0, entries.size)
            val append = at == entries.size
            added.chunked(AvTransport.BATCH).forEachIndexed { n, batch ->
                client.call(base, AvTransport.addTracks(batch.map { it.track }, if (append) null else at + n * AvTransport.BATCH))
            }
            entries = entries.take(at) + added + entries.drop(at)
        }
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int) = send {
        val to = toIndex.coerceAtMost(entries.size)
        if (fromIndex >= to) return@send
        client.call(base, AvTransport.removeTracks(fromIndex, to - fromIndex))
        entries = entries.take(fromIndex) + entries.drop(to)
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) = send {
        val to = toIndex.coerceAtMost(entries.size)
        if (fromIndex >= to) return@send
        client.call(base, AvTransport.moveTracks(fromIndex, to, newIndex))
        val moving = entries.subList(fromIndex, to).toList()
        val rest = entries.take(fromIndex) + entries.drop(to)
        val at = newIndex.coerceIn(0, rest.size)
        entries = rest.take(at) + moving + rest.drop(at)
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean) = send {
        shuffle = shuffleModeEnabled
        client.call(base, AvTransport.setPlayMode(shuffle, repeatMode))
    }

    override fun handleSetRepeatMode(repeatMode: Int) = send {
        this.repeatMode = repeatMode
        client.call(base, AvTransport.setPlayMode(shuffle, repeatMode))
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int) = send {
        volume = deviceVolume.coerceIn(0, MAX_VOLUME)
        client.call(base, AvTransport.setGroupVolume(volume))
    }

    override fun handleIncreaseDeviceVolume(flags: Int) = handleSetDeviceVolume(volume + VOLUME_STEP, flags)

    override fun handleDecreaseDeviceVolume(flags: Int) = handleSetDeviceVolume(volume - VOLUME_STEP, flags)

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int) = send {
        client.call(base, AvTransport.setGroupMute(muted))
    }

    private companion object {
        const val TAG = "SillonSonos"
        const val SLOW_POLL_EVERY = 5
        const val MAX_VOLUME = 100
        const val VOLUME_STEP = 2

        val DEVICE_INFO: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(MAX_VOLUME).build()

        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}

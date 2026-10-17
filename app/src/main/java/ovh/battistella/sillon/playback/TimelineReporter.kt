package ovh.battistella.sillon.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.TimelineState
import ovh.battistella.sillon.data.plex.streamEndpoint

/** One `/:/timeline` call to make. */
data class TimelineReport(
    val server: ServerEndpoint,
    val ratingKey: String,
    val state: TimelineState,
    val timeMs: Long,
    val durationMs: Long,
    val continuing: Boolean = false,
)

/**
 * Tells the Plex server what the player is doing, so the track shows under
 * "Now playing", lands in the history and gets its play count.
 *
 * Listens to the player inside [PlaybackService], so it reports whatever starts
 * playback: the app, the notification, a headset. Reports are sent one at a time
 * in order (a track's `stopped` must reach the server before the next one's
 * `playing`), and a failed report is dropped: the next one carries the full state.
 */
class TimelineReporter(
    private val player: Player,
    /** Main-thread scope of the service; drives the periodic report. */
    private val mainScope: CoroutineScope,
    private val send: suspend (TimelineReport) -> Unit,
) : Player.Listener {

    /** Outlives the service so the final `stopped` report still goes out after onDestroy. */
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<TimelineReport>(Channel.UNLIMITED)
    private var ticker: Job? = null

    /** Last state reported for the current track, to skip duplicate events. */
    private var lastState: TimelineState? = null

    /** Last known duration per track, for the report sent once the player has moved on. */
    private val durations = mutableMapOf<String, Long>()

    fun start() {
        sendScope.launch {
            for (report in queue) {
                runCatching { send(report) }.onFailure {
                    Log.w(TAG, "timeline ${report.state.wire} for ${report.ratingKey} failed: ${it.message}")
                }
            }
        }
        player.addListener(this)
        reportCurrent(force = true)
    }

    /** Report the current track as stopped and stop listening. */
    fun release() {
        stopTicker()
        player.removeListener(this)
        player.currentMediaItem?.let { item ->
            enqueue(item, TimelineState.Stopped, player.currentPosition, continuing = false)
        }
        // Closing lets the sender drain what's queued, then finish.
        queue.close()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startTicker() else stopTicker()
        reportCurrent()
    }

    override fun onPlaybackStateChanged(playbackState: Int) = reportCurrent()

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val oldItem = oldPosition.mediaItem ?: return
        val newItem = newPosition.mediaItem
        if (oldItem.mediaId == newItem?.mediaId) return
        // The track changed (played out, skipped, or the queue was replaced):
        // close the old one. A track that played out reports its full length so
        // the server counts it as played.
        val time = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            durations[oldItem.mediaId] ?: oldPosition.positionMs
        } else {
            oldPosition.positionMs
        }
        enqueue(oldItem, TimelineState.Stopped, time, continuing = newItem != null)
        lastState = null
        reportCurrent()
    }

    /** Report the player's current state, unless it was already reported for this track. */
    private fun reportCurrent(force: Boolean = false) {
        val item = player.currentMediaItem ?: return
        val state = stateOf(player.isPlaying, player.playbackState, player.playWhenReady)
        if (!force && state == lastState) return
        lastState = state
        val time = if (player.playbackState == Player.STATE_ENDED) {
            knownDuration(item) ?: player.currentPosition
        } else {
            player.currentPosition
        }
        enqueue(item, state, time, continuing = false)
    }

    private fun enqueue(item: MediaItem, state: TimelineState, timeMs: Long, continuing: Boolean) {
        val server = endpointOf(item) ?: return
        val report = TimelineReport(
            server = server,
            ratingKey = item.mediaId,
            state = state,
            timeMs = timeMs.coerceAtLeast(0),
            durationMs = knownDuration(item) ?: 0L,
            continuing = continuing,
        )
        queue.trySend(report)
    }

    /** The player's duration for the current track, remembered for later reports. */
    private fun knownDuration(item: MediaItem): Long? {
        if (player.currentMediaItem?.mediaId == item.mediaId) {
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durations[item.mediaId] = it }
        }
        return durations[item.mediaId]
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = mainScope.launch {
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                if (!player.isPlaying) break
                reportCurrent(force = true)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        private const val TAG = "SillonTimeline"

        /** Plex asks for a report every 10 s on a LAN/WAN while playing. */
        const val REPORT_INTERVAL_MS = 10_000L

        /** What to tell Plex for a given player state. */
        fun stateOf(isPlaying: Boolean, playbackState: Int, playWhenReady: Boolean): TimelineState = when {
            isPlaying -> TimelineState.Playing
            playbackState == Player.STATE_ENDED -> TimelineState.Stopped
            playbackState == Player.STATE_BUFFERING && playWhenReady -> TimelineState.Buffering
            else -> TimelineState.Paused
        }

        /** The server a queued item streams from (the URI is in either place, see [MediaItems]). */
        fun endpointOf(item: MediaItem): ServerEndpoint? {
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return null
            return streamEndpoint(uri.toString())
        }
    }
}

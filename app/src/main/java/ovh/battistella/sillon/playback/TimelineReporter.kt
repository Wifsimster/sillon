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

data class TimelineReport(
    val server: ServerEndpoint?,
    val ratingKey: String,
    val state: TimelineState,
    val timeMs: Long,
    val durationMs: Long,
    val continuing: Boolean = false,
)

class TimelineReporter(
    private val player: Player,
    private val mainScope: CoroutineScope,
    private val send: suspend (TimelineReport) -> Unit,
) : Player.Listener {
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<TimelineReport>(Channel.UNLIMITED)
    private var ticker: Job? = null

    private var lastState: TimelineState? = null

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

    fun release() {
        stopTicker()
        player.removeListener(this)
        player.currentMediaItem?.let { item ->
            enqueue(item, TimelineState.Stopped, player.currentPosition, continuing = false)
        }
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
        val time = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            durations[oldItem.mediaId] ?: oldPosition.positionMs
        } else {
            oldPosition.positionMs
        }
        enqueue(oldItem, TimelineState.Stopped, time, continuing = newItem != null)
        lastState = null
        reportCurrent()
    }

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
        if (item.mediaId.isBlank()) return
        val report = TimelineReport(
            server = endpointOf(item),
            ratingKey = item.mediaId,
            state = state,
            timeMs = timeMs.coerceAtLeast(0),
            durationMs = knownDuration(item) ?: 0L,
            continuing = continuing,
        )
        queue.trySend(report)
    }

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

        const val REPORT_INTERVAL_MS = 10_000L

        fun stateOf(isPlaying: Boolean, playbackState: Int, playWhenReady: Boolean): TimelineState = when {
            isPlaying -> TimelineState.Playing
            playbackState == Player.STATE_ENDED -> TimelineState.Stopped
            playbackState == Player.STATE_BUFFERING && playWhenReady -> TimelineState.Buffering
            else -> TimelineState.Paused
        }

        fun endpointOf(item: MediaItem): ServerEndpoint? {
            val uri = item.localConfiguration?.uri ?: return null
            return streamEndpoint(uri.toString())
        }
    }
}

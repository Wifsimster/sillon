package ovh.battistella.sillon.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NeverPlayedRefill(
    private val player: Player,
    private val scope: CoroutineScope,
    private val radio: NeverPlayedRadio,
) : Player.Listener {
    private var loading: Job? = null

    @OptIn(UnstableApi::class)
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (!radio.isActive) return
        val oldKey = oldPosition.mediaItem?.mediaId ?: return
        if (oldKey == newPosition.mediaItem?.mediaId) return
        radio.onLeft(
            key = oldKey,
            listenedMs = oldPosition.positionMs,
            finished = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            skipped = reason == Player.DISCONTINUITY_REASON_SEEK,
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!radio.isActive) return
        val key = mediaItem?.mediaId
        if (key == null || !radio.owns(key)) {
            radio.stop()
            loading?.cancel()
            return
        }
        refillIfLow()
    }

    private fun refillIfLow() {
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > REFILL_BELOW || loading?.isActive == true) return
        loading = scope.launch {
            val more = runCatching { radio.more() }.getOrDefault(emptyList())
            if (more.isNotEmpty() && radio.isActive) player.addMediaItems(more.map(MediaItems::from))
        }
    }

    private companion object {
        const val REFILL_BELOW = 5
    }
}

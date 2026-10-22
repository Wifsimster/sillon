package ovh.battistella.sillon.playback.mystery

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class MysteryWatcher(
    private val player: Player,
    private val session: MysterySession,
    private val scope: CoroutineScope,
    private val shared: Flow<Boolean> = emptyFlow(),
) : Player.Listener {
    private var job: Job? = null
    private var sharedJob: Job? = null

    fun start() {
        player.addListener(this)
        job = scope.launch {
            session.state.collect { if (it?.revealed == true) unmaskAll(it) }
        }
        // A listening-together group shares the queue by track id, so the album can't stay hidden from it.
        sharedJob = scope.launch {
            shared.collect { if (it) session.reveal() }
        }
    }

    fun release() {
        job?.cancel()
        sharedJob?.cancel()
        player.removeListener(this)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION && reason != Player.DISCONTINUITY_REASON_SEEK) return
        val index = oldPosition.mediaItemIndex
        if (index == newPosition.mediaItemIndex || index !in 0 until player.mediaItemCount) return
        session.trackLeft(player.getMediaItemAt(index).mediaId)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && session.state.value?.contains(player.currentMediaItem?.mediaId.orEmpty()) == true) {
            session.albumEnded()
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        val mystery = session.state.value ?: return
        val keys = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        when {
            keys.none(mystery::contains) -> session.clear()
            mystery.revealed -> unmaskAll(mystery)
        }
    }

    private fun unmaskAll(mystery: Mystery) {
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            if (!MysteryMask.isMasked(item)) continue
            val real = mystery.real(item.mediaId) ?: continue
            player.replaceMediaItem(i, MysteryMask.unmask(item, real))
        }
    }
}

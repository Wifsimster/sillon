package ovh.battistella.sillon.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** The rules of "Skip 1-star tracks": a track rated 1 star doesn't play unless you choose it. */
object OneStarSkip {
    /**
     * Whether arriving on a track should move past it if it's rated 1 star: when the queue moved
     * on by itself, or you went to the next track. Never while listening together (the group owns
     * the queue), nor while the player holds at the end of a side.
     */
    fun shouldSkip(reason: Int, forward: Boolean, enabled: Boolean, grouped: Boolean, holding: Boolean): Boolean {
        if (!enabled || grouped || holding) return false
        return when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> true
            Player.DISCONTINUITY_REASON_SEEK -> forward
            else -> false
        }
    }

    /** The first track from [index] on that isn't rated 1 star, or null when none is left. */
    fun target(index: Int?, isOneStar: (Int) -> Boolean, next: (Int) -> Int?, count: Int): Int? {
        var i = index
        var steps = 0
        while (i != null && steps <= count) {
            if (!isOneStar(i)) return i
            i = next(i)
            steps++
        }
        return null
    }

    /** The first index of [count] tracks that may start a queue, from [from] on, or [from] when all are 1 star. */
    fun start(from: Int, count: Int, isOneStar: (Int) -> Boolean): Int =
        target(from, isOneStar, { (it + 1).takeIf { n -> n < count } }, count) ?: from
}

/** Moves past the 1-star tracks of the queue, following [OneStarSkip]. */
class OneStarSkipper(
    private val player: Player,
    private val isOneStar: (MediaItem) -> Boolean,
    private val enabled: () -> Boolean,
    private val grouped: () -> Boolean,
    private val holding: () -> Boolean,
) : Player.Listener {
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val from = oldPosition.mediaItemIndex
        val to = newPosition.mediaItemIndex
        if (from == to || to == C.INDEX_UNSET) return
        val forward = if (player.shuffleModeEnabled) to == next(from) else to > from
        if (!OneStarSkip.shouldSkip(reason, forward, enabled(), grouped(), holding())) return
        moveFrom(to)
    }

    /** The current track was just rated 1 star: on to the next one you'd want to hear. */
    fun skipCurrent() {
        if (grouped() || player.mediaItemCount == 0) return
        val next = next(player.currentMediaItemIndex)
        if (next == null) {
            player.pause()
            return
        }
        moveFrom(next, always = true)
    }

    private fun moveFrom(index: Int, always: Boolean = false) {
        val target = OneStarSkip.target(index, { isOneStar(player.getMediaItemAt(it)) }, ::next, player.mediaItemCount)
        when {
            target == null -> player.pause()
            target != index || always -> player.seekTo(target, 0L)
        }
    }

    private fun next(index: Int): Int? {
        val timeline = player.currentTimeline
        if (index !in 0 until timeline.windowCount) return null
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        return timeline.getNextWindowIndex(index, repeat, player.shuffleModeEnabled).takeIf { it != C.INDEX_UNSET }
    }
}

package ovh.battistella.sillon.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneStarSkipTest {
    private val oneStar = setOf(1, 2, 5)
    private fun next(count: Int) = { i: Int -> (i + 1).takeIf { it < count } }

    @Test
    fun `skips when the queue moves on by itself or you go to the next track`() {
        assertTrue(skip(Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
        assertTrue(skip(Player.DISCONTINUITY_REASON_SEEK, forward = true))
    }

    @Test
    fun `going back, or choosing a track, plays it`() {
        assertFalse(skip(Player.DISCONTINUITY_REASON_SEEK, forward = false))
        assertFalse(skip(Player.DISCONTINUITY_REASON_REMOVE))
        assertFalse(skip(Player.DISCONTINUITY_REASON_INTERNAL))
    }

    @Test
    fun `no skipping while listening together`() {
        assertFalse(skip(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, grouped = true))
        assertFalse(skip(Player.DISCONTINUITY_REASON_SEEK, forward = true, grouped = true))
    }

    @Test
    fun `no skipping when turned off, or while a side waits to be flipped`() {
        assertFalse(skip(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, enabled = false))
        assertFalse(skip(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, holding = true))
    }

    @Test
    fun `goes to the next track that isn't 1 star`() {
        assertEquals(3, OneStarSkip.target(1, oneStar::contains, next(7), 7))
        assertEquals(0, OneStarSkip.target(0, oneStar::contains, next(7), 7))
        assertEquals(6, OneStarSkip.target(5, oneStar::contains, next(7), 7))
    }

    @Test
    fun `nothing left after the 1-star tracks`() {
        assertNull(OneStarSkip.target(5, oneStar::contains, next(6), 6))
        assertNull(OneStarSkip.target(null, oneStar::contains, next(6), 6))
    }

    @Test
    fun `a repeating queue of 1-star tracks doesn't loop forever`() {
        val all = { _: Int -> true }
        assertNull(OneStarSkip.target(0, all, { (it + 1) % 3 }, 3))
    }

    @Test
    fun `a queue started for you opens on the first track worth hearing`() {
        assertEquals(3, OneStarSkip.start(1, 7, oneStar::contains))
        assertEquals(0, OneStarSkip.start(0, 7, oneStar::contains))
        assertEquals(1, OneStarSkip.start(1, 3, oneStar::contains))
    }

    private fun skip(
        reason: Int,
        forward: Boolean = true,
        enabled: Boolean = true,
        grouped: Boolean = false,
        holding: Boolean = false,
    ) = OneStarSkip.shouldSkip(reason, forward, enabled, grouped, holding)
}

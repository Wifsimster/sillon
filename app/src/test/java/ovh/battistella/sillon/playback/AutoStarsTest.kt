package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStarsTest {
    @Test
    fun `an unrated track starts at 3 stars`() {
        assertEquals(3, AutoStars.next(null))
    }

    @Test
    fun `the taps go 3, 4, 2 then clear`() {
        assertEquals(4, AutoStars.next(3))
        assertEquals(2, AutoStars.next(4))
        assertNull(AutoStars.next(2))
    }

    @Test
    fun `a full cycle comes back to unrated and never gives 1 star, which skips the track`() {
        var current: Int? = null
        val seen = mutableListOf<Int?>()
        repeat(4) {
            current = AutoStars.next(current)
            seen += current
        }
        assertEquals(listOf(3, 4, 2, null), seen)
        assertFalse(seen.contains(1))
    }

    @Test
    fun `from the heart or the thumb down the star leaves the extreme for 3 stars`() {
        assertEquals(3, AutoStars.next(1))
        assertEquals(3, AutoStars.next(5))
    }

    @Test
    fun `only 2 to 4 stars show on the star button`() {
        assertNull(AutoStars.shown(null))
        assertNull(AutoStars.shown(1))
        assertEquals(2, AutoStars.shown(2))
        assertEquals(4, AutoStars.shown(4))
        assertNull(AutoStars.shown(5))
    }

    @Test
    fun `the star button is for Plex, Jellyfin keeps the heart and the thumb down`() {
        assertTrue(AutoStars.offered(jellyfin = false))
        assertFalse(AutoStars.offered(jellyfin = true))
    }
}

package ovh.battistella.sillon.playback.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.playback.cache.CacheLimit.GB

class CacheLimitTest {
    @Test
    fun `two gigabytes by default, and only the offered sizes`() {
        assertEquals(listOf(1, 2, 5, 10), CacheLimit.CHOICES_GB)
        assertEquals(2, CacheLimit.sanitize(null))
        assertEquals(2, CacheLimit.sanitize(3))
        assertEquals(5, CacheLimit.sanitize(5))
    }

    @Test
    fun `plenty of space keeps the chosen size`() {
        assertEquals(2 * GB, CacheLimit.effective(2, usedBytes = 0, freeBytes = 50 * GB))
        assertTrue(CacheLimit.fits(10, usedBytes = 0, freeBytes = 50 * GB))
    }

    @Test
    fun `little free space holds the cache back and leaves a reserve`() {
        // 3 GB free, 500 MB already cached: the cache may grow to 2.5 GB, the phone keeps 1 GB.
        assertEquals(2_500_000_000L, CacheLimit.effective(5, usedBytes = 500_000_000L, freeBytes = 3 * GB))
        assertFalse(CacheLimit.fits(5, usedBytes = 500_000_000L, freeBytes = 3 * GB))
        assertTrue(CacheLimit.fits(2, usedBytes = 500_000_000L, freeBytes = 3 * GB))
    }

    @Test
    fun `a full phone stops the cache`() {
        assertEquals(0L, CacheLimit.effective(2, usedBytes = 0, freeBytes = 200_000_000L))
    }
}

package ovh.battistella.sillon.data.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.repository.PlexRepository

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FiveStarsCacheTest {
    private val cache = FiveStarsCache(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() = runBlocking { cache.clear() }

    @Test
    fun `saves the keys of a server and reads them back`() = runBlocking {
        cache.save("s1", listOf("a", "b", "a"), now = 1_000)

        assertEquals(CachedFiveStars(setOf("a", "b"), 1_000), cache.observe("s1").first())
        assertNull(cache.observe("s2").first())
    }

    @Test
    fun `keeps one server at a time`() = runBlocking {
        cache.save("s1", listOf("a"))
        cache.save("s2", listOf("b"))

        assertNull(cache.observe("s1").first())
        assertEquals(setOf("b"), cache.observe("s2").first()?.keys)
    }

    @Test
    fun `keeps at most MAX_KEYS keys`() = runBlocking {
        cache.save("s1", (0 until FiveStarsCache.MAX_KEYS + 50).map { "k$it" })

        val keys = cache.observe("s1").first()!!.keys
        assertEquals(FiveStarsCache.MAX_KEYS, keys.size)
        assertTrue("k0" in keys)
    }

    @Test
    fun `clear forgets everything`() = runBlocking {
        cache.save("s1", listOf("a"))
        cache.clear()

        assertNull(cache.observe("s1").first())
    }

    @Test
    fun `a list is stale when missing or 6 hours old`() {
        val ttl = FiveStarsCache.TTL_MS
        assertTrue(FiveStarsCache.isStale(null, now = 0))
        assertFalse(FiveStarsCache.isStale(CachedFiveStars(emptySet(), fetchedAt = 1_000), now = 1_000 + ttl - 1))
        assertTrue(FiveStarsCache.isStale(CachedFiveStars(emptySet(), fetchedAt = 1_000), now = 1_000 + ttl))
    }

    @Test
    fun `signing out clears the list`() = runBlocking {
        val fiveStars = mockk<FiveStarsCache>(relaxed = true)
        val repository = PlexRepository(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), fiveStars, mockk(relaxed = true), mockk(relaxed = true),
        )

        repository.signOut()

        coVerify { fiveStars.clear() }
    }
}

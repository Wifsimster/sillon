package ovh.battistella.sillon.playback.cache

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ListeningCacheEvictorTest {
    @get:Rule val folder = TemporaryFolder()

    private val evictor = ListeningCacheEvictor(maxBytes = 300)
    private lateinit var cache: SimpleCache

    @Before
    fun setUp() {
        cache = SimpleCache(folder.newFolder("cache"), evictor, StandaloneDatabaseProvider(RuntimeEnvironment.getApplication()))
    }

    @After
    fun tearDown() = cache.release()

    private fun put(key: String, bytes: Long = 100, whole: Long = bytes) {
        val hole = checkNotNull(cache.startReadWriteNonBlocking(key, 0, bytes))
        val file = cache.startFile(key, 0, bytes)
        file.writeBytes(ByteArray(bytes.toInt()))
        cache.commitFile(file, bytes)
        cache.releaseHoleSpan(hole)
        cache.applyContentMetadataMutations(key, ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, whole) })
        tick()
    }

    private fun play(key: String) {
        checkNotNull(cache.startReadWriteNonBlocking(key, 0, 100))
        tick()
    }

    // Spans are ordered by their last touch, in milliseconds.
    private fun tick() = Thread.sleep(5)

    @Test
    fun `the least recently played track goes first when the cache is full`() {
        put("a")
        put("b")
        put("c")
        play("a")
        put("d")
        assertEquals(setOf("a", "c", "d"), cache.keys.filter { cache.getCachedBytes(it, 0, 100) > 0 }.toSet())
        assertTrue(cache.cacheSpace <= 300)
    }

    @Test
    fun `a smaller size evicts at once`() {
        put("a")
        put("b")
        put("c")
        play("a")
        evictor.resize(cache, 150)
        assertEquals(100L, cache.cacheSpace)
        assertTrue(cache.isCached("a", 0, 100))
    }

    @Test
    fun `a track counts as cached only when whole`() {
        put("whole")
        put("half", bytes = 50, whole = 100)
        assertTrue(ListeningCache.isWhole(cache, "whole"))
        assertFalse(ListeningCache.isWhole(cache, "half"))
        assertFalse(ListeningCache.isWhole(cache, "missing"))
    }

    @Test
    fun `clearing empties the cache`() {
        put("a")
        put("b")
        ListeningCache.clearAll(cache)
        assertEquals(0L, cache.cacheSpace)
        assertFalse(ListeningCache.isWhole(cache, "a"))
    }
}

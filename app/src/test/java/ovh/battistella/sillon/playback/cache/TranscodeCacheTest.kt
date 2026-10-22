package ovh.battistella.sillon.playback.cache

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TranscodeCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var cache: SimpleCache
    private lateinit var transcodes: TranscodeCache

    private val aac320 = ListeningCacheKeys.transcode("m1", "1", 320)
    private val original = ListeningCacheKeys.track("m1", "1")

    @Before
    fun setUp() {
        cache = SimpleCache(folder.newFolder("cache"), NoOpCacheEvictor(), StandaloneDatabaseProvider(RuntimeEnvironment.getApplication()))
        transcodes = TranscodeCache { cache }
    }

    @After
    fun tearDown() = cache.release()

    private fun url(session: String, file: String) = when (file) {
        "start.m3u8" -> "https://server:32400/music/:/transcode/universal/start.m3u8?session=$session&X-Plex-Token=secret"
        else -> "https://server:32400/music/:/transcode/universal/session/$session/base/$file?X-Plex-Token=secret"
    }

    private fun master(session: String) = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=320000,CODECS=\"mp4a.40.2\"\nsession/$session/base/index.m3u8\n"

    private val media = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-PLAYLIST-TYPE:VOD\n" +
        "#EXTINF:10.0,\n00000.ts\n#EXTINF:10.0,\n00001.ts\n#EXT-X-ENDLIST\n"

    private fun write(key: String, bytes: ByteArray) {
        val hole = checkNotNull(cache.startReadWriteNonBlocking(key, 0, bytes.size.toLong()))
        val file = cache.startFile(key, 0, bytes.size.toLong())
        file.writeBytes(bytes)
        cache.commitFile(file, bytes.size.toLong())
        cache.releaseHoleSpan(hole)
        cache.applyContentMetadataMutations(key, ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, bytes.size.toLong()) })
    }

    /** What the player does: ask for each file of the play, and write it where the cache says. */
    private fun play(session: String, files: List<String> = listOf("start.m3u8", "index.m3u8", "00000.ts", "00001.ts")) {
        files.forEach { file ->
            val key = transcodes.keyFor(url(session, file)) ?: return@forEach
            val bytes = when (file) {
                "start.m3u8" -> master(session)
                "index.m3u8" -> media
                else -> "audio-$session-$file"
            }.toByteArray()
            if (!ListeningCache.isWhole(cache, key)) write(key, bytes)
        }
    }

    private fun bytesOf(key: String): String = String(checkNotNull(read(key)))

    private fun read(key: String): ByteArray? {
        val span = cache.getCachedSpans(key).firstOrNull() ?: return null
        return span.file?.readBytes()
    }

    @Test
    fun `a played transcode is kept whole under keys without token or session`() {
        transcodes.register("s1", aac320)
        play("s1")
        assertTrue(transcodes.isWhole(aac320))
        assertEquals(CachedVersions(original = false, transcodeKbps = 320), transcodes.versions("m1", "1", listOf(320, 192, 128)))
        cache.keys.forEach { key ->
            assertTrue(key.startsWith("$aac320/"))
            listOf("secret", "Token", "server", "s1").forEach { assertFalse("$key holds $it", key.contains(it)) }
        }
    }

    @Test
    fun `half a transcode is not a copy to play`() {
        transcodes.register("s1", aac320)
        play("s1", listOf("start.m3u8", "index.m3u8", "00000.ts"))
        assertFalse(transcodes.isWhole(aac320))
        assertEquals(CachedVersions.NONE, transcodes.versions("m1", "1", listOf(320)))
    }

    @Test
    fun `a later play reads the whole copy, links from the first play included`() {
        transcodes.register("s1", aac320)
        play("s1")
        transcodes.reuse("s2", aac320)
        // The new play's entry, and the cached playlist's links that still name the first play.
        assertEquals("$aac320/start.m3u8", transcodes.keyFor(url("s2", "start.m3u8")))
        assertEquals("$aac320/session/base/00001.ts", transcodes.keyFor(url("s1", "00001.ts")))
        assertTrue(transcodes.isWhole(aac320))
    }

    @Test
    fun `two plays never mix their bytes`() {
        transcodes.register("s1", aac320)
        play("s1", listOf("start.m3u8", "index.m3u8", "00000.ts"))
        // A new play of the same track starts: the unfinished copy of the first one goes.
        transcodes.register("s2", aac320)
        play("s2", listOf("start.m3u8"))
        assertNull(read("$aac320/session/base/00000.ts"))
        // The first play, still going, now streams without touching the second play's copy.
        assertNull(transcodes.keyFor(url("s1", "00001.ts")))
        play("s2", listOf("index.m3u8", "00000.ts", "00001.ts"))
        assertTrue(transcodes.isWhole(aac320))
        assertEquals("audio-s2-00000.ts", bytesOf("$aac320/session/base/00000.ts"))
        assertEquals("audio-s2-00001.ts", bytesOf("$aac320/session/base/00001.ts"))
    }

    @Test
    fun `a new play leaves a whole copy alone`() {
        transcodes.register("s1", aac320)
        play("s1")
        transcodes.register("s2", aac320)
        assertNull(transcodes.keyFor(url("s2", "start.m3u8")))
        assertTrue(transcodes.isWhole(aac320))
    }

    @Test
    fun `the original once whole replaces the transcodes of its track`() {
        transcodes.register("s1", aac320)
        play("s1")
        val other = ListeningCacheKeys.transcode("m1", "2", 320)
        transcodes.register("s9", other)
        play("s9")
        write(original, ByteArray(100))
        transcodes.onClosed(original)
        assertTrue(cache.keys.none { it.startsWith("$aac320/") })
        assertTrue(transcodes.isWhole(other))
        assertEquals(CachedVersions(original = true, transcodeKbps = null), transcodes.versions("m1", "1", listOf(320)))
    }

    @Test
    fun `half an original keeps the transcode`() {
        transcodes.register("s1", aac320)
        play("s1")
        val hole = checkNotNull(cache.startReadWriteNonBlocking(original, 0, 50))
        val file = cache.startFile(original, 0, 50)
        file.writeBytes(ByteArray(50))
        cache.commitFile(file, 50)
        cache.releaseHoleSpan(hole)
        cache.applyContentMetadataMutations(original, ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, 100) })
        transcodes.onClosed(original)
        assertNotNull(read("$aac320/start.m3u8"))
        assertEquals(320, transcodes.versions("m1", "1", listOf(320)).transcodeKbps)
    }
}

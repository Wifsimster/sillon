package ovh.battistella.sillon.playback.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningCacheKeysTest {
    @Test
    fun `the key names the server, the track and the quality`() {
        val key = ListeningCacheKeys.track("machine-1", "4242")
        assertEquals("sillon-track/v1/machine-1/4242/original", key)
        assertTrue(key.endsWith("/" + ListeningCacheKeys.ORIGINAL))
    }

    @Test
    fun `the key holds no token and no server address`() {
        val url = "https://192-168-1-2.abc.plex.direct:32400/library/parts/1/file.flac?X-Plex-Token=secret"
        val key = ListeningCacheKeys.track("machine-1", "4242")
        assertFalse(key.contains("secret"))
        assertFalse(key.contains("Token"))
        assertFalse(key.contains("http"))
        assertFalse(key.contains("plex.direct"))
        assertNotEquals(url, key)
    }

    @Test
    fun `quality, server and track each make a different key`() {
        val original = ListeningCacheKeys.track("m1", "1")
        assertNotEquals(original, ListeningCacheKeys.track("m1", "1", "aac-128"))
        assertNotEquals(original, ListeningCacheKeys.track("m2", "1"))
        assertNotEquals(original, ListeningCacheKeys.track("m1", "2"))
    }

    @Test
    fun `separators in ids cannot make two tracks collide`() {
        assertNotEquals(ListeningCacheKeys.track("a/b", "c"), ListeningCacheKeys.track("a", "b/c"))
    }

    @Test
    fun `only track keys go to the cache`() {
        assertTrue(ListeningCacheKeys.isTrack(ListeningCacheKeys.track("m1", "1")))
        assertFalse(ListeningCacheKeys.isTrack(null))
        assertFalse(ListeningCacheKeys.isTrack("https://server/library/parts/1/file.flac"))
    }

    @Test
    fun `a transcode has its own key per bitrate, apart from the original`() {
        val aac = ListeningCacheKeys.transcode("m1", "1", 320)
        assertEquals("sillon-track/v1/m1/1/aac-320", aac)
        assertNotEquals(aac, ListeningCacheKeys.transcode("m1", "1", 128))
        assertNotEquals(ListeningCacheKeys.track("m1", "1"), aac)
        assertEquals("sillon-track/v1/m1/1/aac-320/session/base/00001.ts", ListeningCacheKeys.part(aac, "session/base/00001.ts"))
        assertTrue(ListeningCacheKeys.isTrack(aac))
    }

    @Test
    fun `an original key knows the transcodes of its track`() {
        val original = ListeningCacheKeys.track("m1", "1")
        assertTrue(ListeningCacheKeys.isOriginal(original))
        assertFalse(ListeningCacheKeys.isOriginal(ListeningCacheKeys.transcode("m1", "1", 320)))
        assertTrue(ListeningCacheKeys.transcode("m1", "1", 128).startsWith(ListeningCacheKeys.transcodesOf(original)))
        assertFalse(ListeningCacheKeys.transcode("m1", "10", 128).startsWith(ListeningCacheKeys.transcodesOf(original)))
    }
}

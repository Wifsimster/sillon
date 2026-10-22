package ovh.battistella.sillon.playback.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourceTest {
    private fun choose(
        downloaded: Boolean = false,
        original: Boolean = false,
        cachedKbps: Int? = null,
        wantedKbps: Int? = null,
        online: Boolean = true,
    ) = StreamSource.choose(downloaded, original, cachedKbps, wantedKbps, online)

    @Test
    fun `a download wins over the cache and the network`() {
        assertEquals(StreamSource.DOWNLOAD, choose(downloaded = true, original = true))
        assertEquals(StreamSource.DOWNLOAD, choose(downloaded = true, cachedKbps = 320, wantedKbps = 128))
        assertFalse(StreamSource.DOWNLOAD.original)
        assertFalse(StreamSource.DOWNLOAD.transcode)
    }

    @Test
    fun `a cached original wins over everything else, even on mobile data`() {
        assertEquals(StreamSource.CACHE, choose(original = true, cachedKbps = 320, wantedKbps = 128))
        assertEquals(StreamSource.CACHE, choose(original = true))
        assertTrue(StreamSource.CACHE.original)
    }

    @Test
    fun `a cached transcode plays when it is at least what the network asks for`() {
        assertEquals(StreamSource.CACHED_TRANSCODE, choose(cachedKbps = 320, wantedKbps = 320))
        assertEquals(StreamSource.CACHED_TRANSCODE, choose(cachedKbps = 320, wantedKbps = 128))
        assertEquals(StreamSource.TRANSCODE, choose(cachedKbps = 128, wantedKbps = 320))
        assertTrue(StreamSource.CACHED_TRANSCODE.transcode)
    }

    @Test
    fun `on Wi-Fi with only a transcode cached, the original streams`() {
        assertEquals(StreamSource.ORIGINAL, choose(cachedKbps = 320, wantedKbps = null))
        assertTrue(StreamSource.ORIGINAL.original)
    }

    @Test
    fun `offline, any cached copy plays`() {
        assertEquals(StreamSource.CACHED_TRANSCODE, choose(cachedKbps = 128, wantedKbps = null, online = false))
        assertEquals(StreamSource.CACHED_TRANSCODE, choose(cachedKbps = 128, wantedKbps = 320, online = false))
        assertEquals(StreamSource.CACHE, choose(original = true, cachedKbps = 128, online = false))
    }

    @Test
    fun `nothing cached streams what the network asks for, and keeps it`() {
        assertEquals(StreamSource.ORIGINAL, choose())
        assertEquals(StreamSource.TRANSCODE, choose(wantedKbps = 128))
        assertTrue(StreamSource.TRANSCODE.transcode)
    }
}

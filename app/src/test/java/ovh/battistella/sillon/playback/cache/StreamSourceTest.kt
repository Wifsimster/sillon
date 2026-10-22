package ovh.battistella.sillon.playback.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourceTest {
    @Test
    fun `a download wins over the cache and the network`() {
        assertEquals(StreamSource.DOWNLOAD, StreamSource.choose(downloaded = true, originalCached = true, transcodeKbps = null))
        assertEquals(StreamSource.DOWNLOAD, StreamSource.choose(downloaded = true, originalCached = false, transcodeKbps = 128))
        assertFalse(StreamSource.DOWNLOAD.cached)
    }

    @Test
    fun `a cached original wins over a transcode, even on mobile data`() {
        assertEquals(StreamSource.CACHE, StreamSource.choose(downloaded = false, originalCached = true, transcodeKbps = 128))
        assertEquals(StreamSource.CACHE, StreamSource.choose(downloaded = false, originalCached = true, transcodeKbps = null))
        assertTrue(StreamSource.CACHE.cached)
    }

    @Test
    fun `the original from the network is cached, a transcode is not`() {
        assertEquals(StreamSource.ORIGINAL, StreamSource.choose(downloaded = false, originalCached = false, transcodeKbps = null))
        assertTrue(StreamSource.ORIGINAL.cached)
        assertEquals(StreamSource.TRANSCODE, StreamSource.choose(downloaded = false, originalCached = false, transcodeKbps = 320))
        assertFalse(StreamSource.TRANSCODE.cached)
    }
}

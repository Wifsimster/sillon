package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DidlTest {
    private val didl = Didl.track(
        key = "42",
        uri = "http://192.168.1.5:32400/library/parts/42/1/file.flac?X-Plex-Token=transient-1&a=b",
        mimeType = "audio/flac",
        title = "Rock & <Roll>",
        artist = "Artist \"A\"",
        album = "Album",
        artUri = "http://192.168.1.5:32400/photo/:/transcode?url=%2Fthumb&X-Plex-Token=transient-1",
    )

    @Test
    fun `a track is a music track with its URL as the resource`() {
        val item = parseXml(didl).descendants().first { it.localName() == "item" }
        assertEquals("sillon:42", item.getAttribute("id"))
        val res = item.descendants().first { it.localName() == "res" }
        assertEquals("http-get:*:audio/flac:*", res.getAttribute("protocolInfo"))
        assertEquals("http://192.168.1.5:32400/library/parts/42/1/file.flac?X-Plex-Token=transient-1&a=b", res.textContent)
        assertEquals("object.item.audioItem.musicTrack", item.descendants().first { it.localName() == "class" }.textContent)
    }

    @Test
    fun `titles and names are escaped and read back whole`() {
        val root = parseXml(didl)
        assertEquals("Rock & <Roll>", root.descendants().first { it.tagName == "dc:title" }.textContent)
        assertEquals("Artist \"A\"", root.descendants().first { it.tagName == "dc:creator" }.textContent)
        assertEquals("Album", root.descendants().first { it.tagName == "upnp:album" }.textContent)
    }

    @Test
    fun `missing names and a local cover are left out`() {
        val bare = Didl.track("1", "http://s/1.mp3", "audio/mpeg", "T", artist = null, album = " ", artUri = "file:///data/cover.jpg")
        assertFalse(bare.contains("dc:creator"))
        assertFalse(bare.contains("upnp:album>"))
        assertFalse(bare.contains("albumArtURI"))
        assertTrue(bare.contains("<dc:title>T</dc:title>"))
    }
}

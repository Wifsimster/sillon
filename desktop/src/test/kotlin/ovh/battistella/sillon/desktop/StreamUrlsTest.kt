package ovh.battistella.sillon.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.desktop.i18n.EnglishStrings
import ovh.battistella.sillon.desktop.i18n.FrenchStrings
import ovh.battistella.sillon.desktop.i18n.stringsFor
import ovh.battistella.sillon.playback.StreamUrls
import ovh.battistella.sillon.playback.TrackGain
import java.io.File
import java.net.URI
import java.util.Locale

class StreamUrlsTest {
    @Test
    fun `a file url round-trips through spaces accents and reserved characters`() {
        val file = File(System.getProperty("java.io.tmpdir"), "Sillon tests/Été 100% #1 [live]?.flac").absoluteFile
        val url = StreamUrls.ofFile(file)
        assertTrue(url.startsWith("file:///"))
        assertFalse(url.contains(' '))
        assertFalse(url.contains('#'))
        assertFalse(url.contains('?'))
        assertEquals(file, File(URI(url)))
    }

    @Test
    fun `transcodes are told from original files`() {
        assertTrue(StreamUrls.isTranscode("https://host/music/:/transcode/universal/start.m3u8?path=x"))
        assertTrue(StreamUrls.isTranscode("http://host:8096/Audio/1/main.m3u8?audioCodec=aac"))
        assertFalse(StreamUrls.isTranscode("https://host/library/parts/1/file.flac?X-Plex-Token=t"))
        assertFalse(StreamUrls.isTranscode("http://host:8096/Audio/1/stream.flac?static=true"))
    }

    @Test
    fun `gain only turns the volume down`() {
        assertEquals(1f, TrackGain.volumeFor(null))
        assertEquals(1f, TrackGain.volumeFor(4f))
        assertEquals(0.5f, TrackGain.volumeFor(-6.0206f), 0.001f)
        assertEquals(0.1f, TrackGain.volumeFor(-40f))
    }

    @Test
    fun `french is used for french locales and english elsewhere`() {
        assertEquals(FrenchStrings, stringsFor(Locale.FRANCE))
        assertEquals(FrenchStrings, stringsFor(Locale.CANADA_FRENCH))
        assertEquals(EnglishStrings, stringsFor(Locale.US))
        assertEquals(EnglishStrings, stringsFor(Locale.GERMANY))
        assertEquals("1 titre", FrenchStrings.trackCount(1))
        assertEquals("3 titres", FrenchStrings.trackCount(3))
        assertEquals("1 track", EnglishStrings.trackCount(1))
    }
}

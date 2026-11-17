package ovh.battistella.sillon.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.desktop.i18n.EnglishStrings
import ovh.battistella.sillon.desktop.i18n.FrenchStrings
import ovh.battistella.sillon.desktop.i18n.stringsFor
import ovh.battistella.sillon.desktop.platform.WindowChrome
import ovh.battistella.sillon.desktop.player.RadioRefill
import ovh.battistella.sillon.playback.PlayableTrack
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
    fun `a radio refill only brings tracks the queue does not have yet`() {
        fun track(key: String) = PlayableTrack(key, "http://host/$key", key, null, null, null, null, null)
        val queue = listOf(track("a"), track("b"))
        assertEquals(listOf("c", "d"), RadioRefill.fresh(queue, listOf(track("b"), track("c"), track("a"), track("d"), track("c"))).map { it.ratingKey })
        assertEquals(emptyList<PlayableTrack>(), RadioRefill.fresh(queue, listOf(track("a"))))
    }

    @Test
    fun `window colours are handed to Windows as blue-green-red`() {
        assertEquals(0x000F1215, WindowChrome.colorRef(0xFF15120F.toInt()))
        assertEquals(0x00EEF6FB, WindowChrome.colorRef(0xFFFBF6EE.toInt()))
        WindowChrome.blend(0, 0xFF15120F.toInt(), dark = true)
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

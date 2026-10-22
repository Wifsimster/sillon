package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinItem
import ovh.battistella.sillon.data.jellyfin.JellyfinMediaSource
import ovh.battistella.sillon.data.jellyfin.JellyfinMediaStream
import ovh.battistella.sillon.data.jellyfin.toMetadata
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.PlexResponse

class SonosFormatsTest {
    private fun plan(codec: String?, container: String? = null, rate: Int? = null, bits: Int? = null, kbps: Int? = null) =
        SonosFormats.plan(AudioFormat(codec, container, rate, bits, kbps))

    private fun direct(mime: String) = SonosStream.Direct(mime)

    @Test
    fun `CD-quality and 24-bit 48 kHz lossless play as they are`() {
        assertEquals(direct("audio/flac"), plan("flac", "flac", 44_100, 16))
        assertEquals(direct("audio/flac"), plan("flac", "flac", 48_000, 24))
        assertEquals(direct("audio/mp4"), plan("alac", "mp4", 44_100, 16))
        assertEquals(direct("audio/wav"), plan("pcm_s16le", "wav", 44_100, 16))
    }

    @Test
    fun `hi-res is transcoded`() {
        assertEquals(SonosStream.Transcode, plan("flac", "flac", 96_000, 24))
        assertEquals(SonosStream.Transcode, plan("flac", "flac", 192_000, 24))
        assertEquals(SonosStream.Transcode, plan("alac", "mp4", 88_200, 24))
        assertEquals(SonosStream.Transcode, plan("flac", "flac", 48_000, 32))
        // WAV and AIFF stop at 16 bit.
        assertEquals(SonosStream.Transcode, plan("pcm_s24le", "wav", 48_000, 24))
    }

    @Test
    fun `lossless of unknown rate is transcoded, it may be hi-res`() {
        assertEquals(SonosStream.Transcode, plan("flac", "flac"))
        assertEquals(SonosStream.Transcode, plan(null, "flac"))
    }

    @Test
    fun `MP3, AAC and Vorbis play as they are up to 320 kbps`() {
        assertEquals(direct("audio/mpeg"), plan("mp3", "mp3", 44_100, null, 320))
        assertEquals(direct("audio/mpeg"), plan(null, "mp3"))
        assertEquals(direct("audio/mp4"), plan("aac", "mp4", 44_100, null, 256))
        assertEquals(direct("audio/ogg"), plan("vorbis", "ogg", 44_100, null, 192))
        // A VBR average a little over 320 is still a 320 file.
        assertEquals(direct("audio/mpeg"), plan("mp3", "mp3", 44_100, null, 330))
        assertEquals(SonosStream.Transcode, plan("aac", "mp4", 44_100, null, 512))
    }

    @Test
    fun `what Sonos doesn't list is transcoded`() {
        listOf("opus", "wmav2", "dsd_lsbf", "ape", "wavpack", "mp2").forEach { codec ->
            assertEquals(codec, SonosStream.Transcode, plan(codec, null, 48_000, 16))
        }
        assertEquals(SonosStream.Transcode, plan(null, null))
        // A container that may hold several codecs is not guessed.
        assertEquals(SonosStream.Transcode, plan(null, "m4a"))
        assertEquals(SonosStream.Transcode, plan(null, "ogg"))
    }

    @Test
    fun `a Plex track gives its format from the audio stream`() {
        val json = """{"MediaContainer":{"Metadata":[{"ratingKey":"7","type":"track","title":"T","Media":[{"audioCodec":"flac",""" +
            """"container":"flac","bitrate":1411,"Part":[{"key":"/library/parts/7/1/file.flac","Stream":[""" +
            """{"streamType":2,"codec":"flac","samplingRate":96000,"bitDepth":24,"bitrate":2800}]}]}]}]}}"""
        val track = PlexJson.decodeFromString(PlexResponse.serializer(), json).container.metadata.single()
        assertEquals(AudioFormat("flac", "flac", 96_000, 24, 2800), track.audioFormat())
        assertEquals(SonosStream.Transcode, SonosFormats.plan(track.audioFormat()))
    }

    @Test
    fun `a Jellyfin track gives its format from its media source`() {
        val item = JellyfinItem(
            id = "abc",
            type = "Audio",
            container = "flac",
            mediaSources = listOf(
                JellyfinMediaSource(
                    container = "flac",
                    streams = listOf(JellyfinMediaStream(type = "Audio", codec = "flac", sampleRate = 44_100, bitDepth = 16, bitRate = 900_000)),
                ),
            ),
        )
        val format = item.toMetadata().audioFormat()
        assertEquals(AudioFormat("flac", "flac", 44_100, 16, 900), format)
        assertEquals(direct("audio/flac"), SonosFormats.plan(format))
    }
}

package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinItem
import ovh.battistella.sillon.data.jellyfin.toMetadata
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.PlexResponse
import ovh.battistella.sillon.data.repository.Loudness
import ovh.battistella.sillon.data.settings.LoudnessMode

class LoudnessTest {

    @Test
    fun `a negative gain turns the volume down, a positive one can't turn it up`() {
        assertEquals(1f, MediaItems.volumeFor(null), 0f)
        assertEquals(0.5f, MediaItems.volumeFor(-6.02f), 0.01f)
        assertEquals(1f, MediaItems.volumeFor(3f), 0f)
        // A broken tag never silences a track.
        assertEquals(0.1f, MediaItems.volumeFor(-60f), 0f)
    }

    @Test
    fun `reads Plex gains from the audio stream of a full item`() {
        val json = """
            {"MediaContainer": {"Metadata": [{"ratingKey": "1", "Media": [{"Part": [{"key": "/p", "Stream": [
              {"streamType": 2, "gain": -7.5, "albumGain": -6.1, "peak": 0.98},
              {"streamType": 4}]}]}]}]}}
        """.trimIndent()
        val track = PlexJson.decodeFromString(PlexResponse.serializer(), json).container.metadata.single()

        assertEquals(-7.5f, track.trackGain)
        assertEquals(-6.1f, track.albumGain)
    }

    @Test
    fun `takes Jellyfin's normalization gain as the track gain`() {
        val track = JellyfinItem(id = "t1", type = "Audio", normalizationGain = -4.4f).toMetadata()
        assertEquals(-4.4f, track.trackGain)
        assertEquals(-4.4f, track.albumGain) // no album gain: falls back to the track's
    }

    @Test
    fun `picks the gain for the chosen mode`() {
        val loudness = Loudness(track = -7f, album = -5f)
        assertNull(loudness.forMode(LoudnessMode.OFF))
        assertEquals(-7f, loudness.forMode(LoudnessMode.TRACK))
        assertEquals(-5f, loudness.forMode(LoudnessMode.ALBUM))
        assertEquals(-7f, Loudness(track = -7f, album = null).forMode(LoudnessMode.ALBUM))
    }
}

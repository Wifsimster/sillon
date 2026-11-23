package ovh.battistella.sillon.desktop

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.settings.HiddenArtists

class HiddenArtistsTest {
    private fun track(key: String, albumArtist: String?, trackArtist: String? = null) =
        PlexMetadata(ratingKey = key, type = "track", grandparentTitle = albumArtist, originalTitle = trackArtist)

    @Test
    fun `a radio leaves out every track credited to a hidden artist`() {
        val tracks = listOf(
            track("kept", "Daft Punk"),
            track("album", "Astra Blue AI"),
            track("case", "astra blue ai"),
            track("guest", "Various Artists", "Taprooted feat. Someone"),
            track("compilation", "Various Artists", "Air"),
        )

        val kept = HiddenArtists.keep(tracks, setOf("Astra Blue AI", "Taprooted"))

        assertEquals(listOf("kept", "compilation"), kept.map { it.ratingKey })
    }

    @Test
    fun `nothing hidden keeps the whole draw`() {
        val tracks = listOf(track("a", "Astra Blue AI"))

        assertEquals(tracks, HiddenArtists.keep(tracks, emptySet()))
    }

    @Test
    fun `a name inside another artist's name is not a match`() {
        assertFalse(HiddenArtists.credits("Air Supply", setOf("Air")))
        assertTrue(HiddenArtists.credits("Air & Beck", setOf("Air")))
    }
}

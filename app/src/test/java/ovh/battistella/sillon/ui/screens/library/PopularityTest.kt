package ovh.battistella.sillon.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexMetadata

class PopularityTest {

    private fun track(key: String, listeners: Long?) = PlexMetadata(ratingKey = key, title = key, ratingCount = listeners)

    @Test
    fun `popularity spans the album's own range on a log scale`() {
        val pop = popularityOf(
            listOf(track("hit", 1_000_000), track("single", 31_623), track("intro", 1_000), track("unknown", null)),
        )

        assertEquals(1f, pop.getValue("hit"), 0.001f)
        // Halfway between 1 000 and 1 000 000 on a log scale.
        assertEquals(0.5f, pop.getValue("single"), 0.01f)
        assertEquals(0f, pop.getValue("intro"), 0.001f)
        assertNull(pop["unknown"])
    }

    @Test
    fun `an album of equally popular tracks is all hits`() {
        val pop = popularityOf(listOf(track("a", 5_000), track("b", 5_000)))
        assertEquals(listOf(1f, 1f), pop.values.toList())
    }

    @Test
    fun `no popularity at all when Plex has no figures`() {
        assertTrue(popularityOf(listOf(track("a", null), track("b", 0))).isEmpty())
    }

    @Test
    fun `stars are half the Plex rating`() {
        assertEquals(4.5f, PlexMetadata(ratingKey = "1", userRating = 9f).stars)
        assertNull(PlexMetadata(ratingKey = "1", userRating = 0f).stars)
        assertNull(PlexMetadata(ratingKey = "1").stars)
    }
}

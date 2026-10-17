package ovh.battistella.sillon.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexMetadata

class PopularityTest {

    private fun track(key: String, listeners: Long?) = PlexMetadata(ratingKey = key, title = key, ratingCount = listeners)

    @Test
    fun `the most popular track of the list is full, the others relative on a log scale`() {
        val pop = popularityOf(listOf(track("hit", 1_000_000), track("deep cut", 1_000), track("unknown", null)))

        assertEquals(1f, pop.getValue("hit"), 0.001f)
        // 1 000 listeners is half of a million on a log scale, not 0.1 %.
        assertEquals(0.5f, pop.getValue("deep cut"), 0.01f)
        assertNull(pop["unknown"])
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

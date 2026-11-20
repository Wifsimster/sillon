package ovh.battistella.sillon.desktop.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackSortTest {
    private fun track(key: String, stars: Int? = null, plays: Int? = null, title: String = key, number: Int? = null) =
        TrackUi(key, number, title, null, null, stars = stars, plays = plays)

    private val tracks = listOf(
        track("a", stars = 3, plays = 2, number = 1),
        track("b", stars = null, plays = null, number = 2),
        track("c", stars = 5, plays = 9, number = 3),
        track("d", stars = 1, plays = 0, number = 4),
        track("e", stars = 5, plays = 2, number = 5),
    )

    private fun List<TrackUi>.keys() = joinToString("") { it.key }

    @Test
    fun `stars sort best first, unrated last, ties keep album order`() {
        assertEquals("ceadb", tracks.sortedBy(TrackOrder(TrackSort.STARS, descending = true)).keys())
    }

    @Test
    fun `reversed stars still keep unrated last`() {
        assertEquals("daceb", tracks.sortedBy(TrackOrder(TrackSort.STARS, descending = false)).keys())
    }

    @Test
    fun `plays sort most played first`() {
        assertEquals("caedb", tracks.sortedBy(TrackOrder(TrackSort.PLAYS, descending = true)).keys())
    }

    @Test
    fun `no order keeps the original list`() {
        assertEquals("abcde", tracks.sortedBy(null).keys())
    }

    @Test
    fun `title sort ignores case and accents`() {
        val list = listOf(track("1", title = "zèbre"), track("2", title = "Écho"), track("3", title = "abc"))
        assertEquals("321", list.sortedBy(TrackOrder(TrackSort.TITLE, descending = false)).keys())
    }

    @Test
    fun `header click cycles natural direction, reversed, then off`() {
        val first = null.next(TrackSort.STARS)
        assertEquals(TrackOrder(TrackSort.STARS, descending = true), first)
        val second = first.next(TrackSort.STARS)
        assertEquals(TrackOrder(TrackSort.STARS, descending = false), second)
        assertNull(second.next(TrackSort.STARS))
        assertEquals(TrackOrder(TrackSort.TITLE, descending = false), second.next(TrackSort.TITLE))
    }

    @Test
    fun `saved order round-trips and bad values are ignored`() {
        TrackSort.entries.forEach { by ->
            listOf(true, false).forEach { desc ->
                val order = TrackOrder(by, desc)
                assertEquals(order, decodeTrackOrder(order.encode()))
            }
        }
        assertNull(decodeTrackOrder(null))
        assertNull(decodeTrackOrder("STARS"))
        assertNull(decodeTrackOrder("GONE:desc"))
        assertNull(decodeTrackOrder("STARS:sideways"))
    }
}

package ovh.battistella.sillon.data.rating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinItem
import ovh.battistella.sillon.data.jellyfin.JellyfinUserData
import ovh.battistella.sillon.data.jellyfin.toMetadata
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.settings.FiveStarsCache
import java.io.IOException

class RatingsTest {
    private fun track(key: String, rating: Float? = null) = PlexMetadata(ratingKey = key, type = "track", userRating = rating)

    @Test
    fun `stars are the server rating halved, half stars rounded up`() {
        assertNull(Stars.of(null))
        assertNull(Stars.of(0f))
        assertEquals(1, Stars.of(1f))
        assertEquals(1, Stars.of(2f))
        assertEquals(3, Stars.of(5f))
        assertEquals(5, Stars.of(10f))
        assertEquals(2, Stars.toRating(1))
        assertEquals(10, Stars.toRating(5))
        assertEquals(Stars.NONE, Stars.toRating(null))
    }

    @Test
    fun `touching the same star again clears the rating`() {
        assertEquals(4, Stars.toggle(null, 4))
        assertEquals(2, Stars.toggle(4, 2))
        assertNull(Stars.toggle(4, 4))
    }

    @Test
    fun `Jellyfin keeps 1 and 5 stars only`() {
        assertTrue(JellyfinStars.supports(1))
        assertTrue(JellyfinStars.supports(5))
        assertTrue(JellyfinStars.supports(null))
        (2..4).forEach { assertFalse(JellyfinStars.supports(it)) }
    }

    @Test
    fun `a Jellyfin favourite is 5 stars, a dislike 1 star`() {
        fun rated(favourite: Boolean, likes: Boolean?) =
            JellyfinItem(id = "t", type = "Audio", userData = JellyfinUserData(isFavorite = favourite, likes = likes)).toMetadata().userRating

        assertEquals(10f, rated(favourite = true, likes = null))
        assertEquals(10f, rated(favourite = true, likes = false))
        assertEquals(2f, rated(favourite = false, likes = false))
        assertNull(rated(favourite = false, likes = true))
        assertNull(rated(favourite = false, likes = null))
    }

    @Test
    fun `a rating given here shows at once and waits to be sent`() {
        val book = RatingBook().rate("a", 1)

        assertEquals(1, book.starsOf("a", server = 10f))
        assertTrue(book.isOneStar("a", server = 10f))
        assertEquals(mapOf("a" to 2), book.pending)
        assertEquals(RatingSend.SENDING, book.sendState("a"))
    }

    @Test
    fun `clearing a rating overrides the server's`() {
        val book = RatingBook().rate("a", null)

        assertNull(book.starsOf("a", server = 10f))
        assertEquals(mapOf("a" to 0), book.pending)
    }

    @Test
    fun `the server's rating counts when the phone has none`() {
        assertEquals(4, RatingBook().starsOf("a", server = 8f))
        assertTrue(RatingBook().isOneStar("a", server = 2f))
    }

    @Test
    fun `sent clears the line, unless a newer rating came meanwhile`() {
        val book = RatingBook().rate("a", 5)
        assertNull(book.sent("a", 10).sendState("a"))
        assertEquals(5, book.sent("a", 10).starsOf("a", null))

        val newer = book.rate("a", 1)
        assertEquals(mapOf("a" to 2), newer.sent("a", 10).pending)
    }

    @Test
    fun `offline keeps the rating in line, refused stops and says so`() {
        val book = RatingBook().rate("a", 5)

        val offline = book.offline("a")
        assertEquals(RatingSend.WAITING_FOR_NETWORK, offline.sendState("a"))
        assertEquals(mapOf("a" to 10), offline.pending)

        val refused = offline.refused("a")
        assertEquals(RatingSend.FAILED, refused.sendState("a"))
        assertTrue(refused.pending.isEmpty())
        assertEquals(5, refused.starsOf("a", null))

        assertEquals(RatingSend.SENDING, refused.rate("a", 5).sendState("a"))
    }

    @Test
    fun `learns 1-star tracks from the server, and corrects what it knew`() {
        val book = RatingBook().learn(listOf(track("bad", 2f), track("good", 10f), track("none")))
        assertEquals(mapOf("bad" to 2), book.known)

        val changed = book.learn(listOf(track("bad", 8f)))
        assertEquals(mapOf("bad" to 8), changed.known)
        assertFalse(changed.isOneStar("bad", null))
    }

    @Test
    fun `a rating waiting to be sent wins over the server`() {
        val book = RatingBook().rate("a", 1).learn(listOf(track("a", 10f)))

        assertTrue(book.isOneStar("a", 10f))
    }

    @Test
    fun `learning nothing new keeps the same book`() {
        val book = RatingBook().learn(listOf(track("bad", 2f)))
        assertSame(book, book.learn(listOf(track("bad", 2f), track("x"))))
    }

    @Test
    fun `the book stays bounded, cleared ratings going first`() {
        val many = (0 until RatingBook.MAX_KNOWN).associate { "k$it" to 2 }
        val book = RatingBook(known = many + ("cleared" to 0)).rate("new", 5)

        assertEquals(RatingBook.MAX_KNOWN, book.known.size)
        assertFalse("cleared" in book.known)
        assertEquals(10, book.known["new"])
    }

    @Test
    fun `queues drawn for you leave out 1-star tracks`() {
        val book = RatingBook().rate("mine", 1).rate("rescued", 4)
        val tracks = listOf(track("a"), track("server", 2f), track("mine"), track("rescued", 2f), track("half", 1f))

        assertEquals(listOf("a", "rescued"), book.withoutOneStar(tracks).map { it.ratingKey })
    }

    @Test
    fun `network failures wait, refusals stop`() {
        assertEquals(SendOutcome.SENT, sendOutcomeOf(null))
        assertEquals(SendOutcome.OFFLINE, sendOutcomeOf(IOException("no route")))
        assertEquals(SendOutcome.OFFLINE, sendOutcomeOf(PlexHttpException(503, "busy")))
        assertEquals(SendOutcome.REFUSED, sendOutcomeOf(PlexHttpException(401, "no")))
        assertEquals(SendOutcome.REFUSED, sendOutcomeOf(IllegalArgumentException("3 stars on Jellyfin")))
    }

    @Test
    fun `the 5-star list follows a new rating`() {
        assertEquals(setOf("a", "b"), FiveStarsCache.marked(setOf("a"), "b", fiveStars = true))
        assertEquals(setOf("a"), FiveStarsCache.marked(setOf("a", "b"), "b", fiveStars = false))
        assertEquals(setOf("a"), FiveStarsCache.marked(setOf("a"), "b", fiveStars = false))
    }
}

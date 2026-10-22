package ovh.battistella.sillon.data.rating

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind

/** The requests that carry a rating to Plex and to Jellyfin 10.10. */
class RatingRequestsTest {
    private val server = MockWebServer()
    private val device = PlexDevice("Sillon", "1.0.0", "15", "Pixel")
    private val plex = PlexApi(OkHttpClient(), device)
    private val jellyfin = JellyfinApi(OkHttpClient(), device)
    private lateinit var plexServer: ServerEndpoint
    private lateinit var jellyfinServer: ServerEndpoint

    @Before
    fun setUp() {
        server.start()
        plexServer = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")
        jellyfinServer = ServerEndpoint(server.url("/jf").toString().trimEnd('/'), "tok", ServerKind.JELLYFIN, "user-1")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun RecordedRequest.line() = "$method ${requestUrl!!.encodedPath}"

    @Test
    fun `Plex rates with a PUT on the library's rate endpoint`() = runTest {
        server.enqueue(MockResponse())

        plex.rate("client", plexServer, "42", 8)

        val request = server.takeRequest()
        assertEquals("PUT /:/rate", request.line())
        val url = request.requestUrl!!
        assertEquals("42", url.queryParameter("key"))
        assertEquals("com.plexapp.plugins.library", url.queryParameter("identifier"))
        assertEquals("8", url.queryParameter("rating"))
        assertEquals("tok", request.getHeader("X-Plex-Token"))
    }

    @Test
    fun `Plex clears a rating with -1`() = runTest {
        server.enqueue(MockResponse())

        plex.rate("client", plexServer, "42", Stars.NONE)

        assertEquals("-1", server.takeRequest().requestUrl!!.queryParameter("rating"))
    }

    @Test
    fun `Jellyfin 5 stars is a favourite, and drops a dislike`() = runTest {
        repeat(2) { server.enqueue(MockResponse()) }

        jellyfin.rate(jellyfinServer, "device", "t1", 5)

        val favourite = server.takeRequest()
        assertEquals("POST /jf/UserFavoriteItems/t1", favourite.line())
        assertEquals("user-1", favourite.requestUrl!!.queryParameter("userId"))
        assertEquals("DELETE /jf/UserItems/t1/Rating", server.takeRequest().line())
    }

    @Test
    fun `Jellyfin 1 star is a dislike, and drops the favourite`() = runTest {
        repeat(2) { server.enqueue(MockResponse()) }

        jellyfin.rate(jellyfinServer, "device", "t1", 1)

        assertEquals("DELETE /jf/UserFavoriteItems/t1", server.takeRequest().line())
        val dislike = server.takeRequest()
        assertEquals("POST /jf/UserItems/t1/Rating", dislike.line())
        assertEquals("false", dislike.requestUrl!!.queryParameter("likes"))
    }

    @Test
    fun `Jellyfin clearing removes both`() = runTest {
        repeat(2) { server.enqueue(MockResponse()) }

        jellyfin.rate(jellyfinServer, "device", "t1", null)

        assertEquals("DELETE /jf/UserFavoriteItems/t1", server.takeRequest().line())
        val like = server.takeRequest()
        assertEquals("DELETE /jf/UserItems/t1/Rating", like.line())
        assertNull(like.requestUrl!!.queryParameter("likes"))
    }

    @Test
    fun `Jellyfin refuses 2 to 4 stars before sending anything`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { jellyfin.rate(jellyfinServer, "device", "t1", 3) }
        }
        assertEquals(0, server.requestCount)
    }
}

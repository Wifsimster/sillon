package ovh.battistella.sillon.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind

class LyricsApiTest {
    private val server = MockWebServer()
    private val device = PlexDevice("Sillon", "1.0.0", "15", "Pixel")

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `Plex lyrics come from the track's lyric stream`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Metadata": [{"ratingKey": "42", "title": "Song", "parentYear": 1979,
                "Media": [{"Part": [{"key": "/library/parts/1/file.flac", "Stream": [
                  {"streamType": 2}, {"streamType": 4, "key": "/library/streams/77", "format": "lrc"}]}]}]}]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("[00:01.00]Hello\n[00:02.00]World"))
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")

        val raw = PlexApi(OkHttpClient(), device).lyrics("client", endpoint, "42")

        assertEquals(listOf("Hello", "World"), Lyrics.parse(raw))
        assertEquals("/library/metadata/42", server.takeRequest().requestUrl!!.encodedPath)
        val stream = server.takeRequest()
        assertEquals("/library/streams/77", stream.requestUrl!!.encodedPath)
        assertEquals("tok", stream.getHeader("X-Plex-Token"))
    }

    @Test
    fun `a Plex track without a lyric stream has no lyrics`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Metadata": [{"ratingKey": "42", "Media": [{"Part": [{"key": "/p", "Stream": [{"streamType": 2}]}]}]}]}}""",
            ),
        )
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")

        assertNull(PlexApi(OkHttpClient(), device).lyrics("client", endpoint, "42"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `Jellyfin lyrics, and none when the server says 404`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Lyrics": [{"Text": "Une ligne"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        val endpoint = ServerEndpoint(server.url("/jf").toString().trimEnd('/'), "tok", ServerKind.JELLYFIN, "user-1")
        val api = JellyfinApi(OkHttpClient(), device)

        assertEquals(listOf("Une ligne"), Lyrics.parse(api.lyrics(endpoint, "device", "t1")))
        assertNull(api.lyrics(endpoint, "device", "t2"))
        assertEquals("/jf/Audio/t1/Lyrics", server.takeRequest().requestUrl!!.encodedPath)
    }
}

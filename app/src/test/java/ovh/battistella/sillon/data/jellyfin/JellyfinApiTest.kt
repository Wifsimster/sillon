package ovh.battistella.sillon.data.jellyfin

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind

/** Request and response shapes taken from a real Jellyfin server (the public demo). */
class JellyfinApiTest {

    private val server = MockWebServer()
    private val api = JellyfinApi(OkHttpClient(), PlexDevice("Sillon", "1.0.0", "15", "Pixel"))
    private lateinit var endpoint: ServerEndpoint

    @Before
    fun setUp() {
        server.start()
        endpoint = ServerEndpoint(server.url("/jf").toString().trimEnd('/'), "tok", ServerKind.JELLYFIN, "user-1")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `signs in with a user name and password`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"AccessToken": "tok", "ServerId": "srv", "User": {"Id": "user-1", "Name": "me"}}"""),
        )

        val auth = api.authenticate(endpoint.uri, "device-1", "me", "secret")

        assertEquals("tok", auth.accessToken)
        assertEquals("user-1", auth.user.id)
        val request = server.takeRequest()
        assertEquals("/jf/Users/AuthenticateByName", request.requestUrl!!.encodedPath)
        assertTrue(request.body.readUtf8().contains("\"Pw\":\"secret\""))
        val auth2 = request.getHeader("Authorization")!!
        assertTrue(auth2.startsWith("MediaBrowser Client=\"Sillon\""))
        assertTrue(auth2.contains("DeviceId=\"device-1\""))
    }

    @Test
    fun `maps an album's tracks onto the app's model`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"Items": [{"Id": "t1", "Name": "Jellyfin", "Type": "Audio", "IndexNumber": 10,
                  "ParentIndexNumber": 1, "RunTimeTicks": 1830508539, "Album": "Nemesis", "AlbumId": "a1",
                  "AlbumArtist": "Leap Fidei", "Artists": ["Leap Fidei"], "Container": "flac",
                  "AlbumPrimaryImageTag": "x", "ImageTags": {"Primary": "y"},
                  "UserData": {"IsFavorite": true, "PlayCount": 34}}], "TotalRecordCount": 1}""",
            ),
        )

        val track = api.tracksOf(endpoint, "device-1", "a1").single()

        assertEquals("track", track.type)
        assertEquals("Nemesis", track.parentTitle)
        assertEquals("Leap Fidei", track.trackArtist)
        assertEquals(183_050L, track.duration)
        assertEquals(10, track.index)
        assertEquals(5f, track.stars) // a favourite counts as 5 stars
        assertEquals("/Audio/t1/stream.flac?static=true", track.partKey)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/jf/Items", url.encodedPath)
        assertEquals("a1", url.queryParameter("parentId"))
        assertEquals("user-1", url.queryParameter("userId"))
    }

    @Test
    fun `groups search results like Plex hubs`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"Items": [{"Id": "r1", "Name": "Leap Fidei", "Type": "MusicArtist"},
                  {"Id": "t1", "Name": "Jellyfin", "Type": "Audio"}]}""",
            ),
        )

        val hubs = api.search(endpoint, "device-1", "music", "leap")

        assertEquals(listOf("artist", "track"), hubs.map { it.type })
        assertEquals("leap", server.takeRequest().requestUrl!!.queryParameter("searchTerm"))
    }

    @Test
    fun `media URLs carry the token for the player`() {
        val stream = api.streamUrl(endpoint, "/Audio/t1/stream.flac?static=true").toHttpUrl()
        assertEquals("/jf/Audio/t1/stream.flac", stream.encodedPath)
        assertEquals("true", stream.queryParameter("static"))
        assertEquals("tok", stream.queryParameter("ApiKey"))

        val hls = api.transcodeUrl(endpoint, "device-1", "t1", 192, "s1").toHttpUrl()
        assertEquals("/jf/Audio/t1/main.m3u8", hls.encodedPath)
        assertEquals("192000", hls.queryParameter("audioBitRate"))
        assertEquals("aac", hls.queryParameter("audioCodec"))
        assertEquals("tok", hls.queryParameter("ApiKey"))

        val art = api.artworkUrl(endpoint, "${JELLYFIN_IMAGE}a1", 400)!!.toHttpUrl()
        assertEquals("/jf/Items/a1/Images/Primary", art.encodedPath)
        assertNull(api.artworkUrl(endpoint, "/library/metadata/1/thumb", 400))
    }

    @Test
    fun `reports playback in ticks`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.reportPlayback(endpoint, "device-1", "Playing/Progress", "t1", positionMs = 5_000, paused = true)

        val request = server.takeRequest()
        assertEquals("/jf/Sessions/Playing/Progress", request.requestUrl!!.encodedPath)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"PositionTicks\":50000000"))
        assertTrue(body.contains("\"IsPaused\":true"))
    }

    @Test
    fun `stream paths name the container for downloads`() {
        assertEquals("/Audio/t1/stream.mp3?static=true", streamPath("t1", "mp3"))
        assertEquals("/Audio/t1/stream?static=true", streamPath("t1", null))
    }
}

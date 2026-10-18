package ovh.battistella.sillon.data.plex

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PlexApiTest {
    private val server = MockWebServer()
    private val device = PlexDevice("Sillon", "1.0.0", "15", "Pixel")
    private lateinit var api: PlexApi

    @Before
    fun setUp() {
        server.start()
        api = PlexApi(OkHttpClient(), device, server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `creates a PIN with the client headers`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": 42, "code": "AB12", "expiresIn": 900, "authToken": null}"""))

        val pin = api.createPin("client-1", strong = false)

        assertEquals(42L, pin.id)
        assertEquals("AB12", pin.code)
        assertNull(pin.authToken)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/pins", request.requestUrl!!.encodedPath)
        assertNull(request.requestUrl!!.queryParameter("strong"))
        assertEquals("client-1", request.getHeader("X-Plex-Client-Identifier"))
        assertEquals("Sillon", request.getHeader("X-Plex-Product"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `asks for a strong PIN for the browser sign-in`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": 43, "code": "wwxn988ytlwh8jt7ct622327n"}"""))

        api.createPin("client-1", strong = true)

        assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("strong"))
    }

    @Test
    fun `sends the token as a header for library calls`() = runTest {
        server.enqueue(MockResponse().setBody("""{"MediaContainer": {"Metadata": [{"ratingKey": "5", "title": "A"}]}}"""))
        val endpoint = ServerEndpoint(server.url("/").toString(), "srv-token")

        val artists = api.artists("client-1", endpoint, "3")

        assertEquals("A", artists.single().title)
        val request = server.takeRequest()
        assertEquals("/library/sections/3/all", request.requestUrl!!.encodedPath)
        assertEquals("8", request.requestUrl!!.queryParameter("type"))
        assertEquals("srv-token", request.getHeader("X-Plex-Token"))
    }

    @Test
    fun `reports a revoked token as unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            api.sections("client-1", ServerEndpoint(server.url("/").toString(), "bad"))
            fail("expected PlexHttpException")
        } catch (e: PlexHttpException) {
            assertTrue(e.isUnauthorized)
        }
    }

    @Test
    fun `builds direct-play and artwork URLs on the chosen server`() {
        val endpoint = ServerEndpoint("https://10-0-0-2.abc.plex.direct:32400", "t0k")

        assertEquals(
            "https://10-0-0-2.abc.plex.direct:32400/library/parts/7/1700000000/file.flac?X-Plex-Token=t0k",
            api.streamUrl(endpoint, "/library/parts/7/1700000000/file.flac"),
        )
        val art = api.artworkUrl(endpoint, "/library/metadata/100/thumb/1700000000", 400)!!
        assertTrue(art.startsWith("https://10-0-0-2.abc.plex.direct:32400/photo/:/transcode?width=400&height=400"))
        assertTrue(art.contains("url=%2Flibrary%2Fmetadata%2F100%2Fthumb%2F1700000000"))
        assertNull(api.artworkUrl(endpoint, null, 400))
    }

    @Test
    fun `auth URL carries the client id and code`() {
        val url = api.authUrl("client-1", PlexPin(id = 1, code = "AB12"))
        assertTrue(url.startsWith("https://app.plex.tv/auth#?clientID=client-1&code=AB12"))
        assertTrue(url.contains("context%5Bdevice%5D%5Bproduct%5D=Sillon"))
    }

    @Test
    fun `reports a stopped track that hands over to the next one`() = runTest {
        server.enqueue(MockResponse().setBody("""{"MediaContainer": {"size": 0}}"""))
        val endpoint = ServerEndpoint(server.url("/").toString(), "srv-token")

        api.timeline("client-1", endpoint, "101", TimelineState.Stopped, 61_000, 61_000, continuing = true)

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("POST", request.method)
        assertEquals("/:/timeline", url.encodedPath)
        assertEquals("101", url.queryParameter("ratingKey"))
        assertEquals("/library/metadata/101", url.queryParameter("key"))
        assertEquals("stopped", url.queryParameter("state"))
        assertEquals("61000", url.queryParameter("time"))
        assertEquals("61000", url.queryParameter("duration"))
        assertEquals("1", url.queryParameter("continuing"))
        assertEquals("srv-token", request.getHeader("X-Plex-Token"))
        assertEquals("client-1", request.getHeader("X-Plex-Client-Identifier"))
    }

    @Test
    fun `only stopped reports say whether playback continues`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        api.timeline("c", ServerEndpoint(server.url("/").toString(), "t"), "1", TimelineState.Playing, 5_000, 60_000)
        assertNull(server.takeRequest().requestUrl!!.queryParameter("continuing"))
    }

    @Test
    fun `recovers the server and token from a stream URL`() {
        val endpoint = ServerEndpoint("https://10-0-0-2.abc.plex.direct:32400", "t0k")
        val url = api.streamUrl(endpoint, "/library/parts/7/1700000000/file.flac")

        assertEquals(endpoint, streamEndpoint(url))
        assertNull(streamEndpoint("https://example.com/no-token.mp3"))
        assertNull(streamEndpoint("not a url"))
    }

    @Test
    fun `searches the music library and groups results by type`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Hub": [
                  {"type": "artist", "title": "Artists", "Metadata": [{"ratingKey": "5", "title": "Daft Punk"}]},
                  {"type": "track", "title": "Tracks", "Metadata": [
                    {"ratingKey": "9", "title": "One More Time", "parentRatingKey": "8", "parentTitle": "Discovery"}]}
                ]}}""",
            ),
        )
        val endpoint = ServerEndpoint(server.url("/").toString(), "t")

        val hubs = api.search("c", endpoint, "3", "daft")

        assertEquals(listOf("artist", "track"), hubs.map { it.type })
        assertEquals("8", hubs[1].metadata.single().parentRatingKey)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/hubs/search", url.encodedPath)
        assertEquals("daft", url.queryParameter("query"))
        assertEquals("3", url.queryParameter("sectionId"))
    }

    @Test
    fun `lists audio playlists with their mosaic cover`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Metadata": [
                  {"ratingKey": "77", "title": "Running", "leafCount": 42, "composite": "/playlists/77/composite/1"}]}}""",
            ),
        )

        val playlist = api.playlists("c", ServerEndpoint(server.url("/").toString(), "t")).single()

        assertEquals(42, playlist.leafCount)
        assertEquals("/playlists/77/composite/1", playlist.artwork)
        assertEquals("audio", server.takeRequest().requestUrl!!.queryParameter("playlistType"))
    }

    @Test
    fun `five-star tracks are filtered by the server and checked again here`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Metadata": [
                  {"ratingKey": "1", "title": "Loved", "userRating": 10.0, "ratingCount": 1200000},
                  {"ratingKey": "2", "title": "Liked", "userRating": 8.0},
                  {"ratingKey": "3", "title": "Unrated"}]}}""",
            ),
        )

        val tracks = api.fiveStarTracks("c", ServerEndpoint(server.url("/").toString(), "t"), "3")

        assertEquals(listOf("1"), tracks.map { it.ratingKey })
        assertEquals(5f, tracks.single().stars)
        assertEquals(1_200_000L, tracks.single().ratingCount)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/library/sections/3/all", url.encodedPath)
        assertEquals("10", url.queryParameter("type"))
        assertEquals("10", url.queryParameter("userRating"))
    }

    @Test
    fun `builds an AAC transcode of a track as an HLS stream`() {
        val endpoint = ServerEndpoint("https://10-0-0-2.abc.plex.direct:32400", "t0k")
        val url = api.transcodeUrl(endpoint, "client-1", "101", 192, "sess-1").toHttpUrl()

        assertEquals("/music/:/transcode/universal/start.m3u8", url.encodedPath)
        assertEquals("/library/metadata/101", url.queryParameter("path"))
        assertEquals("hls", url.queryParameter("protocol"))
        assertEquals("0", url.queryParameter("directPlay"))
        assertEquals("192", url.queryParameter("musicBitrate"))
        assertEquals("sess-1", url.queryParameter("session"))
        assertEquals("client-1", url.queryParameter("X-Plex-Client-Identifier"))
        assertTrue(url.queryParameter("X-Plex-Client-Profile-Extra")!!.contains("audioCodec=aac"))
        assertEquals(endpoint, streamEndpoint(url.toString()))
    }

    @Test
    fun `plays a station through a play queue`() = runTest {
        server.enqueue(MockResponse().setBody("""{"MediaContainer": {"playQueueID": 977, "Metadata": [{"ratingKey": "1", "title": "First"}]}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"playQueueID": 977, "Metadata": [{"ratingKey": "1", "title": "First"}, {"ratingKey": "2", "title": "Second"}]}}""",
            ),
        )
        val endpoint = ServerEndpoint(server.url("/").toString(), "t")

        val tracks = api.stationTracks("c", endpoint, "machine-1", "/library/sections/3/stations/1", count = 100)

        assertEquals(listOf("First", "Second"), tracks.map { it.title })
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/playQueues", create.requestUrl!!.encodedPath)
        assertEquals(
            "server://machine-1/com.plexapp.plugins.library/library/sections/3/stations/1",
            create.requestUrl!!.queryParameter("uri"),
        )
        val window = server.takeRequest().requestUrl!!
        assertEquals("/playQueues/977", window.encodedPath)
        assertEquals("100", window.queryParameter("window"))
    }

    @Test
    fun `finds the library radios in the station hub`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Hub": [
                  {"hubIdentifier": "music.recent.played.3", "Metadata": [{"ratingKey": "9", "title": "AC/DC"}]},
                  {"hubIdentifier": "music.stations.3", "type": "station", "Metadata": [
                    {"key": "/library/sections/3/stations/1", "type": "playlist", "title": "Library Radio", "radio": "1"}]}
                ]}}""",
            ),
        )

        val stations = api.stations("c", ServerEndpoint(server.url("/").toString(), "t"), "3")

        assertEquals(listOf("Library Radio"), stations.map { it.title })
        assertEquals("/library/sections/3/stations/1", stations.single().key)
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("includeStations"))
    }

    @Test
    fun `asks for sonically similar tracks`() = runTest {
        server.enqueue(MockResponse().setBody("""{"MediaContainer": {"Metadata": [{"ratingKey": "7", "title": "Close"}]}}"""))

        val similar = api.sonicallySimilar("c", ServerEndpoint(server.url("/").toString(), "t"), "50155", limit = 50)

        assertEquals(listOf("Close"), similar.map { it.title })
        val url = server.takeRequest().requestUrl!!
        assertEquals("/library/metadata/50155/nearest", url.encodedPath)
        assertEquals("50", url.queryParameter("limit"))
        assertEquals("0.25", url.queryParameter("maxDistance"))
    }

    @Test
    fun `keeps only mix hubs as mixes`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"Hub": [
                  {"hubIdentifier": "music.stations.3", "Metadata": [{"key": "/library/sections/3/stations/1", "title": "Library Radio"}]},
                  {"hubIdentifier": "music.mixes.3", "Metadata": [
                    {"key": "/library/sections/3/mixes/1", "title": "Chill Mix"}, {"title": "No key"}]}
                ]}}""",
            ),
        )

        val mixes = api.mixes("c", ServerEndpoint(server.url("/").toString(), "t"), "3")

        assertEquals(listOf("Chill Mix"), mixes.map { it.title })
    }
}

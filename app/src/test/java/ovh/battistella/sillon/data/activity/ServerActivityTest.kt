package ovh.battistella.sillon.data.activity

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind

class ServerActivityTest {
    private val server = MockWebServer()
    private val device = PlexDevice("Sillon", "1.0.0", "15", "Pixel")
    private lateinit var plex: PlexApi
    private lateinit var jellyfin: JellyfinApi

    @Before
    fun setUp() {
        server.start()
        plex = PlexApi(OkHttpClient(), device, server.url("/"))
        jellyfin = JellyfinApi(OkHttpClient(), device)
    }

    @After
    fun tearDown() = server.shutdown()

    private val plexSessions = """
        {"MediaContainer": {"size": 3, "Metadata": [
          {"ratingKey": "101", "type": "track", "title": "Blue Train", "grandparentTitle": "John Coltrane",
           "parentTitle": "Blue Train", "parentThumb": "/library/metadata/100/thumb/1", "thumb": "/library/metadata/101/thumb/1",
           "duration": 600000, "viewOffset": 150000, "sessionKey": "7",
           "User": {"id": "4821", "title": "alice"},
           "Player": {"machineIdentifier": "phone-a", "title": "Pixel 8", "product": "Plexamp", "state": "playing"},
           "Session": {"id": "sess-a"}},
          {"ratingKey": "202", "type": "episode", "title": "Pilot", "grandparentTitle": "Severance", "parentTitle": "Season 1",
           "grandparentThumb": "/library/metadata/200/thumb/1", "duration": 3000000, "viewOffset": 0, "sessionKey": "8",
           "User": {"id": 9, "title": "ben"},
           "Player": {"machineIdentifier": "tv-b", "title": "Salon", "product": "Plex for Android (TV)", "state": "paused"},
           "Session": {"id": "sess-b"},
           "TranscodeSession": {"key": "/transcode/sessions/x"}},
          {"ratingKey": "303", "type": "track", "title": "So What", "grandparentTitle": "Miles Davis", "parentTitle": "Kind of Blue",
           "duration": 540000, "viewOffset": 1000, "sessionKey": "9",
           "User": {"id": "1", "title": "owner"},
           "Player": {"machineIdentifier": "client-1", "title": "Pixel", "product": "Sillon", "state": "buffering"},
           "Session": {"id": "sess-c"}}
        ]}}
    """.trimIndent()

    @Test
    fun `reads who plays what on a Plex server`() = runTest {
        server.enqueue(MockResponse().setBody(plexSessions))
        val endpoint = ServerEndpoint(server.url("/").toString(), "srv-token")

        val listens = ServerActivity.fromPlex(plex.sessions("client-1", endpoint), "client-1")

        val request = server.takeRequest()
        assertEquals("/status/sessions", request.requestUrl!!.encodedPath)
        assertEquals("srv-token", request.getHeader("X-Plex-Token"))
        assertEquals(listOf("alice", "ben", "owner"), listens.map { it.user })

        val alice = listens[0]
        assertEquals("sess-a", alice.id)
        assertEquals("4821@phone-a", alice.listenerKey)
        assertEquals("Pixel 8 · Plexamp", alice.device)
        assertEquals("Blue Train", alice.title)
        assertEquals("John Coltrane — Blue Train", alice.subtitle)
        assertEquals("/library/metadata/100/thumb/1", alice.artwork)
        assertEquals(ListenState.PLAYING, alice.state)
        assertEquals(ListenKind.MUSIC, alice.kind)
        assertEquals(0.25f, alice.progress, 0.001f)
        assertFalse(alice.transcoding)
        assertFalse(alice.mine)

        val ben = listens[1]
        assertEquals("9@tv-b", ben.listenerKey)
        assertEquals(ListenKind.VIDEO, ben.kind)
        assertEquals(ListenState.PAUSED, ben.state)
        assertEquals("Severance — Season 1", ben.subtitle)
        assertEquals("/library/metadata/200/thumb/1", ben.artwork)
        assertTrue(ben.transcoding)
        assertFalse(ben.mine)

        val owner = listens[2]
        assertTrue(owner.mine)
        assertEquals(ListenState.BUFFERING, owner.state)
        assertNull(owner.artwork)
    }

    @Test
    fun `an idle Plex server has no listener`() = runTest {
        server.enqueue(MockResponse().setBody("""{"MediaContainer": {"size": 0}}"""))
        val endpoint = ServerEndpoint(server.url("/").toString(), "srv-token")

        assertTrue(ServerActivity.fromPlex(plex.sessions("client-1", endpoint), "client-1").isEmpty())
    }

    @Test
    fun `reads who plays what on a Jellyfin server`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"Id": "s1", "UserId": "u-ana", "UserName": "Ana", "Client": "Finamp", "DeviceName": "iPhone", "DeviceId": "d1",
                   "PlayState": {"PositionTicks": 300000000, "IsPaused": false},
                   "NowPlayingItem": {"Id": "t1", "Name": "Naima", "Type": "Audio", "Album": "Giant Steps", "AlbumId": "al1",
                     "AlbumArtist": "John Coltrane", "Artists": ["John Coltrane"], "RunTimeTicks": 2400000000,
                     "AlbumPrimaryImageTag": "tag"}},
                  {"Id": "s2", "UserId": "u-me", "UserName": "Me", "Client": "Sillon", "DeviceName": "Pixel", "DeviceId": "dev-1",
                   "PlayState": {"IsPaused": true},
                   "NowPlayingItem": {"Id": "t2", "Name": "Alabama", "Type": "Audio", "RunTimeTicks": 3000000000}},
                  {"Id": "s3", "UserId": "u-idle", "UserName": "Idle", "Client": "Web", "DeviceName": "Firefox", "DeviceId": "d3"}
                ]
                """.trimIndent(),
            ),
        )
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "jf-token", ServerKind.JELLYFIN, "u-me")

        val listens = ServerActivity.fromJellyfin(jellyfin.sessions(endpoint, "dev-1"), endpoint.userId, "dev-1")

        assertEquals("/Sessions", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(listOf("Ana", "Me"), listens.map { it.user })
        val ana = listens[0]
        assertEquals("u-ana@d1", ana.listenerKey)
        assertEquals("iPhone · Finamp", ana.device)
        assertEquals("Naima", ana.title)
        assertEquals("John Coltrane — Giant Steps", ana.subtitle)
        assertEquals(30_000L, ana.positionMs)
        assertEquals(240_000L, ana.durationMs)
        assertEquals(ListenState.PLAYING, ana.state)
        assertFalse(ana.mine)
        assertTrue(listens[1].mine)
        assertEquals(ListenState.PAUSED, listens[1].state)
    }

    @Test
    fun `tells a Jellyfin administrator from another user`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Id": "u1", "Policy": {"IsAdministrator": true}}"""))
        server.enqueue(MockResponse().setBody("""{"Id": "u2", "Policy": {"IsAdministrator": false}}"""))
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "jf-token", ServerKind.JELLYFIN, "u1")

        assertTrue(jellyfin.isAdministrator(endpoint, "dev-1"))
        assertFalse(jellyfin.isAdministrator(endpoint, "dev-1"))
        assertEquals("/Users/Me", server.takeRequest().requestUrl!!.encodedPath)
    }
}

package ovh.battistella.sillon.data.home

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind

/** The recent-album requests against fake servers, with synthetic libraries. */
class SearchHomeApiTest {
    private val server = MockWebServer()
    private val device = PlexDevice("Sillon", "1.0.0", "15", "Pixel")

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `Plex recent albums come from the section, sorted by last play`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"size": 2, "Metadata": [
                  {"ratingKey": "11", "type": "album", "title": "Blue Hour", "parentTitle": "Demo Band", "thumb": "/library/metadata/11/thumb/1", "lastViewedAt": 1761000000, "viewCount": 4},
                  {"ratingKey": "12", "type": "album", "title": "Unheard", "parentTitle": "Demo Band"}
                ]}}""",
            ),
        )
        val api = PlexApi(OkHttpClient(), device, server.url("/"))
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")

        val albums = api.sectionItems("client", endpoint, "3", SearchHome.plexRecentQuery()).metadata
        val recent = SearchHome.plexRecent(albums)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/library/sections/3/all", url.encodedPath)
        assertEquals("9", url.queryParameter("type"))
        assertEquals("lastViewedAt:desc", url.queryParameter("sort"))
        assertEquals(listOf("11"), recent.map { it.key })
        assertEquals("Blue Hour", recent.single().title)
        assertEquals(1_761_000_000_000L, recent.single().playedAt)
    }

    @Test
    fun `Jellyfin recent albums come from the recent tracks, grouped by album`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"TotalRecordCount": 0, "Items": [
                  {"Id": "t1", "Name": "Morning", "Type": "Audio", "Album": "Blue Hour", "AlbumId": "al1", "AlbumArtist": "Demo Band", "AlbumPrimaryImageTag": "x", "UserData": {"PlayCount": 3, "LastPlayedDate": "2026-10-22T10:00:00.0000000Z"}},
                  {"Id": "t2", "Name": "Noon", "Type": "Audio", "Album": "Blue Hour", "AlbumId": "al1", "AlbumArtist": "Demo Band", "UserData": {"PlayCount": 1, "LastPlayedDate": "2026-10-22T09:00:00Z"}},
                  {"Id": "t3", "Name": "Dusk", "Type": "Audio", "Album": "Second Wind", "AlbumId": "al2", "AlbumArtist": "Other Band", "UserData": {"PlayCount": 1, "LastPlayedDate": "2026-10-21T09:00:00Z"}}
                ]}""",
            ),
        )
        val api = JellyfinApi(OkHttpClient(), device)
        val endpoint = ServerEndpoint(server.url("/jf").toString().trimEnd('/'), "tok", ServerKind.JELLYFIN, "user-1")

        val tracks = api.itemsPage(endpoint, "device", SearchHome.jellyfinRecentQuery("lib")).first
        val recent = SearchHome.recentFromTracks(tracks)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/jf/Items", url.encodedPath)
        assertEquals("user-1", url.queryParameter("userId"))
        assertEquals("DatePlayed", url.queryParameter("sortBy"))
        assertEquals("IsPlayed", url.queryParameter("filters"))
        assertEquals(listOf("al1", "al2"), recent.map { it.key })
        assertEquals("Blue Hour", recent[0].title)
        assertEquals("Demo Band", recent[0].artist)
        assertEquals("jellyfin-item:al1", recent[0].thumb)
        assertEquals(java.time.Instant.parse("2026-10-22T10:00:00Z").toEpochMilli(), recent[0].playedAt)
    }
}

package ovh.battistella.sillon.data.genres

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

/** The genre requests against fake servers, with synthetic libraries. */
class GenreApiTest {
    private val server = MockWebServer()
    private val device = PlexDevice("Sillon", "1.0.0", "15", "Pixel")

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `Plex albums come with their Genre tags, a page at a time`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer": {"size": 2, "totalSize": 2, "Metadata": [
                  {"ratingKey": "11", "type": "album", "title": "Blue Hour", "parentTitle": "Demo Band", "parentRatingKey": "5",
                   "thumb": "/library/metadata/11/thumb/1", "parentThumb": "/library/metadata/5/thumb/1",
                   "Genre": [{"id": 101, "filter": "genre=101", "tag": "Heavy-Metal"}, {"tag": "Rock"}]},
                  {"ratingKey": "12", "type": "album", "title": "No Tags", "parentTitle": "Demo Band", "parentRatingKey": "5"}
                ]}}""",
            ),
        )
        val api = PlexApi(OkHttpClient(), device, server.url("/"))
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")

        val albums = api.sectionItems("client", endpoint, "3", Genres.plexAlbumsQuery(start = 1000)).metadata.map(Genres::fromPlex)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/library/sections/3/all", url.encodedPath)
        assertEquals("9", url.queryParameter("type"))
        assertEquals("1000", url.queryParameter("X-Plex-Container-Start"))
        assertEquals(Genres.PAGE_SIZE.toString(), url.queryParameter("X-Plex-Container-Size"))
        assertEquals(listOf("Heavy-Metal", "Rock"), albums[0].genres)
        assertEquals("5", albums[0].artistKey)
        assertEquals("/library/metadata/5/thumb/1", albums[0].artistThumb)
        assertEquals(emptyList<String>(), albums[1].genres)
    }

    @Test
    fun `Jellyfin albums ask for Genres and keep the album artist`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"TotalRecordCount": 2, "Items": [
                  {"Id": "al1", "Name": "Blue Hour", "Type": "MusicAlbum", "AlbumArtist": "Demo Band",
                   "AlbumArtists": [{"Name": "Demo Band", "Id": "ar1"}], "Genres": ["Métal", "Rock"],
                   "GenreItems": [{"Name": "Métal", "Id": "g1"}], "ImageTags": {"Primary": "x"}},
                  {"Id": "al2", "Name": "Plain", "Type": "MusicAlbum"}
                ]}""",
            ),
        )
        val api = JellyfinApi(OkHttpClient(), device)
        val endpoint = ServerEndpoint(server.url("/jf").toString().trimEnd('/'), "tok", ServerKind.JELLYFIN, "user-1")

        val albums = api.rawPage(endpoint, "device", Genres.jellyfinAlbumsQuery("lib", start = 0)).items.map(Genres::fromJellyfin)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/jf/Items", url.encodedPath)
        assertEquals("user-1", url.queryParameter("userId"))
        assertEquals("lib", url.queryParameter("parentId"))
        assertEquals("MusicAlbum", url.queryParameter("includeItemTypes"))
        assertEquals("Genres", url.queryParameter("fields"))
        assertEquals("0", url.queryParameter("startIndex"))
        assertEquals(listOf("Métal", "Rock"), albums[0].genres)
        assertEquals("ar1", albums[0].artistKey)
        assertEquals("Demo Band", albums[0].artist)
        assertEquals("jellyfin-item:al1", albums[0].thumb)
        assertEquals("jellyfin-item:ar1", albums[0].artistThumb)
        assertEquals(null, albums[1].thumb)
        assertEquals(emptyList<String>(), albums[1].genres)
    }
}

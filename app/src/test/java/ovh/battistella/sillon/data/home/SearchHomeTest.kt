package ovh.battistella.sillon.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.wear.AlbumPlays

class SearchHomeTest {
    private fun track(key: String, album: String?, playedAt: Long? = null) = PlexMetadata(
        ratingKey = key,
        type = "track",
        title = "Track $key",
        parentRatingKey = album,
        parentTitle = album?.let { "Album $it" },
        grandparentTitle = "Artist",
        parentThumb = album?.let { "jellyfin-item:$it" },
        lastViewedAt = playedAt,
    )

    @Test
    fun `Plex asks for albums sorted by their last play`() {
        val query = SearchHome.plexRecentQuery(size = 40).toMap()
        assertEquals("9", query["type"])
        assertEquals("lastViewedAt:desc", query["sort"])
        assertEquals("40", query["X-Plex-Container-Size"])
    }

    @Test
    fun `Jellyfin asks for played tracks sorted by play date in the library`() {
        val query = SearchHome.jellyfinRecentQuery("lib-1", limit = 200).toMap()
        assertEquals("lib-1", query["parentId"])
        assertEquals("Audio", query["includeItemTypes"])
        assertEquals("IsPlayed", query["filters"])
        assertEquals("DatePlayed", query["sortBy"])
        assertEquals("Descending", query["sortOrder"])
        assertEquals("200", query["limit"])
    }

    @Test
    fun `Plex albums never played are left out`() {
        val albums = listOf(
            PlexMetadata(ratingKey = "a1", title = "One", parentTitle = "Artist", thumb = "/t1", lastViewedAt = 1_700_000_000),
            PlexMetadata(ratingKey = "a2", title = "Two", lastViewedAt = null),
            PlexMetadata(ratingKey = "a3", title = "Three", lastViewedAt = 0),
        )
        val recent = SearchHome.plexRecent(albums)
        assertEquals(listOf("a1"), recent.map { it.key })
        assertEquals(1_700_000_000_000L, recent.single().playedAt)
        assertEquals("/t1", recent.single().thumb)
        assertEquals("Artist", recent.single().artist)
    }

    @Test
    fun `recent tracks fold into their albums, in the order of their latest track`() {
        val tracks = listOf(
            track("t1", "b", 300),
            track("t2", "a", 200),
            track("t3", "b", 100),
            track("t4", null, 90),
            track("t5", "c", 50),
            track("t6", "a", 10),
        )
        val recent = SearchHome.recentFromTracks(tracks)
        assertEquals(listOf("b", "a", "c"), recent.map { it.key })
        assertEquals("Album b", recent.first().title)
        assertEquals("jellyfin-item:b", recent.first().thumb)
        assertEquals(300_000L, recent.first().playedAt)
    }

    @Test
    fun `Sillon's own plays fill in what the server missed`() {
        val server = listOf(HomeAlbum("a", "A", thumb = "/a", playedAt = 2_000), HomeAlbum("b", "B", playedAt = 1_000))
        val local = listOf(HomeAlbum("c", "C", playedAt = 3_000), HomeAlbum("b", "B (local)", playedAt = 2_500))
        val merged = SearchHome.mergeRecent(server, local, sealed = emptySet())
        assertEquals(listOf("c", "b", "a"), merged.map { it.key })
        // The server's title and cover win, the latest play date wins.
        assertEquals("B", merged[1].title)
        assertEquals(2_500L, merged[1].playedAt)
    }

    @Test
    fun `an unrevealed mystery album never shows in Recently played`() {
        val server = listOf(HomeAlbum("mystery", "Secret", playedAt = 9_000), HomeAlbum("a", "A", playedAt = 1_000))
        val local = listOf(HomeAlbum("mystery", "Secret", playedAt = 9_500))
        val merged = SearchHome.mergeRecent(server, local, sealed = setOf("mystery"))
        assertEquals(listOf("a"), merged.map { it.key })
    }

    @Test
    fun `an unrevealed mystery album never shows in Your classics either`() {
        val cached = CachedHome(
            owner = "o",
            recent = listOf(HomeAlbum("a", "A", playedAt = 1)),
            classics = listOf(HomeAlbum("mystery", "Secret"), HomeAlbum("b", "B")),
        )
        val (recent, classics) = SearchHome.rows(cached, hidden = setOf("mystery"))
        assertEquals(listOf("a"), recent.map { it.key })
        assertEquals(listOf("b"), classics.map { it.key })
    }

    @Test
    fun `merged rows stop at the row size`() {
        val server = (1..30).map { HomeAlbum("a$it", "A$it", playedAt = 100L - it) }
        assertEquals(SearchHome.ROW_SIZE, SearchHome.mergeRecent(server, emptyList(), emptySet()).size)
    }

    @Test
    fun `a mystery playing unrevealed is sealed, and the reveal unseals it`() {
        val sealed = SearchHome.seal(emptySet(), mysteryAlbum = "m", revealed = false)
        assertEquals(setOf("m"), sealed)
        assertEquals(emptySet<String>(), SearchHome.seal(sealed, mysteryAlbum = "m", revealed = true))
    }

    @Test
    fun `a mystery left before the reveal stays sealed until played in the open`() {
        val left = SearchHome.seal(setOf("m"), mysteryAlbum = null, revealed = false)
        assertEquals(setOf("m"), left)
        assertEquals(emptySet<String>(), SearchHome.seal(left, mysteryAlbum = null, revealed = false, playedOpenly = "m"))
    }

    @Test
    fun `the unrevealed mystery itself never counts as played in the open`() {
        assertEquals(setOf("m"), SearchHome.seal(emptySet(), mysteryAlbum = "m", revealed = false, playedOpenly = "m"))
    }

    @Test
    fun `classics are the albums with the most plays`() {
        val plays = mapOf(
            "a" to AlbumPlays(plays = 5, tracks = 10),
            "b" to AlbumPlays(plays = 40, tracks = 20),
            "c" to AlbumPlays(plays = 0, tracks = 8),
            "d" to AlbumPlays(plays = 5, tracks = 1),
        )
        assertEquals(listOf("b", "a", "d"), SearchHome.classics(plays))
        assertEquals(listOf("b"), SearchHome.classics(plays, limit = 1))
    }

    @Test
    fun `known albums come back in the order of the keys`() {
        val known = listOf(HomeAlbum("a", "A"), HomeAlbum("b", "B"))
        assertEquals(listOf("b", "a"), SearchHome.ordered(listOf("b", "x", "a"), known).map { it.key })
    }

    @Test
    fun `a new local play moves to the front without duplicates`() {
        val local = listOf(HomeAlbum("a", "A"), HomeAlbum("b", "B"))
        assertEquals(listOf("b", "a"), SearchHome.record(local, HomeAlbum("b", "B")).map { it.key })
        assertEquals(2, SearchHome.record(local, HomeAlbum("c", "C"), limit = 2).size)
    }

    @Test
    fun `offline only the albums on the phone remain`() {
        val albums = listOf(HomeAlbum("a", "A"), HomeAlbum("b", "B"), HomeAlbum("c", "C"))
        assertEquals(listOf("b"), SearchHome.offline(albums, setOf("b", "z")).map { it.key })
        assertTrue(SearchHome.offline(albums, emptySet()).isEmpty())
    }

    @Test
    fun `covers of albums known by key only are built on the key`() {
        assertEquals("/library/metadata/42/thumb", SearchHome.thumbOf(ServerKind.PLEX, "42"))
        assertEquals("jellyfin-item:abc", SearchHome.thumbOf(ServerKind.JELLYFIN, "abc"))
    }
}

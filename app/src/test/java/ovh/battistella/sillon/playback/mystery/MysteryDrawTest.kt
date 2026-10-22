package ovh.battistella.sillon.playback.mystery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.JellyfinItem
import ovh.battistella.sillon.data.jellyfin.JellyfinUserData
import ovh.battistella.sillon.data.jellyfin.toMetadata
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.PlexResponse
import kotlin.random.Random

class MysteryDrawTest {
    private fun album(key: String, tracks: Int? = 10, plays: Int = 0) = AlbumCandidate(key, tracks, plays)

    private fun draws(albums: List<AlbumCandidate>, anyAlbum: Boolean, n: Int = 10_000): Map<String, Int> {
        val random = Random(42)
        return (1..n).mapNotNull { MysteryDraw.pick(albums, anyAlbum, random)?.key }.groupingBy { it }.eachCount()
    }

    @Test
    fun `an empty library draws nothing`() {
        assertNull(MysteryDraw.pick(emptyList(), anyAlbum = false))
        assertNull(MysteryDraw.pick(emptyList(), anyAlbum = true))
    }

    @Test
    fun `albums of fewer than 4 tracks are never drawn`() {
        val counts = draws(listOf(album("ep", tracks = 3), album("single", tracks = 1), album("lp", tracks = 4)), anyAlbum = true)
        assertEquals(mapOf("lp" to 10_000), counts)
    }

    @Test
    fun `a library of short albums only draws nothing`() {
        assertNull(MysteryDraw.pick(listOf(album("ep", tracks = 3), album("single", tracks = 2)), anyAlbum = true))
    }

    @Test
    fun `an album without a track count is kept, the tracks are checked after the draw`() {
        assertEquals("unknown", MysteryDraw.pick(listOf(album("unknown", tracks = null)), anyAlbum = false)?.key)
    }

    @Test
    fun `never played albums are strongly preferred`() {
        val counts = draws(listOf(album("fresh", plays = 0), album("worn", plays = 9)), anyAlbum = false)
        assertTrue("fresh drawn ${counts["fresh"]} times", counts.getValue("fresh") > 9_800)
    }

    @Test
    fun `weight falls with the square of the play count`() {
        assertEquals(1.0, MysteryDraw.weight(album("a", plays = 0), anyAlbum = false), 1e-9)
        assertEquals(0.25, MysteryDraw.weight(album("a", plays = 1), anyAlbum = false), 1e-9)
        assertEquals(0.01, MysteryDraw.weight(album("a", plays = 9), anyAlbum = false), 1e-9)
        assertEquals(1.0, MysteryDraw.weight(album("a", plays = 9), anyAlbum = true), 1e-9)
    }

    @Test
    fun `any album draws evenly whatever the play count`() {
        val counts = draws(listOf(album("fresh", plays = 0), album("worn", plays = 50)), anyAlbum = true)
        assertEquals(5_000.0, counts.getValue("fresh").toDouble(), 300.0)
    }

    @Test
    fun `another surprise skips the album just drawn when there is a choice`() {
        val albums = listOf(album("a"), album("b"))
        repeat(200) { assertEquals("b", MysteryDraw.pick(albums, anyAlbum = true, Random(it), exclude = setOf("a"))?.key) }
        assertEquals("a", MysteryDraw.pick(listOf(album("a")), anyAlbum = true, exclude = setOf("a"))?.key)
    }

    @Test
    fun `reads the play count of Plex and Jellyfin albums`() {
        val plex = PlexJson.decodeFromString(
            PlexResponse.serializer(),
            """{"MediaContainer": {"Metadata": [{"ratingKey": "7", "type": "album", "leafCount": 12, "viewCount": 3}, {"ratingKey": "8", "leafCount": 9}]}}""",
        ).container.metadata.map { it.toAlbumCandidate() }
        assertEquals(listOf(AlbumCandidate("7", 12, 3), AlbumCandidate("8", 9, 0)), plex)

        val jellyfin = JellyfinItem(id = "j1", type = "MusicAlbum", childCount = 11, userData = JellyfinUserData(playCount = 5))
        assertEquals(AlbumCandidate("j1", 11, 5), jellyfin.toMetadata().toAlbumCandidate())
    }
}

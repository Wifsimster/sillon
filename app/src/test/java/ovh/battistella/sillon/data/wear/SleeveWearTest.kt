package ovh.battistella.sillon.data.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexMetadata

class SleeveWearTest {
    @Test
    fun `measure is the average number of plays per track`() {
        assertEquals(2.5, SleeveWear.measure(AlbumPlays(plays = 25, tracks = 10)), 1e-9)
        assertEquals(0.0, SleeveWear.measure(AlbumPlays(plays = 0, tracks = 10)), 1e-9)
    }

    @Test
    fun `measure does not favour long albums`() {
        val ep = SleeveWear.measure(AlbumPlays(plays = 20, tracks = 4))
        val boxSet = SleeveWear.measure(AlbumPlays(plays = 40, tracks = 40))
        assertTrue(ep > boxSet)
    }

    @Test
    fun `measure falls back to the raw count when the track count is unknown`() {
        assertEquals(7.0, SleeveWear.measure(AlbumPlays(plays = 7, tracks = 0)), 1e-9)
    }

    @Test
    fun `an empty library has no levels`() {
        assertEquals(emptyMap<String, Int>(), SleeveWear.levels(emptyMap()))
    }

    @Test
    fun `a library never played stays sealed`() {
        val levels = SleeveWear.levels(mapOf("a" to AlbumPlays(0, 10), "b" to AlbumPlays(0, 3)))
        assertEquals(mapOf("a" to SleeveWear.SEALED, "b" to SleeveWear.SEALED), levels)
    }

    @Test
    fun `a single album played sits in the middle`() {
        assertEquals(mapOf("a" to SleeveWear.NO_SPREAD), SleeveWear.levels(mapOf("a" to AlbumPlays(12, 10))))
    }

    @Test
    fun `a single album never played is sealed`() {
        assertEquals(mapOf("a" to SleeveWear.SEALED), SleeveWear.levels(mapOf("a" to AlbumPlays(0, 10))))
    }

    @Test
    fun `played albums with the same measure share the middle level`() {
        val levels = SleeveWear.levels(
            mapOf("a" to AlbumPlays(10, 10), "b" to AlbumPlays(5, 5), "c" to AlbumPlays(0, 8)),
        )
        assertEquals(mapOf("a" to 2, "b" to 2, "c" to SleeveWear.SEALED), levels)
    }

    @Test
    fun `played albums spread over the four levels by quartile`() {
        val library = (1..8).associate { "a$it" to AlbumPlays(plays = it, tracks = 1) }
        val levels = SleeveWear.levels(library)
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), (1..8).map { levels.getValue("a$it") })
    }

    @Test
    fun `unplayed albums do not push played ones up the scale`() {
        val played = (1..4).associate { "p$it" to AlbumPlays(plays = it, tracks = 1) }
        val unplayed = (1..50).associate { "u$it" to AlbumPlays(plays = 0, tracks = 10) }
        val levels = SleeveWear.levels(played + unplayed)
        assertEquals(listOf(1, 2, 3, 4), (1..4).map { levels.getValue("p$it") })
        assertTrue(unplayed.keys.all { levels[it] == SleeveWear.SEALED })
    }

    @Test
    fun `a light and a heavy listener both see the whole range`() {
        val light = (1..4).associate { "a$it" to AlbumPlays(plays = it, tracks = 10) }
        val heavy = (1..4).associate { "a$it" to AlbumPlays(plays = it * 500, tracks = 10) }
        assertEquals(SleeveWear.levels(light), SleeveWear.levels(heavy))
        assertEquals(setOf(1, 2, 3, 4), SleeveWear.levels(light).values.toSet())
    }

    @Test
    fun `a plateau of ties and one favourite`() {
        val library = (1..7).associate { "a$it" to AlbumPlays(1, 1) } + ("fav" to AlbumPlays(9, 1))
        val levels = SleeveWear.levels(library)
        assertEquals(4, levels["fav"])
        assertTrue((1..7).all { levels["a$it"] == 2 })
    }

    @Test
    fun `percentile is undefined without spread`() {
        assertNull(SleeveWear.percentile(emptyList(), 1.0))
        assertNull(SleeveWear.percentile(listOf(3.0), 3.0))
        assertNull(SleeveWear.percentile(listOf(3.0, 3.0), 3.0))
        assertEquals(0.0, SleeveWear.percentile(listOf(1.0, 2.0, 3.0), 1.0)!!, 1e-9)
        assertEquals(1.0, SleeveWear.percentile(listOf(1.0, 2.0, 3.0), 3.0)!!, 1e-9)
    }

    @Test
    fun `levels stay within bounds`() {
        assertEquals(1, SleeveWear.levelOf(0.0))
        assertEquals(4, SleeveWear.levelOf(1.0))
        assertEquals(SleeveWear.NO_SPREAD, SleeveWear.levelOf(null))
    }

    @Test
    fun `Plex albums carry their plays and track count`() {
        val albums = listOf(
            PlexMetadata(ratingKey = "1", type = "album", viewCount = 30, leafCount = 12),
            PlexMetadata(ratingKey = "2", type = "album"),
            PlexMetadata(ratingKey = "", type = "album", viewCount = 4),
        )
        assertEquals(mapOf("1" to AlbumPlays(30, 12), "2" to AlbumPlays(0, 0)), plexAlbumPlays(albums))
    }

    @Test
    fun `Jellyfin plays are summed from the played tracks of each album`() {
        val albums = listOf(
            PlexMetadata(ratingKey = "a1", type = "album", leafCount = 10),
            PlexMetadata(ratingKey = "a2", type = "album", leafCount = 3),
        )
        val played = listOf(
            PlexMetadata(ratingKey = "t1", type = "track", parentRatingKey = "a1", viewCount = 4),
            PlexMetadata(ratingKey = "t2", type = "track", parentRatingKey = "a1", viewCount = 6),
            PlexMetadata(ratingKey = "t3", type = "track", parentRatingKey = "elsewhere", viewCount = 9),
            PlexMetadata(ratingKey = "t4", type = "track", parentRatingKey = null, viewCount = 2),
        )
        assertEquals(
            mapOf("a1" to AlbumPlays(10, 10), "a2" to AlbumPlays(0, 3)),
            jellyfinAlbumPlays(albums, played),
        )
    }
}

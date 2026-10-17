package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseIdsTest {

    @Test
    fun `every node survives a round trip through its id`() {
        val nodes = listOf(
            BrowseNode.Root,
            BrowseNode.Artists,
            BrowseNode.Albums,
            BrowseNode.Playlists,
            BrowseNode.Artist("12"),
            BrowseNode.Album("34"),
            BrowseNode.Playlist("56"),
            BrowseNode.Track(BrowseNode.Album("34"), "101"),
            BrowseNode.Track(BrowseNode.Playlist("56"), "102"),
            BrowseNode.FiveStars,
            BrowseNode.Track(BrowseNode.FiveStars, "103"),
            BrowseNode.Radios,
            BrowseNode.Station("/library/metadata/35167/station/96f069ae-8d3d-4430-82f7-26621535e9d2?type=10"),
            BrowseNode.Station("jellyfin:library-shuffle"),
        )
        nodes.forEach { assertEquals(it, BrowseIds.parse(BrowseIds.of(it))) }
    }

    @Test
    fun `track ids keep the album they were listed in`() {
        assertEquals("track/album/34/101", BrowseIds.of(BrowseNode.Track(BrowseNode.Album("34"), "101")))
    }

    @Test
    fun `bare rating keys and malformed ids are not browse nodes`() {
        assertNull(BrowseIds.parse("101"))
        assertNull(BrowseIds.parse("artist/"))
        assertNull(BrowseIds.parse("track/artist/12/101"))
        assertNull(BrowseIds.parse("track/album/34"))
        assertNull(BrowseIds.parse("genre/rock"))
    }
}

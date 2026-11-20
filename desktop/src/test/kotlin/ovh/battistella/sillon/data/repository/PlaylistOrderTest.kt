package ovh.battistella.sillon.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexMetadata

class PlaylistOrderTest {

    private fun playlist(key: String, lastViewedAt: Long? = null) =
        PlexMetadata(ratingKey = key, type = "playlist", lastViewedAt = lastViewedAt)

    @Test
    fun `puts the most recently played playlists first and keeps never-played ones in server order`() {
        val ordered = listOf(
            playlist("all-music"),
            playlist("fresh", lastViewedAt = 100),
            playlist("chill"),
            playlist("gym", lastViewedAt = 300),
            playlist("focus", lastViewedAt = 200),
        ).byLatestListen()

        assertEquals(listOf("gym", "focus", "fresh", "all-music", "chill"), ordered.map { it.ratingKey })
    }
}

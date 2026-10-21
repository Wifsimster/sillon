package ovh.battistella.sillon.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexMetadata
import kotlin.random.Random

class NeverPlayedTest {

    private fun track(key: String, plays: Int? = null, album: String = "a1") =
        PlexMetadata(ratingKey = key, type = "track", viewCount = plays, parentRatingKey = album)

    private fun download(key: String, album: String?, track: Int = 1) = DownloadEntity(
        serverId = "srv", ratingKey = key, title = key, artist = null, album = null, albumKey = album,
        albumArtist = null, trackNumber = track, discNumber = 1, durationMs = null, partKey = "/p/$key",
        thumb = null, state = DownloadState.DONE,
    )

    @Test
    fun `asks Plex for random never-played tracks one page at a time`() {
        val query = NeverPlayedQueries.plexTracks().toMap()
        assertEquals("10", query["type"])
        assertEquals("0", query["viewCount"])
        assertEquals("random", query["sort"])
        assertEquals("0", query["X-Plex-Container-Start"])
        assertEquals(NeverPlayedQueries.TRACK_BATCH.toString(), query["X-Plex-Container-Size"])
    }

    @Test
    fun `counts on Plex with a one-item page and checks the filter with the most played first`() {
        val never = NeverPlayedQueries.plexTrackCount(neverPlayedOnly = true).toMap()
        assertEquals("0", never["viewCount"])
        assertEquals("viewCount:desc", never["sort"])
        assertEquals("1", never["X-Plex-Container-Size"])
        val all = NeverPlayedQueries.plexTrackCount(neverPlayedOnly = false).toMap()
        assertEquals("10", all["type"])
        assertFalse("viewCount" in all)
        assertFalse("sort" in all)
    }

    @Test
    fun `asks Plex for random albums`() {
        val query = NeverPlayedQueries.plexAlbums().toMap()
        assertEquals("9", query["type"])
        assertEquals("random", query["sort"])
        assertEquals(NeverPlayedQueries.ALBUM_CANDIDATES.toString(), query["X-Plex-Container-Size"])
    }

    @Test
    fun `asks Jellyfin for random unplayed audio in the library`() {
        val query = NeverPlayedQueries.jellyfinTracks("lib-1").toMap()
        assertEquals("lib-1", query["parentId"])
        assertEquals("Audio", query["includeItemTypes"])
        assertEquals("true", query["recursive"])
        assertEquals("false", query["isPlayed"])
        assertEquals("Random", query["sortBy"])
        assertEquals(NeverPlayedQueries.TRACK_BATCH.toString(), query["limit"])
    }

    @Test
    fun `counts on Jellyfin without downloading any item`() {
        val never = NeverPlayedQueries.jellyfinTrackCount("lib-1", neverPlayedOnly = true).toMap()
        assertEquals("0", never["limit"])
        assertEquals("true", never["enableTotalRecordCount"])
        assertEquals("false", never["isPlayed"])
        assertFalse("isPlayed" in NeverPlayedQueries.jellyfinTrackCount("lib-1", neverPlayedOnly = false).toMap())
    }

    @Test
    fun `asks Jellyfin for random albums not fully played`() {
        val query = NeverPlayedQueries.jellyfinAlbums("lib-1").toMap()
        assertEquals("MusicAlbum", query["includeItemTypes"])
        assertEquals("false", query["isPlayed"])
        assertEquals("Random", query["sortBy"])
    }

    @Test
    fun `computes the share of the library never played`() {
        assertEquals(61, neverPlayedPercent(30_500, 50_000))
        assertEquals(0, neverPlayedPercent(0, 50_000))
        assertEquals(100, neverPlayedPercent(50_000, 50_000))
        assertEquals(1, neverPlayedPercent(1, 50_000))
        assertEquals(99, neverPlayedPercent(49_999, 50_000))
        assertEquals(100, neverPlayedPercent(60_000, 50_000))
        assertNull(neverPlayedPercent(3, 0))
    }

    @Test
    fun `keeps only never-played tracks and never queues one twice`() {
        val session = NeverPlayedSession()
        val first = session.take(listOf(track("1"), track("2", plays = 3), track("3", plays = 0), track("1")))
        assertEquals(listOf("1", "3"), first.map { it.ratingKey })
        val second = session.take(listOf(track("1"), track("3"), track("4")))
        assertEquals(listOf("4"), second.map { it.ratingKey })
    }

    @Test
    fun `a track played in the session does not come back after a restart`() {
        val session = NeverPlayedSession()
        session.take(listOf(track("1"), track("2")))
        assertEquals(NeverPlayedSession.Outcome.PLAYED, session.leave("1", listenedMs = 200_000, skipped = false))
        session.begin()
        assertEquals(listOf("2"), session.take(listOf(track("1"), track("2"))).map { it.ratingKey })
    }

    @Test
    fun `a skip before 30 seconds is not for me, after it counts as played`() {
        val session = NeverPlayedSession()
        session.take(listOf(track("1"), track("2"), track("3")))
        assertEquals(NeverPlayedSession.Outcome.NOT_FOR_ME, session.leave("1", listenedMs = 12_000, skipped = true))
        assertEquals(NeverPlayedSession.Outcome.PLAYED, session.leave("2", listenedMs = 30_000, skipped = true))
        assertEquals(NeverPlayedSession.Outcome.PLAYED, session.leave("3", listenedMs = 5_000, skipped = false))
        assertTrue(session.isNotForMe("1"))
        assertFalse(session.isNotForMe("2"))
        assertTrue(session.hasPlayed("2"))
        session.begin()
        assertTrue(session.take(listOf(track("1"), track("2"), track("3"))).isEmpty())
    }

    @Test
    fun `not for me lasts only for the session`() {
        val session = NeverPlayedSession()
        session.take(listOf(track("1")))
        session.leave("1", listenedMs = 1_000, skipped = true)
        session.reset()
        assertFalse(session.isNotForMe("1"))
        assertEquals(listOf("1"), session.take(listOf(track("1"))).map { it.ratingKey })
    }

    @Test
    fun `ignores tracks that are not from the radio`() {
        val session = NeverPlayedSession()
        assertEquals(NeverPlayedSession.Outcome.IGNORED, session.leave("x", listenedMs = 1_000, skipped = true))
        assertFalse(session.isNotForMe("x"))
    }

    @Test
    fun `draws an album only when none of its tracks was ever played`() {
        val session = NeverPlayedSession()
        assertNull(session.takeAlbum("a1", listOf(track("1"), track("2", plays = 1))))
        assertNull(session.takeAlbum("a2", emptyList()))
        assertEquals(2, session.takeAlbum("a3", listOf(track("3", album = "a3"), track("4", album = "a3")))?.size)
        assertTrue(session.isAlbumQueued("a3"))
        assertNull(session.takeAlbum("a3", listOf(track("3", album = "a3"))))
        assertTrue(session.owns("4"))
    }

    @Test
    fun `offline, plays only downloaded tracks the cached history says are never played`() {
        val session = NeverPlayedSession()
        val downloaded = listOf(download("1", "a"), download("2", "a"), download("3", "b"))
        val picked = pickOffline(downloaded, neverPlayed = setOf("1", "3", "9"), session, NeverPlayedMode.TRACKS, Random(1))
        assertEquals(setOf("1", "3"), picked.map { it.ratingKey }.toSet())
        assertTrue(pickOffline(downloaded, setOf("1", "3"), session, NeverPlayedMode.TRACKS, Random(1)).isEmpty())
    }

    @Test
    fun `offline, draws a downloaded album whose tracks are all never played`() {
        val session = NeverPlayedSession()
        val downloaded = listOf(download("1", "a", 2), download("2", "a", 1), download("3", "b"))
        val picked = pickOffline(downloaded, neverPlayed = setOf("1", "2"), session, NeverPlayedMode.ALBUMS, Random(1))
        assertEquals(listOf("2", "1"), picked.map { it.ratingKey })
        assertTrue(pickOffline(downloaded, setOf("1", "2"), session, NeverPlayedMode.ALBUMS, Random(1)).isEmpty())
    }

    @Test
    fun `maps station keys to the two modes`() {
        assertEquals(NeverPlayedMode.TRACKS, neverPlayedModeOf(NEVER_PLAYED_STATION))
        assertEquals(NeverPlayedMode.ALBUMS, neverPlayedModeOf(NEVER_PLAYED_ALBUMS_STATION))
        assertNull(neverPlayedModeOf("/library/sections/1/stations/1"))
        assertEquals(NEVER_PLAYED_ALBUMS_STATION, NeverPlayedMode.ALBUMS.stationKey())
    }
}

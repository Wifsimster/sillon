package ovh.battistella.sillon.data.rating

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.MediaContainer
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore

/**
 * Every queue drawn for you leaves out the 1-star tracks: the ones the server rated 1 star, and
 * the ones rated here (a rating waiting to be sent, or a download, which carries no rating).
 */
@RunWith(RobolectricTestRunner::class)
class OneStarExclusionTest {
    private val book = RatingBook().rate("mine", 1).rate("rescued", 4)
    private val ratings = mockk<RatingStore>(relaxed = true) {
        coEvery { book(any()) } returns book
    }

    private fun tracks() = listOf(
        PlexMetadata(ratingKey = "a", type = "track"),
        PlexMetadata(ratingKey = "server", type = "track", userRating = 2f),
        PlexMetadata(ratingKey = "mine", type = "track"),
        PlexMetadata(ratingKey = "rescued", type = "track", userRating = 2f),
        PlexMetadata(ratingKey = "liked", type = "track", userRating = 10f),
    )

    private val kept = listOf("a", "rescued", "liked")

    private fun repository(kind: ServerKind, plex: PlexApi = mockk(relaxed = true), jellyfin: JellyfinApi = mockk(relaxed = true)): PlexRepository {
        val server = SelectedServer("m1", "Home", ServerEndpoint("https://server", "token", kind, "user-1"))
        val sessionStore = mockk<SessionStore>(relaxed = true) {
            every { session } returns flowOf(Session.Ready("user-token", server))
            every { musicSectionKey } returns flowOf("1")
            coEvery { clientId() } returns "client"
        }
        return PlexRepository(
            plex, sessionStore, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            jellyfin, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ratings,
        )
    }

    private val section = listOf(PlexDirectory("1", "Music", PlexDirectory.TYPE_MUSIC_SECTION))

    private fun plexApi() = mockk<PlexApi>(relaxed = true) {
        coEvery { sections(any(), any()) } returns section
        coEvery { sectionItems(any(), any(), any(), any()) } returns MediaContainer(metadata = tracks())
        coEvery { stationTracks(any(), any(), any(), any(), any()) } returns tracks()
        coEvery { sonicallySimilar(any(), any(), any(), any()) } returns tracks()
        coEvery { artistStation(any(), any(), any()) } returns "/library/metadata/9/station/x"
    }

    private fun jellyfinApi() = mockk<JellyfinApi>(relaxed = true) {
        coEvery { sections(any(), any()) } returns section
        coEvery { randomTracks(any(), any(), any()) } returns tracks()
        coEvery { instantMix(any(), any(), any()) } returns tracks()
        coEvery { itemsPage(any(), any(), any()) } returns (tracks() to 5)
    }

    private fun List<PlexMetadata>.keys() = map { it.ratingKey }

    @Test
    fun `Plex shuffle, radios and never played leave out 1-star tracks`() = runTest {
        val repo = repository(ServerKind.PLEX, plex = plexApi())

        assertEquals(kept, repo.libraryShuffleTracks().keys())
        assertEquals(kept, repo.stationTracks(Station("/library/sections/1/stations/1", null)).keys())
        assertEquals(kept, repo.trackRadioTracks("a").keys())
        assertEquals(kept, repo.artistRadioTracks("9").keys())
        assertEquals(kept, repo.neverPlayedTracks().keys())
    }

    @Test
    fun `Jellyfin shuffle, radios and never played leave out 1-star tracks`() = runTest {
        val repo = repository(ServerKind.JELLYFIN, jellyfin = jellyfinApi())

        assertEquals(kept, repo.libraryShuffleTracks().keys())
        assertEquals(kept, repo.stationTracks(Station("jellyfin:library-shuffle", null)).keys())
        assertEquals(kept, repo.trackRadioTracks("a").keys())
        assertEquals(kept, repo.artistRadioTracks("9").keys())
        assertEquals(kept, repo.neverPlayedTracks().keys())
    }

    @Test
    fun `albums and playlists keep their 1-star tracks, skipped while playing`() = runTest {
        val plex = plexApi()
        coEvery { plex.children(any(), any(), any()) } returns tracks()
        coEvery { plex.playlistItems(any(), any(), any()) } returns tracks()
        val repo = repository(ServerKind.PLEX, plex = plex)

        assertEquals(5, repo.tracksOf("album").size)
        assertEquals(5, repo.playlistItems("playlist").size)
    }

    @Test
    fun `offline, downloads rated 1 star stay out of the draw`() {
        fun row(key: String) = DownloadEntity(
            serverId = "m1", ratingKey = key, title = key, artist = null, album = null, albumKey = "al",
            albumArtist = null, trackNumber = null, discNumber = null, durationMs = null,
            partKey = "/p/$key", thumb = null, state = DownloadState.DONE, filePath = null,
        )
        val learned = book.learn(listOf(PlexMetadata(ratingKey = "server", userRating = 2f)))

        val keptRows = learned.withoutOneStarDownloads(listOf(row("a"), row("mine"), row("server"), row("rescued")))

        assertEquals(listOf("a", "rescued"), keptRows.map { it.ratingKey })
    }
}

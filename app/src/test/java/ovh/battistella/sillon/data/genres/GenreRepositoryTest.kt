package ovh.battistella.sillon.data.genres

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.home.CachedHome
import ovh.battistella.sillon.data.home.SearchHomeRepository
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.LoudnessMode
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlayableTrack
import java.io.File
import kotlin.random.Random

/** The genre list from disk and server, offline, the surprise, sign-out, and the shuffle's filter. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GenreRepositoryTest {
    private val ready = Session.Ready("tok", SelectedServer("m1", "Demo", ServerEndpoint("https://demo", "tok", userId = "u1")))
    private val session = MutableStateFlow<Session>(ready)
    private val online = MutableStateFlow(true)
    private val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val home = MutableStateFlow<CachedHome?>(null)
    private var serverAlbums = listOf(
        GenreAlbum("1", "One", "Demo Band", "ar1", genres = listOf("Rock")),
        GenreAlbum("2", "Two", "Demo Band", "ar1", genres = listOf("rock", "Jazz")),
        GenreAlbum("secret", "Secret", "Hidden Band", "ar2", genres = listOf("Rock", "Ambient")),
    )
    private val plex = mockk<PlexRepository>(relaxed = true) {
        every { session } returns this@GenreRepositoryTest.session
        coEvery { genreAlbums() } coAnswers { serverAlbums }
        coEvery { currentServerId() } returns "m1"
    }
    private val searchHome = mockk<SearchHomeRepository> {
        every { cached } returns home
        coEvery { hidden(any()) } answers { firstArg<Set<String>>() }
    }
    private val downloads = mockk<DownloadManager> { every { observe("m1") } returns rows }
    private val network = mockk<NetworkMonitor> { every { this@mockk.online } returns this@GenreRepositoryTest.online }
    private val settings = mockk<SettingsStore> { every { loudness } returns flowOf(LoudnessMode.OFF) }
    private val store = GenreCache(ApplicationProvider.getApplicationContext())

    private fun repo(filter: GenreShuffleFilter = GenreShuffleFilter.KeepAll) =
        GenreRepository(plex, store, searchHome, downloads, network, settings, filter)

    private fun view(r: GenreRepository) = runBlocking { r.view.first() }

    private fun downloaded(albumKey: String, track: String): DownloadEntity {
        val file = File.createTempFile("track", ".flac").apply { deleteOnExit() }
        return DownloadEntity(
            "m1", track, "Track $track", "Demo Band", "Album $albumKey", albumKey, "Demo Band",
            1, 1, 1000, "/part", null, DownloadState.DONE, filePath = file.path,
        )
    }

    @Test
    fun `refresh fetches, caches on disk and merges the spellings`() = runBlocking {
        val r = repo()
        r.refresh(now = 1_000)
        val genres = view(r).genres
        assertEquals(listOf("Rock" to 3, "Ambient" to 1, "Jazz" to 1), genres.map { it.label to it.albums.size })
        assertTrue(view(r).known)
        // Read back by a fresh repository: from disk, without asking the server.
        serverAlbums = emptyList()
        assertEquals(3, view(repo()).genres.first().albums.size)
    }

    @Test
    fun `a fresh cache is not fetched again, an old one is`() = runBlocking {
        val r = repo()
        r.refresh(now = 1_000)
        r.refresh(now = 2_000)
        coVerify(exactly = 1) { plex.genreAlbums() }
        r.refresh(now = 1_000 + 16 * 60 * 1_000L)
        coVerify(exactly = 2) { plex.genreAlbums() }
    }

    @Test
    fun `an unrevealed surprise is in no genre`() = runBlocking {
        home.value = CachedHome(ready.server.machineId + "-u1", sealed = setOf("secret"))
        val r = repo()
        r.refresh(now = 1_000)
        val genres = view(r).genres
        assertEquals(listOf("Rock" to 2, "Jazz" to 1), genres.map { it.label to it.albums.size })
        assertTrue(genres.flatMap { it.albums }.none { it.key == "secret" })
    }

    @Test
    fun `offline only the genres of downloaded albums show`() = runBlocking {
        val r = repo()
        r.refresh(now = 1_000)
        online.value = false
        assertEquals(emptyList<Genre>(), view(r).genres)
        rows.value = listOf(downloaded("2", "t2"))
        assertEquals(listOf("Jazz" to 1, "Rock" to 1), view(r).genres.map { it.label to it.albums.size })
        assertEquals(false, view(r).online)
    }

    @Test
    fun `sign-out forgets the genres`() = runBlocking {
        val r = repo().also { it.start() }
        r.refresh(now = 1_000)
        session.value = Session.SignedOut
        val until = System.currentTimeMillis() + 5_000
        while (File(ApplicationProvider.getApplicationContext<Application>().noBackupFilesDir, "search-genres").exists()) {
            check(System.currentTimeMillis() < until) { "cache not cleared" }
            Thread.sleep(10)
        }
        assertNull(r.cached.first())
    }

    @Test
    fun `the shuffle goes through the filter, the 1-star rule's hook`() = runBlocking {
        val kept = slot<List<PlexMetadata>>()
        coEvery { plex.tracksOf(any()) } answers {
            val album = firstArg<String>()
            listOf(PlexMetadata("$album-a", type = "track"), PlexMetadata("$album-b", type = "track"))
        }
        coEvery { plex.playable(capture(kept), any()) } answers {
            kept.captured.map { PlayableTrack(it.ratingKey, "u", it.ratingKey, null, null, null, null, null) }
        }
        val dropB = object : GenreShuffleFilter {
            override suspend fun online(tracks: List<PlexMetadata>) = tracks.filterNot { it.ratingKey.endsWith("-b") }
        }
        val r = repo(dropB)
        r.refresh(now = 1_000)
        val rock = view(r).genres.first { it.key == "rock" }
        val played = r.shuffle(rock, online = true, random = Random(1)).map { it.ratingKey }.toSet()
        assertEquals(setOf("1-a", "2-a", "secret-a"), played)
    }

    @Test
    fun `offline the shuffle plays the downloaded tracks of the genre, filtered too`() = runBlocking {
        rows.value = listOf(downloaded("1", "t1"), downloaded("1", "t1b"), downloaded("other", "t9"))
        val dropT1b = object : GenreShuffleFilter {
            override suspend fun offline(tracks: List<DownloadEntity>) = tracks.filterNot { it.ratingKey == "t1b" }
        }
        val r = repo(dropT1b)
        r.refresh(now = 1_000)
        online.value = false
        val rock = view(r).genres.single()
        assertEquals(listOf("t1"), r.shuffle(rock, online = false).map { it.ratingKey })
    }
}

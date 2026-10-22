package ovh.battistella.sillon.ui.screens.listen

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.CachedFiveStars
import ovh.battistella.sillon.data.settings.FiveStarsCache
import ovh.battistella.sillon.data.settings.LoudnessMode
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.data.rating.RatingBook
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackConnection
import java.io.IOException

/**
 * The real [ListenViewModel], with fake repository, downloads, cache, network and player: where
 * Shuffle and 5 stars take their music from, online, offline, and when the server fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListenViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val online = MutableStateFlow(true)
    private val cached = MutableStateFlow<CachedFiveStars?>(null)
    private val played = mutableListOf<Pair<List<String>, Boolean>>()

    private val network = mockk<NetworkMonitor> {
        every { this@mockk.online } returns this@ListenViewModelTest.online
        every { isOnline() } answers { this@ListenViewModelTest.online.value }
    }
    private val repository = mockk<PlexRepository> {
        coEvery { ratingBook() } returns RatingBook()
        coEvery { currentServerId() } returns SERVER
        every { isJellyfin } returns flowOf(false)
        coEvery { fiveStarTracks() } returns emptyList()
        val tracks = slot<List<PlexMetadata>>()
        coEvery { playable(capture(tracks), any()) } answers { tracks.captured.map { streamed(it.ratingKey) } }
    }
    private val fiveStarsCache = mockk<FiveStarsCache> {
        every { observe(SERVER) } returns cached
        val keys = slot<Collection<String>>()
        coEvery { save(SERVER, capture(keys), any()) } answers { cached.value = CachedFiveStars(keys.captured.toSet(), NOW()) }
    }
    private val playback = mockk<PlaybackConnection> {
        val tracks = slot<List<PlayableTrack>>()
        val shuffle = slot<Boolean>()
        every { play(capture(tracks), any(), capture(shuffle), any()) } answers {
            played += tracks.captured.map { it.ratingKey } to shuffle.captured
        }
    }
    private val settings = mockk<SettingsStore> { every { loudness } returns flowOf(LoudnessMode.OFF) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `online, shuffle plays the server's random draw`() {
        coEvery { repository.libraryShuffleTracks() } returns listOf(meta("s1"), meta("s2"))
        val model = model(downloads = listOf(row("d1")))

        model.shuffle()
        model.awaitIdle()

        assertEquals(listOf(listOf("s1", "s2") to false), played)
        assertNull(model.notice.value)
    }

    @Test
    fun `online but the server fails, shuffle falls back to the downloads`() {
        coEvery { repository.libraryShuffleTracks() } throws IOException("unreachable")
        val model = model(downloads = listOf(row("d1"), row("d2")))

        model.shuffle()
        model.awaitIdle()

        assertEquals(setOf("d1", "d2"), played.single().first.toSet())
        assertEquals(ListenNotice.SHUFFLE_FROM_DOWNLOADS, model.notice.value)
    }

    @Test
    fun `offline, shuffle plays only the downloads on disk and never asks the server`() {
        online.value = false
        val model = model(downloads = listOf(row("d1"), row("d2"), row("gone", onDisk = false), row("half", state = DownloadState.DOWNLOADING)))

        model.shuffle()
        model.awaitIdle()

        assertEquals(setOf("d1", "d2"), played.single().first.toSet())
        assertEquals(ListenNotice.SHUFFLE_FROM_DOWNLOADS, model.notice.value)
        coVerify(exactly = 0) { repository.libraryShuffleTracks() }
    }

    @Test
    fun `offline with nothing downloaded, shuffle plays nothing and says it failed`() {
        online.value = false
        val model = model(downloads = emptyList())

        model.shuffle()
        model.awaitIdle()

        assertEquals(emptyList<Any>(), played)
        assertEquals(ListenNotice.FAILED, model.notice.value)
    }

    @Test
    fun `online, 5 stars plays the server list shuffled and keeps its keys for later`() {
        cached.value = CachedFiveStars(setOf("old"), NOW())
        coEvery { repository.fiveStarTracks() } returns listOf(meta("f1"), meta("f2"))
        val model = model(downloads = emptyList())

        model.fiveStars()
        model.awaitIdle()

        assertEquals(listOf(listOf("f1", "f2") to true), played)
        assertEquals(setOf("f1", "f2"), cached.value?.keys)
        assertNull(model.notice.value)
    }

    @Test
    fun `online with no 5-star track on the server, 5 stars says so`() {
        cached.value = CachedFiveStars(emptySet(), NOW())
        val model = model(downloads = listOf(row("d1")))

        model.fiveStars()
        model.awaitIdle()

        assertEquals(emptyList<Any>(), played)
        assertEquals(ListenNotice.NO_FIVE_STARS, model.notice.value)
    }

    @Test
    fun `offline, 5 stars plays only the downloads saved as 5 stars`() {
        online.value = false
        cached.value = CachedFiveStars(setOf("d2", "d3", "not-downloaded"), 0)
        val model = model(downloads = listOf(row("d1"), row("d2"), row("d3")))
        model.awaitOffline(OfflineMusic(tracks = 3, fiveStars = 2))

        model.fiveStars()
        model.awaitIdle()

        assertEquals(setOf("d2", "d3"), played.single().first.toSet())
        assertEquals(ListenNotice.FIVE_STARS_FROM_DOWNLOADS, model.notice.value)
        coVerify(exactly = 0) { repository.fiveStarTracks() }
    }

    @Test
    fun `offline with no 5-star download, 5 stars plays nothing`() {
        online.value = false
        cached.value = CachedFiveStars(setOf("elsewhere"), 0)
        val model = model(downloads = listOf(row("d1")))
        model.awaitOffline(OfflineMusic(tracks = 1, fiveStars = 0))

        model.fiveStars()
        model.awaitIdle()

        assertEquals(emptyList<Any>(), played)
        assertEquals(ListenNotice.FAILED, model.notice.value)
    }

    @Test
    fun `online but the server fails, 5 stars falls back to the saved 5-star downloads`() {
        cached.value = CachedFiveStars(setOf("d1"), NOW())
        val model = model(downloads = listOf(row("d1"), row("d2")))
        model.awaitOffline(OfflineMusic(tracks = 2, fiveStars = 1))
        coEvery { repository.fiveStarTracks() } throws IOException("unreachable")

        model.fiveStars()
        model.awaitIdle()

        assertEquals(listOf(listOf("d1") to false), played)
        assertEquals(ListenNotice.FIVE_STARS_FROM_DOWNLOADS, model.notice.value)
    }

    @Test
    fun `on opening, a 5-star list older than 6 hours is fetched again`() {
        cached.value = CachedFiveStars(setOf("old"), NOW() - FiveStarsCache.TTL_MS - 1)
        coEvery { repository.fiveStarTracks() } returns listOf(meta("f1"))

        model(downloads = emptyList())

        runBlocking { withTimeout(TIMEOUT_MS) { while (cached.value?.keys != setOf("f1")) delay(10) } }
    }

    @Test
    fun `on opening, a fresh 5-star list or no connection asks nothing of the server`() {
        cached.value = CachedFiveStars(setOf("f1"), NOW() - FiveStarsCache.TTL_MS + 60_000)
        model(downloads = emptyList()).awaitOffline(OfflineMusic())
        online.value = false
        cached.value = null
        model(downloads = emptyList()).awaitOffline(OfflineMusic())

        coVerify(exactly = 0) { repository.fiveStarTracks() }
        coVerify(exactly = 0) { fiveStarsCache.save(any(), any(), any()) }
    }

    @Test
    fun `the tile states follow the downloads and the saved 5-star keys`() {
        online.value = false
        cached.value = CachedFiveStars(setOf("d1"), 0)
        val model = model(downloads = listOf(row("d1"), row("d2")))
        model.awaitOffline(OfflineMusic(tracks = 2, fiveStars = 1))

        assertEquals(TileState.FROM_DOWNLOADS, ListenTiles.state(ListenTile.FIVE_STARS, model.online.value, model.offline.value))
        cached.value = null
        model.awaitOffline(OfflineMusic(tracks = 2, fiveStars = 0))
        assertEquals(TileState.NEEDS_SERVER, ListenTiles.state(ListenTile.FIVE_STARS, model.online.value, model.offline.value))
    }

    private fun model(downloads: List<DownloadEntity>): ListenViewModel {
        val manager = mockk<DownloadManager> { every { observe(SERVER) } returns flowOf(downloads) }
        return ListenViewModel(repository, manager, playback, settings, fiveStarsCache, network)
    }

    private fun ListenViewModel.awaitIdle() = runBlocking {
        withTimeout(TIMEOUT_MS) { while (starting.value != null) delay(10) }
    }

    private fun ListenViewModel.awaitOffline(expected: OfflineMusic) = runBlocking {
        withTimeout(TIMEOUT_MS) { while (offline.value != expected) delay(10) }
    }

    private fun meta(key: String) = PlexMetadata(ratingKey = key, type = "track", title = "Song $key")

    private fun streamed(key: String) = PlayableTrack(key, "https://server.invalid/$key", "Song $key", null, null, null, null, null)

    private fun row(key: String, onDisk: Boolean = true, state: DownloadState = DownloadState.DONE) = DownloadEntity(
        serverId = SERVER, ratingKey = key, title = "Song $key", artist = null, album = null, albumKey = null,
        albumArtist = null, trackNumber = null, discNumber = null, durationMs = null, partKey = "/p/$key",
        thumb = null, state = state,
        filePath = if (onDisk) folder.newFile("$key.flac").path else folder.root.resolve("gone-$key.flac").path,
    )

    private companion object {
        const val SERVER = "s"
        const val TIMEOUT_MS = 5_000L
        val NOW = System::currentTimeMillis
    }
}

package ovh.battistella.sillon.ui.screens.search

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.home.SearchHomeRepository
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.wear.SleeveWearRepository
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackConnection

/** A long press on an album of the search home is a *Play*: it opens on the first track worth hearing. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SearchAlbumPlayTest {
    private val online = MutableStateFlow(true)
    private val network = mockk<NetworkMonitor> {
        every { this@mockk.online } returns this@SearchAlbumPlayTest.online
        every { isOnline() } answers { this@SearchAlbumPlayTest.online.value }
    }
    private val repository = mockk<PlexRepository>(relaxed = true) {
        every { session } returns emptyFlow()
        coEvery { tracksOf("album") } returns listOf("t1", "t2").map { PlexMetadata(ratingKey = it, type = "track", title = it) }
        val tracks = slot<List<PlexMetadata>>()
        coEvery { playable(capture(tracks), any()) } answers {
            tracks.captured.map { PlayableTrack(it.ratingKey, "https://server.invalid/${it.ratingKey}", it.title, null, null, null, null, null) }
        }
    }
    private val searchHome = mockk<SearchHomeRepository>(relaxed = true) { every { cached } returns emptyFlow() }
    private val sleeveWear = mockk<SleeveWearRepository> { every { levels } returns emptyFlow() }
    private val playback = mockk<PlaybackConnection>(relaxed = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a long press on an album picks its start past the 1-star tracks`() {
        val model = SearchViewModel(
            repository = repository,
            playback = playback,
            searchHome = searchHome,
            downloads = mockk<DownloadManager>(relaxed = true),
            settings = mockk<SettingsStore>(relaxed = true),
            network = network,
            sleeveWear = sleeveWear,
        )

        model.playAlbum("album")

        verify(timeout = 5_000) { playback.play(match { it.map(PlayableTrack::ratingKey) == listOf("t1", "t2") }, 0, false, any(), true) }
    }
}

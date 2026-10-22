package ovh.battistella.sillon.ui.screens.listen

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackConnection
import java.io.IOException

/** The radios on Listen: loaded from the server, again when the connection returns, off while offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenRadiosViewModelTest {
    private val online = MutableStateFlow(true)
    private val played = mutableListOf<List<String>>()

    private val network = mockk<NetworkMonitor> {
        every { this@mockk.online } returns this@ListenRadiosViewModelTest.online
        every { isOnline() } answers { this@ListenRadiosViewModelTest.online.value }
    }
    private val repository = mockk<PlexRepository> {
        coEvery { stations() } returns STATIONS
        coEvery { stationTracks(any()) } returns listOf(meta("t1"), meta("t2"))
        val tracks = slot<List<PlexMetadata>>()
        coEvery { playable(capture(tracks), any()) } answers { tracks.captured.map { playable(it.ratingKey) } }
    }
    private val playback = mockk<PlaybackConnection> {
        val tracks = slot<List<PlayableTrack>>()
        every { play(capture(tracks), any(), any(), any()) } answers { played += tracks.captured.map { it.ratingKey } }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `online, the radios load from the server`() {
        val model = ListenRadiosViewModel(repository, playback, network)

        assertEquals(STATIONS, model.stations.value)
    }

    @Test
    fun `offline at first, the radios load once the connection comes back`() {
        online.value = false
        val model = ListenRadiosViewModel(repository, playback, network)
        assertEquals(emptyList<Station>(), model.stations.value)
        coVerify(exactly = 0) { repository.stations() }

        online.value = true

        assertEquals(STATIONS, model.stations.value)
    }

    @Test
    fun `a failed reload keeps the radios already shown`() {
        val model = ListenRadiosViewModel(repository, playback, network)
        coEvery { repository.stations() } throws IOException("unreachable")

        model.load()

        assertEquals(STATIONS, model.stations.value)
    }

    @Test
    fun `starting a radio plays its tracks`() {
        val model = ListenRadiosViewModel(repository, playback, network)

        model.play(STATIONS[1])

        assertEquals(listOf(listOf("t1", "t2")), played)
        coVerify { repository.stationTracks(STATIONS[1]) }
        assertNull(model.starting.value)
    }

    @Test
    fun `offline, a radio does not start`() {
        val model = ListenRadiosViewModel(repository, playback, network)
        online.value = false

        model.play(STATIONS[0])

        assertEquals(emptyList<List<String>>(), played)
        coVerify(exactly = 0) { repository.stationTracks(any()) }
    }

    @Test
    fun `a radio with no tracks plays nothing`() {
        coEvery { repository.stationTracks(any()) } returns emptyList()
        val model = ListenRadiosViewModel(repository, playback, network)

        model.play(STATIONS[0])

        verify(exactly = 0) { playback.play(any(), any(), any(), any()) }
    }

    @Test
    fun `the section shows with radios, or offline to say why they are off`() {
        assertTrue(showRadios(STATIONS, online = true))
        assertTrue(showRadios(STATIONS, online = false))
        assertTrue(showRadios(emptyList(), online = false))
        assertFalse(showRadios(emptyList(), online = true))
    }

    private fun meta(key: String) = PlexMetadata(ratingKey = key, type = "track", title = "Song $key")

    private fun playable(key: String) = PlayableTrack(key, "https://server.invalid/$key", "Song $key", null, null, null, null, null)

    private companion object {
        val STATIONS = listOf(Station("mix", "Synthetic mix"), Station("deep", "Synthetic deep cuts"))
    }
}

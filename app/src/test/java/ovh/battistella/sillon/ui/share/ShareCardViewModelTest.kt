package ovh.battistella.sillon.ui.share

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareCardViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val repository = mockk<PlexRepository>()
    private lateinit var viewModel: ShareCardViewModel

    private val trackA = ShareSource(ShareSource.Kind.TRACK, "a", "Track A", "Synthetic Artist")
    private val trackB = ShareSource(ShareSource.Kind.TRACK, "b", "Track B", "Synthetic Artist")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        every { repository.isJellyfin } returns flowOf(false)
        viewModel = ShareCardViewModel(ApplicationProvider.getApplicationContext(), repository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun open(source: ShareSource) {
        viewModel.open(source) { "“$it”" }
        scheduler.runCurrent()
    }

    @Test
    fun `a slow answer for the previous track never lands on the new card`() {
        val slowA = CompletableDeferred<PlexMetadata?>()
        coEvery { repository.metadata("a") } coAnswers { slowA.await() }
        coEvery { repository.lyrics("a") } returns listOf("Line of A")
        coEvery { repository.metadata("b") } returns PlexMetadata(ratingKey = "b", userRating = 6f, parentYear = 2001)
        coEvery { repository.lyrics("b") } returns listOf("Line of B")

        open(trackA)
        open(trackB)
        slowA.complete(PlexMetadata(ratingKey = "a", userRating = 10f, parentYear = 1979))
        scheduler.runCurrent()

        val ui = viewModel.ui.value
        assertEquals(trackB, ui.source)
        assertFalse(ui.loading)
        assertEquals(3f, ui.stars)
        assertEquals("2001", ui.detail)
        assertEquals(listOf("Line of B"), ui.lyrics)
    }

    @Test
    fun `offline, the card keeps what the screen knew and can be shared`() {
        coEvery { repository.metadata("a") } throws IOException("no route to host")
        coEvery { repository.lyrics("a") } throws IOException("no route to host")

        open(trackA.copy(album = "Synthetic Album"))

        val ui = viewModel.ui.value
        assertFalse(ui.loading)
        assertNull(ui.stars)
        assertEquals(emptyList<String>(), ui.lyrics)
        assertEquals("Synthetic Album", ui.detail)
    }

    @Test
    fun `failing lyrics keep the rating`() {
        coEvery { repository.metadata("a") } returns PlexMetadata(ratingKey = "a", userRating = 8f)
        coEvery { repository.lyrics("a") } throws IOException("HTTP 500")

        open(trackA)

        assertEquals(4f, viewModel.ui.value.stars)
        assertEquals(emptyList<String>(), viewModel.ui.value.lyrics)
    }

    @Test
    fun `a server that never answers releases the Share button after 5 seconds`() {
        coEvery { repository.metadata("a") } coAnswers { awaitCancellation() }

        open(trackA)
        assertEquals(true, viewModel.ui.value.loading)
        scheduler.advanceTimeBy(5_001)
        scheduler.runCurrent()

        assertFalse(viewModel.ui.value.loading)
        assertNull(viewModel.ui.value.stars)
    }
}

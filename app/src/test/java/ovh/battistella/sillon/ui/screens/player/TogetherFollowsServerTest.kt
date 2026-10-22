package ovh.battistella.sillon.ui.screens.player

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.ui.screens.library.LibraryMenuViewModel

/**
 * The real player and menu view models, with a fake repository: the together entry follows the
 * active server, so switching between Plex and Jellyfin shows or hides it without a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TogetherFollowsServerTest {
    private val jellyfin = MutableStateFlow(false)
    private val repository = mockk<PlexRepository>(relaxed = true) {
        every { isJellyfin } returns jellyfin
        every { musicSectionKey } returns MutableStateFlow(null)
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the player button follows the server`() = follows(
        PlayerViewModel(mockk(relaxed = true), repository, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)).together,
    )

    @Test
    fun `the menu entry follows the server`() = follows(LibraryMenuViewModel(repository).together)

    private fun follows(together: StateFlow<Boolean>) = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { together.collect {} }
        assertEquals("Plex", false, together.value)

        jellyfin.value = true
        assertEquals("switched to Jellyfin", true, together.value)

        jellyfin.value = false
        assertEquals("back to Plex", false, together.value)
    }
}

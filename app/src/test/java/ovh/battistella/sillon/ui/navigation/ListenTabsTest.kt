package ovh.battistella.sillon.ui.navigation

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.ui.screens.library.PlaylistsScreen
import ovh.battistella.sillon.ui.screens.listen.ListenScreen
import ovh.battistella.sillon.ui.screens.listen.OfflineMusic
import ovh.battistella.sillon.ui.theme.SillonTheme

/** The bottom bar holds Listen, Playlists and Search; Listen opens first and plays in one tap. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h2000dp")
class ListenTabsTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the bottom bar has listen, playlists and search, and listen opens first`() {
        compose.setContent { SillonTheme { SillonNavigationBar(START_DESTINATION) {} } }

        assertEquals(Routes.LISTEN, START_DESTINATION)
        compose.onNodeWithText(context.getString(R.string.listen_tab)).assertIsDisplayed().assertIsSelected()
        listOf(R.string.library_playlists, R.string.library_search)
            .forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        listOf(R.string.library_artists, R.string.library_albums, R.string.library_downloads)
            .forEach { compose.onNodeWithText(context.getString(it)).assertDoesNotExist() }
    }

    @Test
    fun `a saved tab that left the bar falls back to listen`() {
        listOf(Routes.ARTISTS, Routes.ALBUMS, Routes.DOWNLOADS, "home", "unknown", null).forEach {
            assertEquals("saved $it", Routes.LISTEN, restoredTab(it))
        }
        listOf(Routes.LISTEN, Routes.PLAYLISTS, Routes.SEARCH).forEach { assertEquals(it, restoredTab(it)) }
    }

    @Test
    fun `restoring a saved artists tab shows listen selected without crashing`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            // The state an older version saved: Artists was the first tab.
            val tab by rememberSaveable(saver = CurrentTabSaver) { mutableStateOf(Routes.ARTISTS) }
            SillonTheme { SillonNavigationBar(tab) {} }
        }

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(context.getString(R.string.listen_tab)).assertIsSelected()
    }

    @Test
    fun `listen shows the tiles, each a button with its action for TalkBack`() {
        val fakes = ListenFakes()
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        listOf(R.string.listen_shuffle, R.string.five_stars, R.string.never_played, R.string.mystery_title, R.string.never_played_albums)
            .forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(R.string.listen_offline)).assertDoesNotExist()
        listOf(
            R.string.listen_shuffle to R.string.listen_shuffle_action,
            R.string.five_stars to R.string.five_stars_shuffle,
            R.string.never_played to R.string.never_played_play,
            R.string.never_played_albums to R.string.never_played_albums_play,
        ).forEach { (title, action) ->
            compose.onNode(hasText(context.getString(title)) and hasClickAction()).assert(clickLabel(context.getString(action)))
        }
        compose.onNodeWithText(context.getString(R.string.listen_shuffle)).performClick()
        verify { fakes.listen.shuffle() }
    }

    @Test
    fun `offline with downloads, shuffle and 5 stars play them and the list waits for the server`() {
        val fakes = ListenFakes(online = false, offline = OfflineMusic(tracks = 12, fiveStars = 3))
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.listen_offline)).assertIsDisplayed()
        tile(R.string.listen_shuffle).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.listen_shuffle_downloads)).assertIsDisplayed()
        tile(R.string.five_stars).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.listen_five_stars_downloads)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.listen_five_stars_list)).assertIsNotEnabled()
        tile(R.string.never_played).assertIsEnabled()
    }

    @Test
    fun `offline without 5-star downloads, the 5 stars tile is off and says why`() {
        val fakes = ListenFakes(online = false, offline = OfflineMusic(tracks = 12, fiveStars = 0))
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        tile(R.string.five_stars).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.listen_five_stars_needs_server)).assertIsDisplayed()
        tile(R.string.listen_shuffle).assertIsEnabled()
    }

    @Test
    fun `offline with nothing downloaded, every tile is off and the banner says why`() {
        val fakes = ListenFakes(online = false, offline = OfflineMusic())
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.listen_offline_nothing)).assertIsDisplayed()
        listOf(R.string.listen_shuffle, R.string.five_stars, R.string.never_played, R.string.never_played_albums, R.string.mystery_title)
            .forEach { tile(it).assertIsNotEnabled() }
    }

    @Test
    fun `listen shows the radios under the tiles, each a button that starts it`() {
        val fakes = ListenFakes()
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.radios)).assertIsDisplayed()
        listOf(context.getString(R.string.radio_library), "Synthetic radio", "Synthetic deep cuts").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assertIsDisplayed().assertIsEnabled()
                .assert(clickLabel(context.getString(R.string.listen_radio_play)))
        }
        compose.onNodeWithText(context.getString(R.string.listen_radios_offline)).assertDoesNotExist()
        compose.onNodeWithText("Synthetic radio").performClick()
        verify { fakes.radios.play(Station("s2", "Synthetic radio")) }
    }

    @Test
    fun `offline, the radios stay listed but off, and say why`() {
        val fakes = ListenFakes(online = false, offline = OfflineMusic(tracks = 12))
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.listen_radios_offline)).assertIsDisplayed()
        compose.onNode(hasText("Synthetic radio") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `the never-played albums tile has a subtitle like the others`() {
        val fakes = ListenFakes()
        compose.setContent { SillonTheme { fakes.Provide { ListenScreen(onFiveStarsList = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.listen_never_played_albums_hint)).assertIsDisplayed()
    }

    @Test
    fun `playlists keep only the playlists, not the radios nor the tiles that moved to listen`() {
        val fakes = ListenFakes()
        compose.setContent { SillonTheme { fakes.Provide { PlaylistsScreen(onPlaylist = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.library_playlists)).assertIsDisplayed()
        compose.onNodeWithText("Synthetic Mix").assertIsDisplayed()
        listOf(context.getString(R.string.radios), context.getString(R.string.radio_library), "Synthetic radio")
            .forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        listOf(R.string.five_stars, R.string.never_played, R.string.mystery_title)
            .forEach { compose.onNodeWithText(context.getString(it)).assertDoesNotExist() }
    }

    @Test
    fun `playlists without any playlist say so and point to the radios`() {
        val fakes = ListenFakes(playlists = emptyList())
        compose.setContent { SillonTheme { fakes.Provide { PlaylistsScreen(onPlaylist = {}, contentPadding = PaddingValues()) } } }

        compose.onNodeWithText(context.getString(R.string.playlists_empty)).assertIsDisplayed()
    }

    private fun tile(title: Int) = compose.onNode(hasText(context.getString(title)) and hasClickAction())

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == label
    }

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }
}

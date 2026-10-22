package ovh.battistella.sillon.ui.navigation

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.ui.screens.downloads.DownloadsScreen
import ovh.battistella.sillon.ui.screens.downloads.DownloadsViewModel
import ovh.battistella.sillon.ui.screens.settings.SettingsScreen
import ovh.battistella.sillon.ui.screens.settings.SettingsViewModel
import ovh.battistella.sillon.ui.theme.SillonTheme

/** Downloads left the bottom bar for the settings; the settings entry leads to the downloads screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsInSettingsTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the bottom bar has no downloads tab`() {
        compose.setContent { SillonTheme { SillonNavigationBar(Routes.LISTEN) {} } }

        listOf(R.string.listen_tab, R.string.library_playlists, R.string.library_search)
            .forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(R.string.library_downloads)).assertDoesNotExist()
    }

    @Test
    fun `the settings entry opens the downloads, which go back and still open an album`() {
        val repository = mockk<PlexRepository> { coEvery { currentServerId() } returns "s" }
        val downloads = mockk<DownloadManager>(relaxed = true) {
            every { observe("s") } returns flowOf(listOf(row("1", "album-1"), row("2", "album-1")))
        }
        val settings = SettingsStore(context)
        val settingsModel = SettingsViewModel(settings, downloads, repository)
        val downloadsModel = DownloadsViewModel(repository, downloads, settings)
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            SillonTheme {
                NavHost(nav, startDestination = Routes.SETTINGS) {
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = {},
                            onDownloads = { nav.navigate(Routes.DOWNLOADS) },
                            contentPadding = PaddingValues(),
                            viewModel = settingsModel,
                        )
                    }
                    composable(Routes.DOWNLOADS) {
                        DownloadsScreen(
                            onAlbum = { nav.navigate(Routes.downloadedAlbum(it)) },
                            onBack = { nav.popBackStack() },
                            contentPadding = PaddingValues(),
                            viewModel = downloadsModel,
                        )
                    }
                    composable(
                        Routes.DOWNLOADED_ALBUM,
                        arguments = listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
                    ) { Text("opened ${it.arguments?.getString(Routes.ARG_KEY)}") }
                }
            }
        }

        val summary = context.resources.getQuantityString(R.plurals.settings_downloads_summary, 1, 1, "")
        compose.onNodeWithText(summary.substringBefore(" ·"), substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_downloads)).performClick()
        compose.waitForIdle()
        assertEquals(Routes.DOWNLOADS, nav.currentDestination?.route)
        compose.onNodeWithText("Synthetic Album").performClick()
        compose.onNodeWithText("opened album-1").assertIsDisplayed()

        compose.runOnUiThread { nav.popBackStack() }
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertEquals(Routes.SETTINGS, nav.currentDestination?.route)
    }

    private fun row(key: String, albumKey: String) = DownloadEntity(
        serverId = "s", ratingKey = key, title = "Song $key", artist = null, album = "Synthetic Album",
        albumKey = albumKey, albumArtist = "Synthetic Artist", trackNumber = null, discNumber = null,
        durationMs = null, partKey = "/library/parts/$key/1/f.flac", thumb = null, state = DownloadState.DONE,
        sizeBytes = 1_000_000,
    )

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }
}

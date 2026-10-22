package ovh.battistella.sillon.ui.navigation

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.LocalOpenDownloads
import ovh.battistella.sillon.ui.screens.downloads.DownloadsScreen
import ovh.battistella.sillon.ui.screens.downloads.DownloadsViewModel
import ovh.battistella.sillon.ui.screens.settings.SettingsScreen
import ovh.battistella.sillon.ui.screens.settings.SettingsViewModel
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Renders the bottom bar, the settings, the downloads screen and the offline error with synthetic
 * data, in English and French, into `app/build/downloads-settings-renders/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h2000dp-xhdpi")
class DownloadsInSettingsRenders {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/downloads-settings-renders").apply { mkdirs() }
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private var screen by mutableStateOf<Screen?>(null)

    @Test
    fun `renders in English`() = renderAll("en")

    @Test
    @Config(qualifiers = "+fr")
    fun `renders in French`() = renderAll("fr")

    private fun renderAll(lang: String) {
        save(capture { SillonNavigationBar(Routes.LISTEN) {} }, "bottom-bar-$PHASE-$lang.png")
        val repository = mockk<PlexRepository> { coEvery { currentServerId() } returns "s" }
        val downloads = mockk<DownloadManager>(relaxed = true) { every { observe("s") } returns flowOf(ROWS) }
        val store = SettingsStore(context)
        val settings = SettingsViewModel(store, downloads, repository)
        save(
            capture { SettingsScreen(onBack = {}, onDownloads = {}, contentPadding = PaddingValues(), viewModel = settings) },
            "settings-$PHASE-$lang.png",
        )
        val list = DownloadsViewModel(repository, downloads, store)
        save(
            capture { DownloadsScreen(onAlbum = {}, onBack = {}, contentPadding = PaddingValues(), viewModel = list) },
            "downloads-$PHASE-$lang.png",
        )
        // A library screen that cannot reach the server: the error offers the downloads.
        save(
            capture {
                CompositionLocalProvider(LocalOpenDownloads provides {}) {
                    LoadContent(Load.Failed("Unable to resolve host"), onRetry = {}) {}
                }
            },
            "offline-$PHASE-$lang.png",
        )
    }

    private fun capture(content: @Composable () -> Unit): Bitmap {
        if (screen == null) {
            compose.setContent {
                screen?.let { current ->
                    SillonTheme(darkTheme = false) {
                        Surface(Modifier.testTag(ROOT), color = MaterialTheme.colorScheme.background) {
                            key(current) { current.content() }
                        }
                    }
                }
            }
        }
        screen = Screen(content)
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(Canvas(full)) }
        val bounds = compose.onNodeWithTag(ROOT).fetchSemanticsNode().boundsInWindow
        return Bitmap.createBitmap(
            full,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt().coerceAtMost(full.width - bounds.left.toInt()),
            bounds.height.toInt().coerceAtMost(full.height - bounds.top.toInt()),
        )
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Screen(val content: @Composable () -> Unit)

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    private companion object {
        const val ROOT = "root"
        const val PHASE = "after"

        /** 12 synthetic albums of 10 tracks, 3.4 GB in all: no real library in the renders. */
        val ROWS = (1..12).flatMap { album ->
            (1..10).map { track ->
                DownloadEntity(
                    serverId = "s", ratingKey = "$album-$track", title = "Song $track", artist = null,
                    album = "Synthetic Album $album", albumKey = "album-$album", albumArtist = "Synthetic Artist",
                    trackNumber = track, discNumber = null, durationMs = null,
                    partKey = "/library/parts/$album$track/1/f.flac", thumb = null, state = DownloadState.DONE,
                    sizeBytes = 3_400_000_000L / 120,
                )
            }
        }
    }
}

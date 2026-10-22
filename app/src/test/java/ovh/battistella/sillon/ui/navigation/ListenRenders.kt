package ovh.battistella.sillon.ui.navigation

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
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
import ovh.battistella.sillon.ui.screens.library.PlaylistsScreen
import ovh.battistella.sillon.ui.screens.listen.ListenScreen
import ovh.battistella.sillon.ui.screens.listen.OfflineMusic
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Renders the Listen page (online, offline with downloads, offline with nothing) and the Playlists
 * tab (with playlists, and empty) with the bottom bar, with synthetic data, in English and French, into `app/build/listen-renders/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class ListenRenders {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/listen-renders").apply { mkdirs() }
    private var page by mutableStateOf<Page?>(null)

    @Test
    fun `renders in English`() = renderAll("en")

    @Test
    @Config(qualifiers = "+fr")
    fun `renders in French`() = renderAll("fr")

    private fun renderAll(lang: String) {
        compose.setContent {
            val current = page ?: return@setContent
            key(current.name) {
            SillonTheme(darkTheme = false) {
                current.fakes.Provide {
                    Scaffold(
                        modifier = Modifier.testTag(ROOT),
                        bottomBar = { SillonNavigationBar(current.tab) {} },
                    ) { padding ->
                        if (current.tab == Routes.LISTEN) {
                            ListenScreen(onFiveStarsList = {}, contentPadding = padding)
                        } else {
                            PlaylistsScreen(onPlaylist = {}, contentPadding = padding)
                        }
                    }
                }
            }
            }
        }
        listOf(
            Page("listen-online", Routes.LISTEN, ListenFakes()),
            Page("listen-offline", Routes.LISTEN, ListenFakes(online = false, offline = OfflineMusic(tracks = 42, fiveStars = 0))),
            Page("listen-offline-nothing", Routes.LISTEN, ListenFakes(online = false, offline = OfflineMusic())),
            Page("playlists", Routes.PLAYLISTS, ListenFakes()),
            Page("playlists-empty", Routes.PLAYLISTS, ListenFakes(playlists = emptyList())),
        ).forEach {
            compose.runOnUiThread { page = it }
            save(capture(), "${it.name}-$lang.png")
        }
    }

    private fun capture(): Bitmap {
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

    private class Page(val name: String, val tab: String, val fakes: ListenFakes)

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    private companion object {
        const val ROOT = "root"
    }
}

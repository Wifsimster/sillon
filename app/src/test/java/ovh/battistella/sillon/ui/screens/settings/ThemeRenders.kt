package ovh.battistella.sillon.ui.screens.settings

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.settings.ThemeAccent
import ovh.battistella.sillon.data.settings.ThemeMode
import ovh.battistella.sillon.data.settings.ThemePreferences
import ovh.battistella.sillon.data.storage.AndroidStorage
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Renders the theme page with a few choices, in English and French, into `app/build/theme-renders/`.
 * The page is wrapped in the theme it sets, as the app does, so each render shows the whole result.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ThemeRenders(private val locale: String) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/theme-renders").apply { mkdirs() }

    @Test
    fun systemAmber() = render("theme-1-system-amber", ThemePreferences())

    @Test
    fun lightRust() = render("theme-2-light-rust", ThemePreferences(mode = ThemeMode.LIGHT, accent = ThemeAccent.RUST))

    @Test
    fun darkTeal() = render("theme-3-dark-teal", ThemePreferences(mode = ThemeMode.DARK, accent = ThemeAccent.TEAL))

    @Test
    fun darkPureBlackPlum() = render(
        "theme-4-dark-pure-black-plum",
        ThemePreferences(mode = ThemeMode.DARK, accent = ThemeAccent.PLUM, pureBlack = true),
    )

    /** Material You on: the accents grey out and a hint says why. */
    @Test
    fun wallpaperColors() = render(
        "theme-5-wallpaper-colors",
        ThemePreferences(mode = ThemeMode.DARK, wallpaperColors = true),
        wallpaperAvailable = true,
    )

    /** Before Android 12 there is no Material You switch. */
    @Test
    fun noWallpaperColors() = render(
        "theme-6-no-wallpaper-switch",
        ThemePreferences(mode = ThemeMode.LIGHT, accent = ThemeAccent.INDIGO),
        wallpaperAvailable = false,
    )

    private fun render(name: String, preferences: ThemePreferences, wallpaperAvailable: Boolean = true) {
        val store = SettingsStore(AndroidStorage(ApplicationProvider.getApplicationContext<Application>()))
        runBlocking {
            store.setThemeMode(preferences.mode)
            store.setThemeAccent(preferences.accent)
            store.setPureBlack(preferences.pureBlack)
            store.setWallpaperColors(preferences.wallpaperColors)
        }
        // DataStore writes off the main thread: wait for the stored theme, not for the view model.
        runBlocking { withTimeout(5_000) { store.theme.first { it == preferences } } }
        val viewModel = ThemeViewModel(store)
        compose.setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            SillonTheme(theme) {
                Surface(Modifier.testTag(ROOT), color = MaterialTheme.colorScheme.background) {
                    ThemeScreen(
                        onBack = {},
                        contentPadding = PaddingValues(),
                        viewModel = viewModel,
                        wallpaperAvailable = wallpaperAvailable,
                    )
                }
            }
        }
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(Canvas(full)) }
        val bounds = compose.onNodeWithTag(ROOT).fetchSemanticsNode().boundsInWindow
        val bitmap = Bitmap.createBitmap(
            full,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt().coerceAtMost(full.width - bounds.left.toInt()),
            bounds.height.toInt().coerceAtMost(full.height - bounds.top.toInt()),
        )
        File(out, "$name-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-w412dp-h1400dp-notnight-xhdpi")
class ThemeRendersEn : ThemeRenders("en")

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "fr-w412dp-h1400dp-notnight-xhdpi")
class ThemeRendersFr : ThemeRenders("fr")

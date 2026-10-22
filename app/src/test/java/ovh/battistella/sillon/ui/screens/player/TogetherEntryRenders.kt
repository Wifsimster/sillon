package ovh.battistella.sillon.ui.screens.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.R
import ovh.battistella.sillon.playback.PlayerUiState
import ovh.battistella.sillon.ui.screens.library.LibraryMenu
import ovh.battistella.sillon.ui.screens.library.LibraryMenuUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Listen together needs Jellyfin SyncPlay: on Plex neither the player button nor the menu entry is
 * shown. Renders the Plex player and menu with synthetic data, in English and French.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class TogetherEntryRenders(private val locale: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val togetherLabel get() = compose.activity.getString(R.string.together_open)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.waitForIdle()
    }

    /** Draws every window, the menu popup included, where it sits on screen. */
    private fun save(name: String) {
        compose.waitForIdle()
        val manager = Class.forName("android.view.WindowManagerGlobal")
        val global = manager.getMethod("getInstance").invoke(null)
        val views = manager.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<*>
        val decor = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val at = IntArray(2)
        compose.runOnUiThread {
            views.filterIsInstance<View>().filter { it.isShown }.forEach { view ->
                view.getLocationOnScreen(at)
                canvas.save()
                canvas.translate(at[0].toFloat(), at[1].toFloat())
                view.draw(canvas)
                canvas.restore()
            }
        }
        val dir = File("build/outputs/renders").apply { mkdirs() }
        File(dir, "$name-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Player(together: Boolean) = PlayerContent(
        state = PlayerUiState(
            isConnected = true,
            currentKey = "1",
            title = "Synthetic Song",
            artist = "Synthetic Artist",
            album = "Synthetic Album",
            isPlaying = true,
            positionMs = 74_000,
            durationMs = 241_000,
            hasNext = true,
            hasPrevious = true,
        ),
        startingRadio = false,
        mystery = null,
        mysteryStarting = false,
        keeping = false,
        keep = null,
        wear = null,
        controls = NoControls,
        onClose = {},
        onTogether = if (together) ({}) else null,
    )

    @Composable
    private fun Menu(together: Boolean) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        LibraryMenu(state = LibraryMenuUi(), together = together, onSelect = {}, onChangeServer = {}, onSignOut = {})
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_more)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun plexPlayerHasNoTogetherButton() {
        show { Player(together = false) }
        compose.onAllNodesWithContentDescription(togetherLabel).assertCountEquals(0)
        save("together-player-plex")
    }

    @Test
    fun jellyfinPlayerHasTogetherButton() {
        show { Player(together = true) }
        compose.onAllNodesWithContentDescription(togetherLabel).assertCountEquals(1)
    }

    @Test
    fun plexMenuHasNoTogetherEntry() {
        show { Menu(together = false) }
        openMenu()
        compose.onAllNodesWithText(togetherLabel).assertCountEquals(0)
        compose.onAllNodesWithText(compose.activity.getString(R.string.settings)).assertCountEquals(1)
        save("together-menu-plex")
    }

    @Test
    fun jellyfinMenuHasTogetherEntry() {
        show { Menu(together = true) }
        openMenu()
        compose.onAllNodesWithText(togetherLabel).assertCountEquals(1)
    }

    private object NoControls : PlayerControls {
        override fun radioFromCurrent() = Unit
        override fun reveal() = Unit
        override fun revealSeen() = Unit
        override fun keepSurprise() = Unit
        override fun anotherSurprise() = Unit
        override fun flipSide() = Unit
        override fun playPause() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeat() = Unit
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-night-xhdpi")
class TogetherEntryRendersEn : TogetherEntryRenders("en")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class TogetherEntryRendersFr : TogetherEntryRenders("fr")

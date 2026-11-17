package ovh.battistella.sillon.ui.screens.activity

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.data.activity.ActiveListen
import ovh.battistella.sillon.data.activity.ActivityState
import ovh.battistella.sillon.data.activity.ListenKind
import ovh.battistella.sillon.data.activity.ListenState
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ActivityRenders(private val locale: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val listens = listOf(
        ActiveListen(
            id = "a", listenerKey = "alice@phone", user = "alice", device = "Pixel 8 · Plexamp", title = "Blue Train",
            subtitle = "John Coltrane — Blue Train", artwork = null, state = ListenState.PLAYING, positionMs = 150_000,
            durationMs = 600_000, kind = ListenKind.MUSIC, transcoding = false, mine = false,
        ),
        ActiveListen(
            id = "b", listenerKey = "ben@tv", user = "ben", device = "Salon · Plex for Android (TV)", title = "Pilot",
            subtitle = "Severance — Season 1", artwork = null, state = ListenState.PAUSED, positionMs = 900_000,
            durationMs = 3_000_000, kind = ListenKind.VIDEO, transcoding = true, mine = false,
        ),
        ActiveListen(
            id = "c", listenerKey = "me@pixel", user = "damien", device = "Pixel · Sillon", title = "So What",
            subtitle = "Miles Davis — Kind of Blue", artwork = null, state = ListenState.PLAYING, positionMs = 400_000,
            durationMs = 540_000, kind = ListenKind.MUSIC, transcoding = false, mine = true,
        ),
    )

    private fun render(name: String, state: ActivityState, notify: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ActivityContent(state, notify, onNotify = {}, onBack = {}, contentPadding = PaddingValues())
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        val view = compose.activity.window.decorView
        val window = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(window))
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val bitmap = Bitmap.createBitmap(
            window,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt().coerceAtMost(window.width - bounds.left.toInt()),
            bounds.height.toInt().coerceAtMost(window.height - bounds.top.toInt()),
        )
        val dir = File("build/outputs/renders").apply { mkdirs() }
        File(dir, "activity-$name-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun listeners() {
        render("1-listeners", ActivityState.Live(listens), notify = true)
        compose.onNodeWithText("Blue Train").assertIsDisplayed()
        compose.onNodeWithText("alice · Pixel 8 · Plexamp").assertIsDisplayed()
    }

    @Test
    fun nobody() = render("2-nobody", ActivityState.Live(emptyList()))

    @Test
    fun notAllowed() = render("3-not-allowed", ActivityState.NotAllowed)

    @Test
    fun unreachable() = render("4-unreachable", ActivityState.Unreachable)
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-night-xhdpi")
class ActivityRendersEn : ActivityRenders("en")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class ActivityRendersFr : ActivityRenders("fr")

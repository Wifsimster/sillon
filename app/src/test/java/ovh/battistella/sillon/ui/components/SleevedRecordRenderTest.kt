package ovh.battistella.sillon.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.ui.screens.library.AlbumUi
import ovh.battistella.sillon.ui.screens.library.TrackCollectionScreen
import ovh.battistella.sillon.ui.screens.library.TrackUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
class SleevedRecordRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, dark: Boolean, playing: Boolean) {
        val tracks = (1..6).map { TrackUi("t$it", it, "Synthetic track $it", null, 240_000L) }
        val album = AlbumUi(title = "Demo Album", artist = "Demo Artist", year = 1977, artworkUrl = null, tracks = tracks)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SillonTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TrackCollectionScreen(
                        state = Load.Ready(album),
                        nowPlayingKey = "t2",
                        onBack = {},
                        onRetry = {},
                        onPlay = {},
                        onShuffle = {},
                        contentPadding = PaddingValues(),
                        record = true,
                        playing = playing,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/outputs/renders").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun restingLight() = render("album-record-1-resting-light", dark = false, playing = false)

    @Test
    fun playingLight() = render("album-record-2-playing-light", dark = false, playing = true)

    @Test
    fun restingDark() = render("album-record-3-resting-dark", dark = true, playing = false)

    @Test
    fun playingDark() = render("album-record-4-playing-dark", dark = true, playing = true)
}

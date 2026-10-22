package ovh.battistella.sillon.ui.screens.player

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.playback.AlbumSides
import ovh.battistella.sillon.playback.SideEnd
import ovh.battistella.sillon.playback.SideTrack
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.screens.library.AlbumUi
import ovh.battistella.sillon.ui.screens.library.TrackCollectionScreen
import ovh.battistella.sillon.ui.screens.library.TrackUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SideEndRenders(private val locale: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, advanceMs: Long = 1_200, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(advanceMs)
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
        File(dir, "$name-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun EndOfSide(reducedMotion: Boolean) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayerHeader(onClose = {}, onTogether = {}, startingRadio = false, radioEnabled = true, onRadio = {})
            SideEndRecord(reducedMotion, Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f))
            SideEndPanel(SideEnd(ended = "A", next = "B", nextIndex = 5), onFlip = {})
        }
    }

    @Test
    fun endOfSide() = render("side-end-1-arm-lifted") { EndOfSide(reducedMotion = false) }

    @Test
    fun endOfSideMidAnimation() = render("side-end-2-arm-lifting", advanceMs = 400) { EndOfSide(reducedMotion = false) }

    @Test
    fun endOfSideReducedMotion() = render("side-end-3-reduced-motion", advanceMs = 0) { EndOfSide(reducedMotion = true) }

    @Test
    fun albumWithSides() {
        val minutes = listOf(4, 5, 6, 3, 5, 4, 6, 3)
        val tracks = minutes.mapIndexed { i, m ->
            TrackUi("t$i", i + 1, "Synthetic track ${i + 1}", null, m * 60_000L)
        }
        val album = AlbumUi(
            title = "Demo Album",
            artist = "Demo Artist",
            year = 1977,
            artworkUrl = null,
            tracks = tracks,
            sides = AlbumSides.split(tracks.map { SideTrack(it.key, it.durationMs, null, it.title) }),
        )
        render("side-album") {
            TrackCollectionScreen(
                state = Load.Ready(album),
                nowPlayingKey = "t1",
                onBack = {},
                onRetry = {},
                onPlay = {},
                onShuffle = {},
                contentPadding = PaddingValues(),
                listenBySide = true,
                onListenBySideChange = {},
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-night-xhdpi")
class SideEndRendersEn : SideEndRenders("en")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class SideEndRendersFr : SideEndRenders("fr")

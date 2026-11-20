package ovh.battistella.sillon.ui.screens.library

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.data.sort.TrackOrder
import ovh.battistella.sillon.data.sort.TrackSort
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/** An album sorted by stars, with play counts, and the sort menu picks, from synthetic data, into `app/build/outputs/renders/`. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class TrackSortRenders {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val album = AlbumUi(
        title = "Demo Album",
        artist = "Demo Artist",
        year = 2026,
        artworkUrl = null,
        tracks = listOf(
            TrackUi("t1", 1, "Synthetic opener", null, 241_000, popularity = 0.4f, stars = 3f, plays = 4),
            TrackUi("t2", 2, "Never rated", null, 198_000, popularity = 0.1f, plays = 0),
            TrackUi("t3", 3, "The favourite", null, 305_000, popularity = 1f, stars = 5f, plays = 27),
            TrackUi("t4", 4, "The one I skip", null, 222_000, popularity = 0.6f, stars = 1f, plays = 2),
        ),
    )

    private val picked = mutableListOf<TrackOrder?>()

    private fun show(initial: TrackOrder?) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var order by remember { mutableStateOf(initial) }
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Screen(order) {
                        picked += it
                        order = it
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    @Composable
    private fun Screen(order: TrackOrder?, onOrder: (TrackOrder?) -> Unit) {
        TrackCollectionScreen(
            state = Load.Ready(album),
            nowPlayingKey = null,
            onBack = {},
            onRetry = {},
            onPlay = {},
            onShuffle = {},
            contentPadding = PaddingValues(),
            order = order,
            onOrder = onOrder,
        )
    }

    @Test
    fun sortedByStars() {
        show(TrackOrder(TrackSort.STARS, descending = true))
        save("sort-1-album-by-stars")
    }

    @Test
    fun pickingRatingSortsBestFirstThenReverses() {
        show(null)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Trier").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Note").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Trier").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Note").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(TrackOrder(TrackSort.STARS, descending = true), TrackOrder(TrackSort.STARS, descending = false)),
            picked,
        )
    }

    private fun save(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/outputs/renders").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

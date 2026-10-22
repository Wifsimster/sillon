package ovh.battistella.sillon.ui.screens.player

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.data.rating.RatingBook
import ovh.battistella.sillon.data.rating.RatingSend
import ovh.battistella.sillon.playback.PlayerUiState
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.screens.library.AlbumUi
import ovh.battistella.sillon.ui.screens.library.TrackCollectionScreen
import ovh.battistella.sillon.ui.screens.library.TrackUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/** The player with its stars, and an album with a 1-star track, from synthetic data, into `app/build/outputs/renders/`. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class RatingRenders(private val locale: String, private val rateLabel: String, private val clearLabel: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Controls : PlayerControls {
        val rated = mutableListOf<Int?>()
        override fun rate(stars: Int?) {
            rated += stars
        }
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

    private val state = PlayerUiState(
        isConnected = true,
        currentKey = "t1",
        title = "Synthetic Track",
        artist = "Demo Artist",
        album = "Demo Album",
        isPlaying = true,
        positionMs = 64_000,
        durationMs = 215_000,
        hasNext = true,
        hasPrevious = true,
    )

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun save(name: String) {
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

    private fun player(rating: RatingUi, controls: PlayerControls = Controls()) = show {
        PlayerContent(
            state = state,
            startingRadio = false,
            mystery = null,
            mysteryStarting = false,
            keeping = false,
            keep = null,
            wear = null,
            controls = controls,
            onClose = {},
            onTogether = {},
            rating = rating,
        )
    }

    @Test
    fun rated4() {
        player(RatingUi("t1", 4))
        save("rating-1-plex-4-stars")
    }

    @Test
    fun unrated() {
        player(RatingUi("t1", null))
        save("rating-2-unrated")
    }

    @Test
    fun oneStar() {
        player(RatingUi("t1", 1))
        save("rating-3-one-star")
    }

    @Test
    fun waitingForNetwork() {
        player(RatingUi("t1", 5, RatingSend.WAITING_FOR_NETWORK))
        save("rating-4-offline")
    }

    @Test
    fun failed() {
        player(RatingUi("t1", 5, RatingSend.FAILED))
        save("rating-5-failed")
    }

    @Test
    fun jellyfinUnrated() {
        player(RatingUi("t1", null, jellyfin = true))
        save("rating-6-jellyfin-unrated")
    }

    @Test
    fun jellyfin() {
        player(RatingUi("t1", 5, jellyfin = true))
        save("rating-6-jellyfin-5-stars")
        compose.onNodeWithContentDescription(rateLabel.format(3)).assertIsNotEnabled()
    }

    @Test
    fun touchingAStarRatesTouchingItAgainClears() {
        val controls = Controls()
        player(RatingUi("t1", 4), controls)

        compose.onNodeWithContentDescription(rateLabel.format(2)).performClick()
        compose.onNodeWithContentDescription(clearLabel).performClick()

        assertEquals(listOf(2, null), controls.rated)
    }

    @Test
    fun albumWithAOneStarTrack() {
        val tracks = listOf(
            TrackUi("t1", 1, "Synthetic opener", null, 241_000, stars = 5f),
            TrackUi("t2", 2, "The one I skip", null, 198_000, stars = 1f),
            TrackUi("t3", 3, "Synthetic ballad", null, 305_000),
            TrackUi("t4", 4, "Rated here, not yet sent", null, 222_000),
        )
        val album = AlbumUi(title = "Demo Album", artist = "Demo Artist", year = 1977, artworkUrl = null, tracks = tracks, sides = emptyList())
        show {
            TrackCollectionScreen(
                state = Load.Ready(album),
                nowPlayingKey = "t1",
                onBack = {},
                onRetry = {},
                onPlay = {},
                onShuffle = {},
                contentPadding = PaddingValues(),
                ratings = RatingBook().rate("t4", 1),
                skipOneStar = true,
            )
        }
        save("rating-7-album-one-star")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-night-xhdpi")
class RatingRendersEn : RatingRenders("en", "Rate %d stars", "Clear the rating")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class RatingRendersFr : RatingRenders("fr", "Noter %d étoiles", "Effacer la note")

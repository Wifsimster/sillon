package ovh.battistella.sillon.ui.screens.mystery

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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
import ovh.battistella.sillon.R
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlayerUiState
import ovh.battistella.sillon.playback.mystery.KeepResult
import ovh.battistella.sillon.playback.mystery.Mystery
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysteryTrack
import ovh.battistella.sillon.playback.mystery.SleeveArt
import ovh.battistella.sillon.ui.screens.player.PlayerContent
import ovh.battistella.sillon.ui.screens.player.PlayerControls
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Renders the mystery sleeve, the masked player and the reveal with synthetic data (no server, no
 * real artwork), in English and French, and writes the PNGs to `app/build/mystery-renders/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class MysteryRenderTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/mystery-renders").apply { mkdirs() }
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun fakeArtwork() {
        val cover = syntheticCover()
        val loader = ImageLoader.Builder(context)
            .allowHardware(false)
            .interceptorDispatcher(Dispatchers.Main.immediate)
            .components {
                add(Interceptor { chain -> SuccessResult(BitmapDrawable(context.resources, cover), chain.request, DataSource.MEMORY) })
            }
            .build()
        Coil.setImageLoader(loader)
    }

    @After
    fun resetArtwork() = Coil.reset()

    @Test
    fun `renders in English`() = renderAll("en")

    @Test
    @Config(qualifiers = "+fr")
    fun `renders in French`() = renderAll("fr")

    private fun renderAll(lang: String) {
        // The sleeve the notification, the lock screen and Android Auto show.
        val art = SleeveArt(context).png
        File(out, "sleeve-notification-$lang.png").writeBytes(art)
        assertTrue(BitmapFactory.decodeByteArray(art, 0, art.size) != null)
        save(capture { MysteryCover(Modifier.size(280.dp)) }, "sleeve-app-$lang.png")

        val hidden = PlayerUiState(
            isConnected = true,
            currentKey = MysteryMask.HIDDEN_KEY,
            title = context.getString(R.string.mystery_track_label, 1, 2),
            artist = context.getString(R.string.mystery_album),
            artworkUri = null,
            isPlaying = true,
            positionMs = 74_000,
            durationMs = 241_000,
            hasNext = true,
            hasPrevious = true,
        )
        save(capture { MysteryPlayer(hidden, MYSTERY) }, "player-hidden-$lang.png")
        save(capture { MysteryPlayer(hidden, MYSTERY.copy(offline = true)) }, "player-hidden-offline-$lang.png")
        // A postcard of the hidden album would give it away.
        compose.onAllNodesWithContentDescription(context.getString(R.string.share_card)).assertCountEquals(0)

        val shown = hidden.copy(
            currentKey = "3",
            title = "Synthetic Song",
            artist = "Synthetic Artist",
            album = "Synthetic Album",
            artworkUri = COVER,
            albumKey = null,
        )
        val revealed = MYSTERY.copy(revealed = true, left = setOf("1", "2", "3"))
        compose.mainClock.autoAdvance = false
        save(capture(frameMs = 700) { MysteryPlayer(shown, revealed) }, "reveal-sliding-$lang.png")
        compose.mainClock.autoAdvance = true
        save(capture { MysteryPlayer(shown, revealed.copy(revealSeen = true)) }, "reveal-done-$lang.png")
        compose.onAllNodesWithContentDescription(context.getString(R.string.share_card)).assertCountEquals(1)
        save(capture { MysteryPlayer(shown, revealed.copy(revealSeen = true), keep = KeepResult.KEPT) }, "reveal-kept-$lang.png")
    }

    @Composable
    private fun MysteryPlayer(state: PlayerUiState, mystery: Mystery, keep: KeepResult? = null) = PlayerContent(
        state = state,
        startingRadio = false,
        mystery = mystery,
        mysteryStarting = false,
        keeping = false,
        keep = keep,
        wear = null,
        controls = NoControls,
        onClose = {},
        onTogether = {},
    )

    private var screen by mutableStateOf<Screen?>(null)

    private fun capture(frameMs: Long? = null, content: @Composable () -> Unit): Bitmap {
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
        frameMs?.let { compose.mainClock.advanceTimeBy(it) }
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

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    private companion object {
        const val ROOT = "root"
        const val COVER = "synthetic://cover"

        val MYSTERY = Mystery(
            albumKey = "synthetic-album",
            tracks = (1..6).map { n ->
                MysteryTrack(
                    PlayableTrack(
                        ratingKey = "$n",
                        streamUrl = "https://server.example/track/$n",
                        title = "Synthetic Song $n",
                        artist = "Synthetic Artist",
                        album = "Synthetic Album",
                        trackNumber = n,
                        durationMs = 241_000,
                        artworkUrl = COVER,
                    ),
                    side = if (n <= 3) 1 else 2,
                    number = if (n <= 3) n else n - 3,
                )
            },
        )

        /** An abstract cover drawn in code: no real artwork ends up in the renders. */
        fun syntheticCover(): Bitmap {
            val size = 600
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFF1E3A5F.toInt(), 0xFF0B1422.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            paint.shader = null
            paint.color = 0xFFE8A33D.toInt()
            canvas.drawCircle(size * 0.62f, size * 0.42f, size * 0.24f, paint)
            paint.color = 0xFFF6EFE4.toInt()
            paint.textSize = 54f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("SYNTHETIC", 60f, 120f, paint)
            return bitmap
        }
    }
}

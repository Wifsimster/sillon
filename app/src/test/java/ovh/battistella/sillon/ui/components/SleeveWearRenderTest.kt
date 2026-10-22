package ovh.battistella.sillon.ui.components

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import ovh.battistella.sillon.data.wear.SleeveWear
import ovh.battistella.sillon.ui.screens.library.AlbumUi
import ovh.battistella.sillon.ui.screens.library.TileGrid
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.screens.library.TrackCollectionScreen
import ovh.battistella.sillon.ui.screens.library.TrackUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File
import kotlin.math.abs

/**
 * Renders sleeve wear on synthetic covers (no real artwork, no server) and writes the PNGs to
 * `app/build/sleeve-wear-renders/` so a reviewer can look at them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class SleeveWearRenderTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/sleeve-wear-renders").apply { mkdirs() }

    @Before
    fun fakeArtwork() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val covers = mapOf(DARK to syntheticCover(dark = true), LIGHT to syntheticCover(dark = false))
        val loader = ImageLoader.Builder(context)
            .allowHardware(false)
            .interceptorDispatcher(Dispatchers.Main.immediate)
            .components {
                add(
                    Interceptor { chain ->
                        val bitmap = covers.getValue(chain.request.data as String)
                        SuccessResult(BitmapDrawable(context.resources, bitmap), chain.request, DataSource.MEMORY)
                    },
                )
            }
            .build()
        Coil.setImageLoader(loader)
    }

    @After
    fun resetArtwork() = Coil.reset()

    @Test
    fun `each wear level adds to the cover without hiding it`() {
        val plain = capture { Artwork(DARK, Modifier.size(200.dp)) }
        val diffs = LEVELS.map { level ->
            val worn = capture { Artwork(DARK, Modifier.size(200.dp), wear = Wear(level, SEED)) }
            save(worn, "tile-dark-level-$level.png")
            meanDiff(plain, worn)
        }
        save(plain, "tile-dark-off.png")

        assertTrue("sealed sheen is visible: $diffs", diffs[0] > 0.0)
        (2..SleeveWear.MAX).forEach { level ->
            assertTrue("level $level wears more than ${level - 1}: $diffs", diffs[level] > diffs[level - 1])
        }
        assertTrue("the heaviest wear leaves the cover readable: $diffs", diffs.last() < MAX_MEAN_DIFF)
    }

    @Test
    fun `album grid shows every level side by side`() {
        listOf(DARK to "dark", LIGHT to "light").forEach { (cover, name) ->
            listOf(false, true).forEach { darkTheme ->
                val tiles = listOf(null) + LEVELS
                val image = capture(darkTheme) {
                    TileGrid(
                        tiles = tiles.map { level ->
                            TileUi(key = tileKey(level), title = label(level), subtitle = "Synthetic Artist", artworkUrl = cover)
                        },
                        onClick = {},
                        wear = { key -> tiles.firstOrNull { tileKey(it) == key }?.let { Wear(it, SEED) } },
                    )
                }
                save(image, "grid-$name-cover-${if (darkTheme) "dark" else "light"}-theme.png")
            }
        }
    }

    @Test
    fun `album page before and after`() {
        val pages = (listOf(null) + LEVELS).associateWith { level ->
            capture {
                TrackCollectionScreen(
                    state = Load.Ready(ALBUM),
                    nowPlayingKey = null,
                    onBack = {},
                    onRetry = {},
                    onPlay = {},
                    onShuffle = {},
                    contentPadding = PaddingValues(),
                    wear = level?.let { Wear(it, SEED) },
                )
            }
        }
        pages.forEach { (level, page) -> save(page, "album-page-${level?.let { "level-$it" } ?: "off"}.png") }
        save(sideBySide(pages.getValue(null), pages.getValue(SleeveWear.MAX)), "album-page-before-after.png")
    }

    private var screen by mutableStateOf<Screen?>(null)

    private fun capture(darkTheme: Boolean = false, content: @Composable () -> Unit): Bitmap {
        if (screen == null) {
            compose.setContent {
                screen?.let { current ->
                    SillonTheme(darkTheme = current.darkTheme) {
                        Surface(Modifier.testTag(ROOT), color = MaterialTheme.colorScheme.background) {
                            key(current) { current.content() }
                        }
                    }
                }
            }
        }
        screen = Screen(darkTheme, content)
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(Canvas(full)) }
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

    private class Screen(val darkTheme: Boolean, val content: @Composable () -> Unit)

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    private companion object {
        const val ROOT = "root"
        const val DARK = "synthetic://cover/dark"
        const val LIGHT = "synthetic://cover/light"
        const val SEED = 0x51110
        const val MAX_MEAN_DIFF = 40.0
        val LEVELS = (SleeveWear.SEALED..SleeveWear.MAX).toList()

        val ALBUM = AlbumUi(
            title = "Synthetic Album",
            artist = "Synthetic Artist",
            year = 1979,
            artworkUrl = DARK,
            tracks = (1..4).map { TrackUi("t$it", it, "Track $it", null, 200_000L + it * 15_000L) },
        )

        fun tileKey(level: Int?) = level?.let { "level-$it" } ?: "off"

        fun label(level: Int?) = when (level) {
            null -> "Wear off"
            SleeveWear.SEALED -> "0 · sealed"
            else -> "Level $level"
        }

        /** An abstract cover drawn in code: no real artwork ends up in the renders. */
        fun syntheticCover(dark: Boolean): Bitmap {
            val size = 600
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val (top, bottom) = if (dark) 0xFF1E3A5F.toInt() to 0xFF0B1422.toInt() else 0xFFF1E9D8.toInt() to 0xFFD9CDB4.toInt()
            paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            paint.shader = null
            paint.color = if (dark) 0xFFE8A33D.toInt() else 0xFFC2552B.toInt()
            canvas.drawCircle(size * 0.62f, size * 0.42f, size * 0.24f, paint)
            paint.color = if (dark) 0xFFF6EFE4.toInt() else 0xFF15120F.toInt()
            repeat(5) { i -> canvas.drawRect(60f, 380f + i * 22f, 60f + 300f - i * 40f, 390f + i * 22f, paint) }
            paint.textSize = 54f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("SYNTHETIC", 60f, 120f, paint)
            return bitmap
        }

        fun meanDiff(a: Bitmap, b: Bitmap): Double {
            var total = 0L
            for (y in 0 until a.height) for (x in 0 until a.width) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                total += abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) +
                    abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) +
                    abs((p and 0xFF) - (q and 0xFF))
            }
            return total / (a.width * a.height * 3.0)
        }

        fun sideBySide(left: Bitmap, right: Bitmap): Bitmap {
            val gap = 24
            val result = Bitmap.createBitmap(left.width + gap + right.width, maxOf(left.height, right.height), Bitmap.Config.ARGB_8888)
            Canvas(result).apply {
                drawColor(0xFF15120F.toInt())
                drawBitmap(left, 0f, 0f, null)
                drawBitmap(right, (left.width + gap).toFloat(), 0f, null)
            }
            return result
        }
    }
}

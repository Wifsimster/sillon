package ovh.battistella.sillon.ui.share

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
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
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Draws every card on a synthetic cover (no real artwork, no server) into `app/build/outputs/renders/`
 * so a reviewer can look at them, and the share sheet in both languages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class CardRenders {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val out = File("build/outputs/renders").apply { mkdirs() }
    private val renderer = CardRenderer(context)

    private val album = CardText(
        title = "Synthetic Summer",
        artist = "The Placeholder Band",
        detail = "1979",
        stars = 4.5f,
        note = "“Le disque de nos étés, en boucle dans la voiture.”",
        lyrics = listOf("We drove all night with the windows down", "Singing a song that nobody knows"),
    )

    private val bare = CardText(title = "Untitled Track Number Nine of an Album With an Extraordinarily Long Name, Deluxe Edition", artist = "Synthetic Artist")

    @Test
    fun `every template in both formats`() {
        val cover = syntheticCover()
        val palette = CardPalette.of(cover)
        CardTemplate.entries.forEach { template ->
            CardFormat.entries.forEach { format ->
                val bitmap = renderer.render(renderer.layout(format, template, album), cover, palette)
                assertTrue(bitmap.width == format.width && bitmap.height == format.height)
                save(bitmap, "card-${template.name.lowercase()}-${format.name.lowercase()}.png")
            }
        }
    }

    @Test
    fun `long title, no year, no rating, no cover`() {
        CardTemplate.entries.forEach { template ->
            val bitmap = renderer.render(renderer.layout(CardFormat.SQUARE, template, bare), null, CardPalette.Default)
            save(bitmap, "card-${template.name.lowercase()}-bare.png")
        }
    }

    @Test
    fun `the share sheet`() {
        val cover = syntheticCover()
        val preview = renderer.render(renderer.layout(CardFormat.SQUARE, CardTemplate.VINYL, album), cover, CardPalette.of(cover), 0.5f)
        val ui = ShareCardUi(
            source = ShareSource(ShareSource.Kind.TRACK, "t1", "Synthetic Summer", "The Placeholder Band", "Synthetic Album"),
            note = "Le disque de nos étés",
            stars = 4.5f,
            showStars = true,
            lyrics = listOf("We drove all night with the windows down", "Singing a song that nobody knows", "And the radio played"),
            chosenLyrics = listOf(0, 1),
            preview = preview,
            loading = false,
        )
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ShareCardContent(ui, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val window = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(window)) }
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInWindow
        save(
            Bitmap.createBitmap(
                window, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt().coerceAtMost(window.width - bounds.left.toInt()),
                bounds.height.toInt().coerceAtMost(window.height - bounds.top.toInt()),
            ),
            "share-sheet.png",
        )
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class RegisterActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    /** An abstract cover drawn in code: no real artwork ends up in the renders. */
    private fun syntheticCover(): Bitmap {
        val size = 900
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFF2E5E7E.toInt(), 0xFF0E1B2A.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = 0xFFE8A33D.toInt()
        canvas.drawCircle(size * 0.64f, size * 0.38f, size * 0.2f, paint)
        paint.color = 0xFFC2552B.toInt()
        repeat(6) { i -> canvas.drawRect(80f, 560f + i * 40f, 80f + 520f - i * 70f, 580f + i * 40f, paint) }
        return bitmap
    }
}

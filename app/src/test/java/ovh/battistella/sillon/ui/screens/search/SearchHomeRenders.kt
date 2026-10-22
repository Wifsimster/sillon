package ovh.battistella.sillon.ui.screens.search

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
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
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Renders the empty search page with synthetic albums (no server, no real library), in English and
 * French, into `app/build/search-home-renders/`, and checks what TalkBack hears and can do.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class SearchHomeRenders {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/search-home-renders").apply { mkdirs() }
    private var page by mutableStateOf<Page?>(null)
    private val opened = mutableListOf<String>()
    private val played = mutableListOf<String>()

    @Before
    fun fakeArtwork() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val loader = ImageLoader.Builder(context)
            .allowHardware(false)
            .interceptorDispatcher(Dispatchers.Main.immediate)
            .components {
                add(
                    Interceptor { chain ->
                        val hue = (chain.request.data as String).hashCode().mod(360).toFloat()
                        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f)))
                        SuccessResult(BitmapDrawable(context.resources, bitmap), chain.request, DataSource.MEMORY)
                    },
                )
            }
            .build()
        Coil.setImageLoader(loader)
    }

    @Test
    fun `renders in English`() = renderAll("en")

    @Test
    @Config(qualifiers = "+fr")
    fun `renders in French`() = renderAll("fr")

    @Test
    fun `TalkBack reads each album and offers open and play`() {
        show(Page("online", SearchHomeUi(recent = albums("r", 3), classics = albums("c", 3))))
        val node = compose.onNodeWithContentDescription("Album r1, Demo Artist r1")
        val actions = node.fetchSemanticsNode().config
        assertEquals("Open the album", actions[SemanticsActions.OnClick].label)
        assertEquals("Play the album", actions[SemanticsActions.OnLongClick].label)
        node.performClick()
        node.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf("r1"), opened)
        assertEquals(listOf("r1"), played)
        compose.onNodeWithText("Recently played").assertExists()
        compose.onNodeWithText("Your classics").assertExists()
    }

    @Test
    fun `offline with nothing downloaded says so`() {
        show(Page("offline-empty", SearchHomeUi(online = false)))
        compose.onNodeWithText("You are offline", substring = true).assertExists()
    }

    private fun renderAll(lang: String) {
        listOf(
            Page("online", SearchHomeUi(recent = albums("r", 6), classics = albums("c", 6))),
            Page("long-names", SearchHomeUi(recent = longNames(), classics = albums("c", 2))),
            Page("offline", SearchHomeUi(recent = albums("r", 2), classics = albums("c", 1), online = false)),
            Page("offline-empty", SearchHomeUi(online = false)),
            Page("no-wear", SearchHomeUi(recent = albums("r", 4), classicsNeedWear = true)),
            Page("first-run", SearchHomeUi()),
        ).forEach {
            show(it)
            save(capture(), "search-home-${it.name}-$lang.png")
        }
    }

    private fun show(next: Page) {
        if (page == null) {
            compose.setContent {
                val current = page ?: return@setContent
                key(current.name) {
                    SillonTheme(darkTheme = false) {
                        Surface(Modifier.fillMaxSize().testTag(ROOT)) {
                            Column {
                                SearchHome(
                                    current.ui,
                                    onAlbum = { opened += it },
                                    onPlayAlbum = { played += it },
                                    contentPadding = PaddingValues(),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.runOnUiThread { page = next }
        compose.waitForIdle()
    }

    private fun albums(prefix: String, count: Int) = (1..count).map {
        TileUi("$prefix$it", "Album $prefix$it", "Demo Artist $prefix$it", "demo:$prefix$it")
    }

    private fun longNames() = listOf(
        TileUi("l1", "An Extremely Long Album Title That Never Seems To End, Deluxe Remastered Edition", "Orchestre Philharmonique de Démonstration et Chœurs", "demo:l1"),
        TileUi("l2", "Été", null, "demo:l2"),
        TileUi("l3", "X", "Y", null),
    )

    private fun capture(): Bitmap {
        compose.waitForIdle()
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

    private class Page(val name: String, val ui: SearchHomeUi)

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

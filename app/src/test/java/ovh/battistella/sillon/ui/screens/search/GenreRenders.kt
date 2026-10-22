package ovh.battistella.sillon.ui.screens.search

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
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
import ovh.battistella.sillon.data.genres.Genres
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * Renders the genres of the search page, a genre page and the full list with synthetic genres (no server,
 * no real library), in English and French, into `app/build/genre-renders/`, and checks what TalkBack hears.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class GenreRenders {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterActivity()).around(compose)

    private val out = File("build/genre-renders").apply { mkdirs() }
    private var page by mutableStateOf<Page?>(null)
    private val opened = mutableListOf<String>()

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
    fun `TalkBack reads a genre tile with its album count and opens it`() {
        show(Page("home") { SearchHome(homeUi(online = true), {}, {}, PaddingValues(), onGenre = { opened += it }) })
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("All genres"))
        val node = compose.onNodeWithContentDescription("Heavy Metal, 1 album")
        assertEquals("Open the genre", node.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        node.performClick()
        assertEquals(listOf("heavy metal"), opened)
    }

    @Test
    fun `TalkBack offers the artist and the shuffle on a genre page`() {
        show(Page("genre") { GenrePage(genreUi(online = true), false, false, { opened += it }, { opened += "artist:$it" }, {}, {}, PaddingValues()) })
        val artist = compose.onNodeWithContentDescription("Demo Artist 1")
        assertEquals("Open the artist", artist.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        artist.performClick()
        compose.onNodeWithText("Shuffle the genre").assertExists()
        assertEquals(listOf("artist:ar1"), opened)
    }

    private fun renderAll(lang: String) {
        listOf(
            Page("home") { SearchHome(homeUi(online = true), {}, {}, PaddingValues()) },
            Page("home-offline") { SearchHome(homeUi(online = false), {}, {}, PaddingValues()) },
            Page("genre") { GenrePage(genreUi(online = true), false, false, {}, {}, {}, {}, PaddingValues()) },
            Page("genre-offline") { GenrePage(genreUi(online = false), false, false, {}, {}, {}, {}, PaddingValues()) },
            // After a shuffle that found no track to play.
            Page("genre-nothing-to-play") { GenrePage(genreUi(online = true), false, true, {}, {}, {}, {}, PaddingValues()) },
            Page("genre-missing") { GenrePage(GenrePageUi(found = false, loading = false), false, false, {}, {}, {}, {}, PaddingValues()) },
            Page("all") { GenreList(GenreListUi(genres(), loading = false), {}, {}, PaddingValues()) },
            Page("all-offline-none") { GenreList(GenreListUi(online = false, loading = false), {}, {}, PaddingValues()) },
        ).forEach {
            show(it)
            save(capture(), "genres-${it.name}-$lang.png")
            if (it.name == "home") {
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(if (lang == "fr") "Tous les genres" else "All genres"))
                save(capture(), "genres-home-scrolled-$lang.png")
            }
        }
    }

    /** Synthetic genres, with a long one and an accented one. */
    private fun genres(): List<GenreTileUi> = listOf(
        "Rock" to 128, "Jazz" to 64, "Électronique" to 41, "Metal" to 33, "Hip-Hop" to 20, "Classique" to 18,
        "Ambient" to 12, "Folk" to 9, "Soul" to 7, "Reggae" to 5, "Progressive Psychedelic Space Rock" to 3, "Blues" to 2,
        "Heavy Metal" to 1, "Chanson" to 1,
    ).map { (label, count) -> GenreTileUi(Genres.normalize(label), label, count) }

    private fun offlineGenres(): List<GenreTileUi> = listOf("Rock" to 4, "Jazz" to 2, "Électronique" to 1)
        .map { (label, count) -> GenreTileUi(Genres.normalize(label), label, count) }

    private fun homeUi(online: Boolean) = SearchHomeUi(
        recent = albums("r", 4),
        classics = albums("c", 4),
        online = online,
        // Online: twelve tiles ending on "Heavy Metal, 1 album", and more behind "All genres".
        // Offline: only the genres of downloaded albums, counted on those albums.
        genres = if (online) genres().filter { it.label != "Blues" }.take(Genres.VISIBLE) else offlineGenres(),
        moreGenres = online,
    )

    private fun genreUi(online: Boolean) = GenrePageUi(
        label = "Rock",
        artists = if (online) (1..5).map { TileUi("ar$it", "Demo Artist $it", null, "demo:ar$it") } else emptyList(),
        albums = albums("a", if (online) 8 else 2),
        online = online,
        loading = false,
    )

    private fun albums(prefix: String, count: Int) = (1..count).map {
        TileUi("$prefix$it", "Album $prefix$it", "Demo Artist $prefix$it", "demo:$prefix$it")
    }

    private fun show(next: Page) {
        if (page == null) {
            compose.setContent {
                val current = page ?: return@setContent
                key(current.name) {
                    SillonTheme(darkTheme = false) {
                        Surface(Modifier.fillMaxSize().testTag(ROOT)) { current.content() }
                    }
                }
            }
        }
        compose.runOnUiThread { page = next }
        compose.waitForIdle()
    }

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

    private class Page(val name: String, val content: @Composable () -> Unit)

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

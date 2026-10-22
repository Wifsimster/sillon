package ovh.battistella.sillon.ui.screens.player

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import ovh.battistella.sillon.sonos.Output
import ovh.battistella.sillon.sonos.OutputNotice
import ovh.battistella.sillon.sonos.SonosGroup
import ovh.battistella.sillon.sonos.SonosZone
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

/**
 * The Sonos output picker, the player header and the banner, with synthetic speakers, into
 * `app/build/outputs/renders/sonos-*.png`. "Before" is the header as on `main` (no output button).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SonosRenders(private val locale: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val salon = SonosZone("RINCON_A", "Salon", "http://192.168.1.21:1400")
    private val kitchen = SonosZone("RINCON_B", "Cuisine", "http://192.168.1.22:1400")
    private val salonGroup = SonosGroup("RINCON_A:1", salon, listOf(salon))
    private val both = SonosGroup("RINCON_B:2", kitchen, listOf(kitchen, salon))
    private val bedroom = SonosZone("RINCON_C", "Chambre", "http://192.168.1.23:1400").let { SonosGroup("RINCON_C:3", it, listOf(it)) }

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(600)
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
        File(dir, "sonos-$name-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Header(output: Output?, notice: OutputNotice? = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (output == null) {
                PlayerHeader(onClose = {}, onTogether = {}, startingRadio = false, radioEnabled = true, onRadio = {})
            } else {
                PlayerHeader(
                    onClose = {},
                    onTogether = {},
                    startingRadio = false,
                    radioEnabled = true,
                    onRadio = {},
                    onOutput = {},
                    outputName = (output as? Output.OnSonos)?.group?.name,
                )
                OutputBanner(output, notice, onOpen = {}, onNoticeSeen = {})
            }
        }
    }

    @Composable
    private fun Sheet(output: Output, groups: List<SonosGroup>, scanning: Boolean = false) =
        OutputSheetContent(output, groups, scanning, block = null, reducedMotion = true, onPhone = {}, onGroup = {}, onRescan = {})

    @Test
    fun headerBefore() = render("1-header-before") { Header(output = null) }

    @Test
    fun headerOnPhone() = render("2-header-after-phone") { Header(Output.Phone) }

    @Test
    fun sheetSearching() = render("3-sheet-searching") { Sheet(Output.Phone, emptyList(), scanning = true) }

    @Test
    fun sheetFound() = render("4-sheet-found") { Sheet(Output.Phone, listOf(salonGroup, both, bedroom)) }

    @Test
    fun sheetConnecting() = render("5-sheet-connecting") { Sheet(Output.Connecting(salonGroup), listOf(salonGroup, both, bedroom)) }

    @Test
    fun headerOnSonos() = render("6-header-on-sonos") { Header(Output.OnSonos(salonGroup)) }

    @Test
    fun plexRefused() = render("7-notice-plex-token") { Header(Output.Phone, OutputNotice.PLEX_TOKEN_REFUSED) }

    @Test
    fun jellyfinRefused() = render("8-notice-jellyfin-token") { Header(Output.Phone, OutputNotice.JELLYFIN_TOKEN_REFUSED) }

    @Test
    fun unreachable() = render("9-notice-unreachable") { Header(Output.Phone, OutputNotice.UNREACHABLE) }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-night-xhdpi")
class SonosRendersEn : SonosRenders("en")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class SonosRendersFr : SonosRenders("fr")

package ovh.battistella.sillon.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.i18n.stringsFor
import ovh.battistella.sillon.desktop.ui.SillonApp
import ovh.battistella.sillon.desktop.ui.handleShortcut
import ovh.battistella.sillon.desktop.ui.rememberAppState
import ovh.battistella.sillon.desktop.ui.theme.SillonTheme
import java.awt.Dimension
import java.io.File

fun main() {
    val graph = AppGraph()
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.client })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(graph.storage.privateDir, "images").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }
    val icon = AppGraph::class.java.getResourceAsStream("/sillon.png")?.use {
        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
    }
    val strings = stringsFor()
    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        val app = rememberAppState(graph)
        Window(
            onCloseRequest = {
                graph.close()
                exitApplication()
            },
            state = windowState,
            title = "Sillon",
            icon = icon,
            onPreviewKeyEvent = { handleShortcut(it, app) },
        ) {
            window.minimumSize = Dimension(960, 600)
            CompositionLocalProvider(LocalStrings provides strings) {
                SillonTheme {
                    SillonApp(app)
                }
            }
        }
    }
}

private const val IMAGE_CACHE_BYTES = 256L * 1024 * 1024

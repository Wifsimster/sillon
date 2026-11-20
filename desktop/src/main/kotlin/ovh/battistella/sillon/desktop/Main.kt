package ovh.battistella.sillon.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import kotlinx.coroutines.runBlocking
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image
import ovh.battistella.sillon.data.settings.ThemePreferences
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.i18n.stringsFor
import ovh.battistella.sillon.desktop.platform.DesktopPrefs
import ovh.battistella.sillon.desktop.platform.DesktopStorage
import ovh.battistella.sillon.desktop.platform.SingleInstance
import ovh.battistella.sillon.desktop.platform.WindowChrome
import ovh.battistella.sillon.desktop.platform.WindowPlacement as SavedPlacement
import ovh.battistella.sillon.desktop.ui.LocalWindowFrame
import ovh.battistella.sillon.desktop.ui.SillonApp
import ovh.battistella.sillon.desktop.ui.WindowFrame
import ovh.battistella.sillon.desktop.ui.handleShortcut
import ovh.battistella.sillon.desktop.ui.rememberAppState
import ovh.battistella.sillon.desktop.ui.theme.SillonTheme
import ovh.battistella.sillon.desktop.update.UpdateInstaller
import ovh.battistella.sillon.desktop.update.Versions
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--self-test") exitProcess(SelfTest.run(args.getOrNull(1)?.let(::File)))
    if (args.firstOrNull() == "--install-update" && args.size >= 2) exitProcess(SelfTest.installUpdate(File(args[1]), args.getOrNull(2)))
    val storage = DesktopStorage()
    val instance = SingleInstance.acquire(File(storage.privateDir, "sillon.lock")) ?: return
    val updates = UpdateInstaller(File(storage.privateDir, "updates"), AppGraph.runningVersion())
    if (Versions.isRelease(AppGraph.runningVersion()) && UpdateInstaller.installAtStartup(updates, DesktopPrefs(storage))) {
        instance.release()
        exitProcess(0)
    }
    val graph = AppGraph(storage)
    val startMaximized = System.getProperty("sillon.window.start") == "maximized"
    val saved = runBlocking { runCatching { graph.prefs.window() }.getOrNull() }
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
        val windowState = rememberWindowState(
            placement = if (saved?.maximized == true || startMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = visiblePosition(saved),
            size = DpSize((saved?.width ?: 1280).coerceAtLeast(MIN_WIDTH).dp, (saved?.height ?: 820).coerceAtLeast(MIN_HEIGHT).dp),
        )
        val app = rememberAppState(graph)
        val customFrame = remember { WindowChrome.customFrame() }
        val close = {
            runBlocking { runCatching { graph.prefs.setWindow(placementOf(windowState)) } }
            graph.close()
            instance.release()
            exitApplication()
        }
        app.exit = close
        Window(
            onCloseRequest = close,
            state = windowState,
            title = "Sillon",
            icon = icon,
            undecorated = customFrame,
            onPreviewKeyEvent = { handleShortcut(it, app) },
        ) {
            window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
            val frame = remember(window) {
                WindowFrame(
                    custom = customFrame,
                    isMaximized = { windowState.placement == WindowPlacement.Maximized },
                    minimize = { windowState.isMinimized = true },
                    toggleMaximize = {
                        if (windowState.placement == WindowPlacement.Maximized) {
                            windowState.placement = WindowPlacement.Floating
                        } else {
                            window.maximizedBounds = WindowChrome.workArea(window)
                            windowState.placement = WindowPlacement.Maximized
                        }
                    },
                    close = close,
                    draggable = { modifier, content -> WindowDraggableArea(modifier) { content() } },
                )
            }
            if (customFrame) window.maximizedBounds = WindowChrome.workArea(window)
            CompositionLocalProvider(LocalStrings provides strings, LocalWindowFrame provides frame) {
                val theme by graph.settings.theme.collectAsState(ThemePreferences())
                val dark = theme.isDark(isSystemInDarkTheme())
                SillonTheme(darkTheme = dark, accent = theme.accent, pureBlack = theme.pureBlack) {
                    val background = MaterialTheme.colorScheme.background
                    val outline = MaterialTheme.colorScheme.outlineVariant
                    LaunchedEffect(background, dark) {
                        if (customFrame) {
                            WindowChrome.round(window.windowHandle, outline.toArgb())
                        } else {
                            WindowChrome.blend(window.windowHandle, background.toArgb(), dark)
                        }
                    }
                    SillonApp(app)
                }
            }
        }
    }
}

private fun placementOf(state: WindowState): SavedPlacement {
    val position = state.position.takeIf { it.isSpecified }
    return SavedPlacement(
        width = state.size.width.value.toInt(),
        height = state.size.height.value.toInt(),
        x = position?.x?.value?.toInt(),
        y = position?.y?.value?.toInt(),
        maximized = state.placement == WindowPlacement.Maximized,
    )
}

private fun visiblePosition(saved: SavedPlacement?): WindowPosition {
    val x = saved?.x ?: return WindowPosition.PlatformDefault
    val y = saved.y ?: return WindowPosition.PlatformDefault
    val visible = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { screen ->
            screen.defaultConfiguration.bounds.intersects(Rectangle(x, y, TITLE_GRIP, TITLE_GRIP))
        }
    }.getOrDefault(false)
    return if (visible) WindowPosition(x.dp, y.dp) else WindowPosition.PlatformDefault
}

private const val IMAGE_CACHE_BYTES = 256L * 1024 * 1024
private const val MIN_WIDTH = 960
private const val MIN_HEIGHT = 600
private const val TITLE_GRIP = 80

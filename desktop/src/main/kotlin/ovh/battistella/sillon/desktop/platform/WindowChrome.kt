package ovh.battistella.sillon.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

internal interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

object WindowChrome {
    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36
    private const val CORNER_PREFERENCE = 33
    private const val CORNER_ROUND = 2

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private val dwm: Dwmapi? by lazy {
        if (isWindows) runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull() else null
    }

    fun colorRef(argb: Int): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (blue shl 16) or (green shl 8) or red
    }

    fun customFrame(): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val wanted = System.getProperty("sillon.window.frame")
        return when (wanted) {
            "native" -> false
            "custom" -> true
            else -> !os.contains("mac")
        }
    }

    fun workArea(window: java.awt.Window): java.awt.Rectangle {
        val configuration = window.graphicsConfiguration
        val bounds = configuration.bounds
        val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        val onWindows = isWindows
        return java.awt.Rectangle(
            (if (onWindows) 0 else bounds.x) + insets.left,
            (if (onWindows) 0 else bounds.y) + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
    }

    fun round(windowHandle: Long, borderArgb: Int) {
        val api = dwm ?: return
        if (windowHandle == 0L) return
        val window = Pointer(windowHandle)
        runCatching {
            api.DwmSetWindowAttribute(window, CORNER_PREFERENCE, IntByReference(CORNER_ROUND), Int.SIZE_BYTES)
            api.DwmSetWindowAttribute(window, BORDER_COLOR, IntByReference(colorRef(borderArgb)), Int.SIZE_BYTES)
        }
    }

    fun blend(windowHandle: Long, backgroundArgb: Int, dark: Boolean) {
        val api = dwm ?: return
        if (windowHandle == 0L) return
        val window = Pointer(windowHandle)
        val colour = colorRef(backgroundArgb)
        runCatching {
            api.DwmSetWindowAttribute(window, USE_IMMERSIVE_DARK_MODE, IntByReference(if (dark) 1 else 0), Int.SIZE_BYTES)
            api.DwmSetWindowAttribute(window, CAPTION_COLOR, IntByReference(colour), Int.SIZE_BYTES)
            api.DwmSetWindowAttribute(window, TEXT_COLOR, IntByReference(colour), Int.SIZE_BYTES)
            api.DwmSetWindowAttribute(window, BORDER_COLOR, IntByReference(colour), Int.SIZE_BYTES)
        }
    }
}

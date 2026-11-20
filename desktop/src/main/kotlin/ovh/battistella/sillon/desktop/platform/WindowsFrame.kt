package ovh.battistella.sillon.desktop.platform

import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.ConcurrentHashMap

internal interface User32Extra : StdCallLibrary {
    fun IsZoomed(window: WinDef.HWND): Boolean
    fun GetDpiForWindow(window: WinDef.HWND): Int
    fun ScreenToClient(window: WinDef.HWND, point: WinDef.POINT): Boolean
}

/**
 * An undecorated AWT window is a bare popup: Windows gives it no resize border and
 * leaves it out of Snap (Win + arrows, dragging to a screen edge). This puts the
 * standard frame styles back and subclasses the window procedure so the frame
 * takes no room, which keeps the app-drawn title bar while Windows treats the
 * window as a normal resizable, snappable one. The app's [CaptionAreas] are
 * reported as the native caption, so Windows runs the drag itself: edge snapping,
 * Snap Layouts and double-click to maximize come with it.
 */
object WindowsFrame {
    private const val GWL_STYLE = -16
    private const val GWLP_WNDPROC = -4

    private const val WS_POPUP = 0x80000000.toInt()
    private const val WS_CAPTION = 0x00C00000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val FRAME_STYLES = WS_CAPTION or WS_SYSMENU or WS_THICKFRAME or WS_MINIMIZEBOX or WS_MAXIMIZEBOX

    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCACTIVATE = 0x0086

    private const val HTCLIENT = 1
    private const val HTCAPTION = 2
    private const val HTLEFT = 10
    private const val HTRIGHT = 11
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val HTBOTTOM = 15
    private const val HTBOTTOMLEFT = 16
    private const val HTBOTTOMRIGHT = 17

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_ASYNCWINDOWPOS = 0x4000

    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val DEFAULT_DPI = 96
    private const val RESIZE_BORDER_DP = 6

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private val extra: User32Extra? by lazy {
        if (isWindows) runCatching { Native.load("user32", User32Extra::class.java) }.getOrNull() else null
    }

    /** Keeps each window procedure reachable so the GC cannot free a callback Windows still calls. */
    private val procedures = ConcurrentHashMap<Long, WinUser.WindowProc>()

    /** True once Windows runs the frame: resizing, Snap and dragging by the [captions]. */
    fun install(windowHandle: Long, captions: CaptionAreas): Boolean {
        if (!isWindows || windowHandle == 0L) return false
        if (procedures.containsKey(windowHandle)) return true
        val api = extra ?: return false
        return runCatching {
            val user32 = User32.INSTANCE
            val hwnd = WinDef.HWND(Pointer(windowHandle))
            val original = Pointer(user32.GetWindowLongPtr(hwnd, GWLP_WNDPROC).toLong())
            val procedure = FrameProcedure(api, original, captions)
            procedures[windowHandle] = procedure
            user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(procedure))
            val style = user32.GetWindowLongPtr(hwnd, GWL_STYLE).toLong().toInt()
            user32.SetWindowLongPtr(hwnd, GWL_STYLE, Pointer(((style and WS_POPUP.inv()) or FRAME_STYLES).toLong() and 0xFFFFFFFFL))
            user32.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED or SWP_ASYNCWINDOWPOS,
            )
            true
        }.getOrDefault(false)
    }

    /** Which resize edge, if any, a point in window coordinates sits on. */
    fun edgeAt(x: Int, y: Int, width: Int, height: Int, border: Int): Int {
        val left = x < border
        val right = x >= width - border
        val top = y < border
        val bottom = y >= height - border
        return when {
            top && left -> HTTOPLEFT
            top && right -> HTTOPRIGHT
            bottom && left -> HTBOTTOMLEFT
            bottom && right -> HTBOTTOMRIGHT
            left -> HTLEFT
            right -> HTRIGHT
            top -> HTTOP
            bottom -> HTBOTTOM
            else -> HTCLIENT
        }
    }

    private class FrameProcedure(
        private val api: User32Extra,
        private val original: Pointer,
        private val captions: CaptionAreas,
    ) : WinUser.WindowProc {
        override fun callback(hwnd: WinDef.HWND, message: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT =
            try {
                when (message) {
                    WM_NCCALCSIZE -> calcSize(hwnd, wParam, lParam)
                    WM_NCHITTEST -> hitTest(hwnd, message, wParam, lParam)
                    WM_NCACTIVATE -> forward(hwnd, message, wParam, WinDef.LPARAM(-1))
                    else -> forward(hwnd, message, wParam, lParam)
                }
            } catch (_: Throwable) {
                forward(hwnd, message, wParam, lParam)
            }

        private fun forward(hwnd: WinDef.HWND, message: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT =
            User32.INSTANCE.CallWindowProc(original, hwnd, message, wParam, lParam)

        /** The whole window is client area; a maximized one is kept inside the monitor's work area. */
        private fun calcSize(hwnd: WinDef.HWND, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT {
            if (wParam.toLong() == 0L) return forward(hwnd, WM_NCCALCSIZE, wParam, lParam)
            if (api.IsZoomed(hwnd)) {
                val monitor = User32.INSTANCE.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
                val info = WinUser.MONITORINFO()
                if (User32.INSTANCE.GetMonitorInfo(monitor, info).booleanValue()) {
                    val rect = Pointer(lParam.toLong())
                    val work = info.rcWork
                    rect.setInt(0, maxOf(rect.getInt(0), work.left))
                    rect.setInt(4, maxOf(rect.getInt(4), work.top))
                    rect.setInt(8, minOf(rect.getInt(8), work.right))
                    rect.setInt(12, minOf(rect.getInt(12), work.bottom))
                }
            }
            return WinDef.LRESULT(0)
        }

        private fun hitTest(hwnd: WinDef.HWND, message: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT {
            val inner = forward(hwnd, message, wParam, lParam)
            if (inner.toInt() != HTCLIENT) return inner
            val raw = lParam.toLong()
            val screenX = (raw and 0xFFFF).toShort().toInt()
            val screenY = ((raw shr 16) and 0xFFFF).toShort().toInt()
            if (!api.IsZoomed(hwnd)) {
                val bounds = WinDef.RECT()
                if (User32.INSTANCE.GetWindowRect(hwnd, bounds)) {
                    val dpi = runCatching { api.GetDpiForWindow(hwnd) }.getOrDefault(DEFAULT_DPI).takeIf { it > 0 } ?: DEFAULT_DPI
                    val border = RESIZE_BORDER_DP * dpi / DEFAULT_DPI
                    val edge = edgeAt(screenX - bounds.left, screenY - bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top, border)
                    if (edge != HTCLIENT) return WinDef.LRESULT(edge.toLong())
                }
            }
            val point = WinDef.POINT(screenX, screenY)
            if (api.ScreenToClient(hwnd, point) && captions.contains(point.x, point.y)) return WinDef.LRESULT(HTCAPTION.toLong())
            return inner
        }
    }
}

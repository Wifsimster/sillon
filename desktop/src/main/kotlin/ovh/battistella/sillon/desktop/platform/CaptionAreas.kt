package ovh.battistella.sillon.desktop.platform

import java.util.concurrent.ConcurrentHashMap

/**
 * The parts of the window that act as its title bar, in window pixels. The UI
 * reports them as it lays out; the native window procedure reads them on another
 * thread to tell Windows where a drag moves the window.
 */
class CaptionAreas {
    private val areas = ConcurrentHashMap<Any, IntArray>()

    fun put(owner: Any, left: Int, top: Int, right: Int, bottom: Int) {
        areas[owner] = intArrayOf(left, top, right, bottom)
    }

    fun remove(owner: Any) {
        areas.remove(owner)
    }

    fun contains(x: Int, y: Int): Boolean =
        areas.values.any { (left, top, right, bottom) -> x >= left && x < right && y >= top && y < bottom }
}

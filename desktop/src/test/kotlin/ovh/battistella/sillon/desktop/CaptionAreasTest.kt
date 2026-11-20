package ovh.battistella.sillon.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.desktop.platform.CaptionAreas

class CaptionAreasTest {
    @Test
    fun `a point drags the window only inside a reported area`() {
        val areas = CaptionAreas()
        val strip = Any()
        areas.put(strip, 300, 0, 900, 36)
        assertTrue(areas.contains(300, 0))
        assertTrue(areas.contains(899, 35))
        assertFalse(areas.contains(900, 10))
        assertFalse(areas.contains(500, 36))
        assertFalse(areas.contains(10, 10))
    }

    @Test
    fun `an area stops dragging once removed`() {
        val areas = CaptionAreas()
        val strip = Any()
        areas.put(strip, 0, 0, 100, 36)
        areas.remove(strip)
        assertFalse(areas.contains(50, 10))
    }
}

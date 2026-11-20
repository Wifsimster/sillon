package ovh.battistella.sillon.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.sillon.desktop.platform.WindowsFrame

class WindowsFrameTest {
    private fun edge(x: Int, y: Int) = WindowsFrame.edgeAt(x, y, width = 1000, height = 600, border = 6)

    @Test
    fun `the middle of the window is client area`() {
        assertEquals(1, edge(500, 300))
        assertEquals(1, edge(6, 6))
    }

    @Test
    fun `sides and corners resize`() {
        assertEquals(10, edge(0, 300))
        assertEquals(11, edge(999, 300))
        assertEquals(12, edge(500, 0))
        assertEquals(15, edge(500, 594))
        assertEquals(13, edge(2, 2))
        assertEquals(14, edge(997, 3))
        assertEquals(16, edge(1, 598))
        assertEquals(17, edge(999, 599))
    }
}

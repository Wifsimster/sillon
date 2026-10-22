package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosQueueTest {
    @Test
    fun `a short queue goes whole`() {
        assertEquals(0 until 12, SonosQueue.window(size = 12, index = 7))
        assertTrue(SonosQueue.window(size = 0, index = 0).isEmpty())
    }

    @Test
    fun `a long queue is cut around the current track, a quarter before it`() {
        assertEquals(950 until 1150, SonosQueue.window(size = 5_000, index = 1_000))
        assertEquals(0 until 200, SonosQueue.window(size = 5_000, index = 10))
        assertEquals(4_800 until 5_000, SonosQueue.window(size = 5_000, index = 4_990))
    }

    @Test
    fun `the start moves to the next track the speaker can play`() {
        assertEquals(1, SonosQueue.realign(kept = listOf(0, 2, 3), index = 2))
        assertEquals(1, SonosQueue.realign(kept = listOf(0, 2, 3), index = 1))
        assertEquals(2, SonosQueue.realign(kept = listOf(0, 2, 3), index = 9))
        assertEquals(0, SonosQueue.realign(kept = emptyList(), index = 3))
    }

    @Test
    fun `back on the phone, its own queue picks up at the speaker's track`() {
        val phone = listOf("a", "b", "c", "d", "e")
        assertEquals(3, SonosQueue.phoneIndex(phone, sent = listOf("b", "d", "e"), index = 1))
        assertEquals(0, SonosQueue.phoneIndex(phone, sent = phone, index = 0))
        // The same track twice: the occurrence in order.
        assertEquals(3, SonosQueue.phoneIndex(listOf("a", "b", "a", "b"), sent = listOf("a", "b", "a", "b"), index = 3))
    }

    @Test
    fun `a queue changed on the speaker isn't found on the phone`() {
        val phone = listOf("a", "b", "c")
        assertNull(SonosQueue.phoneIndex(phone, sent = listOf("x", "y"), index = 0))
        assertNull(SonosQueue.phoneIndex(phone, sent = listOf("c", "a"), index = 1))
        assertNull(SonosQueue.phoneIndex(phone, sent = listOf("a"), index = 4))
    }
}

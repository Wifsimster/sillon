package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skipping 1-star tracks never runs past the end of a side. */
class SideOneStarTest {
    private val queue = listOf("a1", "a2", "a3", "b1", "b2")
    private val sidePlan = SidePlan.of(
        listOf(Side("A", listOf("a1", "a2", "a3"), 0), Side("B", listOf("b1", "b2"), 0)),
        queue,
    )!!

    private fun machineAt(index: Int, oneStar: Set<String>) = SideMachine().apply {
        skipped = { queue.getOrNull(it) in oneStar }
        setPlan(sidePlan)
        onQueue(queue, index, shuffle = false)
    }

    @Test
    fun `a side ends after its last track that will play`() {
        val machine = machineAt(1, oneStar = setOf("a3"))

        assertTrue(machine.pauseAtEnd)
        assertEquals(SideEnd("A", "B", 3), machine.onPausedAtEndOfItem())
    }

    @Test
    fun `the end of the side holds if the player already reached the skipped track`() {
        val machine = machineAt(1, oneStar = setOf("a3"))
        machine.onPausedAtEndOfItem()

        machine.onQueue(queue, 2, shuffle = false)

        assertEquals(SideEnd("A", "B", 3), machine.state.ended)
        assertEquals(3, machine.flip())
    }

    @Test
    fun `several 1-star tracks at the end of a side`() {
        val machine = machineAt(0, oneStar = setOf("a2", "a3"))

        assertTrue(machine.pauseAtEnd)
        assertEquals(SideEnd("A", "B", 3), machine.onPausedAtEndOfItem())
    }

    @Test
    fun `a 1-star track in the middle of a side changes nothing`() {
        val machine = machineAt(0, oneStar = setOf("a2"))

        assertFalse(machine.pauseAtEnd)
        assertNull(machine.onPausedAtEndOfItem())
    }

    @Test
    fun `without 1-star tracks the side ends on its last track`() {
        assertFalse(machineAt(1, oneStar = emptySet()).pauseAtEnd)
        assertTrue(machineAt(2, oneStar = emptySet()).pauseAtEnd)
    }

    @Test
    fun `listening together, sides stay off whatever the ratings`() {
        val machine = machineAt(1, oneStar = setOf("a3"))
        machine.grouped = true

        assertFalse(machine.pauseAtEnd)
        assertNull(machine.onPausedAtEndOfItem())
    }
}

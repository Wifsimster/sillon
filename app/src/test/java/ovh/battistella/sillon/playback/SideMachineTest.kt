package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideMachineTest {
    private val queue = listOf("a1", "a2", "b1", "b2", "c1", "c2")
    private val sidePlan = SidePlan.of(
        listOf(
            Side("A", listOf("a1", "a2"), 0),
            Side("B", listOf("b1", "b2"), 0),
            Side("C", listOf("c1", "c2"), 0),
        ),
        queue,
    )!!

    private fun machineAt(index: Int, shuffle: Boolean = false) = SideMachine().apply {
        setPlan(sidePlan)
        onQueue(queue, index, shuffle)
    }

    @Test
    fun `reports the side being played`() {
        assertEquals(SideState(active = true, current = "A"), machineAt(0).state)
        assertEquals("B", machineAt(3).state.current)
    }

    @Test
    fun `pauses only at the end of the last track of a side`() {
        assertFalse(machineAt(0).pauseAtEnd)
        assertTrue(machineAt(1).pauseAtEnd)
        assertFalse(machineAt(2).pauseAtEnd)
        assertTrue(machineAt(3).pauseAtEnd)
    }

    @Test
    fun `last side plays to the end as usual`() {
        val machine = machineAt(5)
        assertFalse(machine.pauseAtEnd)
        assertNull(machine.onPausedAtEndOfItem())
        assertNull(machine.state.ended)
    }

    @Test
    fun `end of side then flip starts the next side`() {
        val machine = machineAt(1)
        assertEquals(SideEnd("A", "B", 2), machine.onPausedAtEndOfItem())
        assertEquals(SideEnd("A", "B", 2), machine.state.ended)
        assertFalse(machine.pauseAtEnd)

        assertEquals(2, machine.flip())
        assertNull(machine.state.ended)
        machine.onQueue(queue, 2, shuffle = false)
        assertEquals("B", machine.state.current)
    }

    @Test
    fun `end of side is also recognised when the player already moved to the next track`() {
        val machine = machineAt(4)
        assertEquals(SideEnd("B", "C", 4), machine.onPausedAtEndOfItem())
    }

    @Test
    fun `a pause in the middle of a side is not an end of side`() {
        assertNull(machineAt(0).onPausedAtEndOfItem())
    }

    @Test
    fun `flip without an ended side does nothing`() {
        assertNull(machineAt(1).flip())
    }

    @Test
    fun `end of side survives the move to the next side but not a jump elsewhere`() {
        val machine = machineAt(1)
        machine.onPausedAtEndOfItem()
        machine.onQueue(queue, 2, shuffle = false)
        assertEquals("A", machine.state.ended?.ended)
        machine.onQueue(queue, 5, shuffle = false)
        assertNull(machine.state.ended)
    }

    @Test
    fun `shuffle turns sides off`() {
        val machine = machineAt(1, shuffle = true)
        assertEquals(SideState(), machine.state)
        assertFalse(machine.pauseAtEnd)
        assertNull(machine.onPausedAtEndOfItem())
    }

    @Test
    fun `a different queue such as a radio or a playlist turns sides off`() {
        val machine = machineAt(1)
        machine.onQueue(queue + "radio", 1, shuffle = false)
        assertFalse(machine.state.active)
        assertFalse(machine.pauseAtEnd)
    }

    @Test
    fun `a new plan clears the ended side`() {
        val machine = machineAt(1)
        machine.onPausedAtEndOfItem()
        machine.setPlan(null)
        assertEquals(SideState(), machine.state)
        assertNull(machine.flip())
    }

    @Test
    fun `restored state resumes on the ended side once the queue is back`() {
        val machine = SideMachine()
        machine.restore(sidePlan, SideEnd("A", "B", 2))
        assertEquals(SideState(), machine.state)
        machine.onQueue(queue, 1, shuffle = false)
        assertEquals(SideEnd("A", "B", 2), machine.state.ended)
        assertEquals(2, machine.flip())
    }

    @Test
    fun `listening together turns sides off and drops the ended side`() {
        val machine = machineAt(1)
        machine.onPausedAtEndOfItem()
        machine.grouped = true
        assertEquals(SideState(), machine.state)
        assertFalse(machine.pauseAtEnd)
        assertNull(machine.onPausedAtEndOfItem())
        assertNull(machine.flip())
    }

    @Test
    fun `sides come back once the group session is over`() {
        val machine = machineAt(1)
        machine.grouped = true
        machine.grouped = false
        assertTrue(machine.pauseAtEnd)
        assertEquals("A", machine.state.current)
    }
}

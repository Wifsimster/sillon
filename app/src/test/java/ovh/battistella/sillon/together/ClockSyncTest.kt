package ovh.battistella.sillon.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSyncTest {
    private fun exchange(at: Long, offset: Long, up: Long, down: Long, processing: Long = 1): ClockSample {
        val serverReceivedAt = at + up + offset
        val serverSentAt = serverReceivedAt + processing
        return ClockSample(at, serverReceivedAt, serverSentAt, at + up + processing + down)
    }

    @Test
    fun `symmetric round trip gives the exact offset`() {
        val sample = exchange(at = 1_000, offset = 5_000, up = 20, down = 20)
        assertEquals(5_000.0, sample.offsetMs, 0.0)
        assertEquals(40L, sample.roundTripMs)
    }

    @Test
    fun `the error of an asymmetric round trip is half the asymmetry`() {
        val sample = exchange(at = 0, offset = -2_000, up = 10, down = 50)
        assertEquals(-2_000.0 - 20.0, sample.offsetMs, 0.0)
    }

    @Test
    fun `server processing time is not counted as network`() {
        val sample = exchange(at = 0, offset = 0, up = 15, down = 15, processing = 300)
        assertEquals(30L, sample.roundTripMs)
        assertEquals(0.0, sample.offsetMs, 0.0)
    }

    @Test
    fun `trusts the sample with the shortest round trip`() {
        val clock = ClockSync()
        clock.add(exchange(at = 0, offset = 700, up = 5, down = 400))
        clock.add(exchange(at = 1_000, offset = 700, up = 8, down = 8))
        clock.add(exchange(at = 2_000, offset = 700, up = 300, down = 20))

        assertEquals(700L, clock.offsetMs)
        assertEquals(16L, clock.roundTripMs)
    }

    @Test
    fun `needs a few samples before it is trusted`() {
        val clock = ClockSync()
        clock.add(exchange(0, 0, 5, 5))
        clock.add(exchange(1, 0, 5, 5))
        assertFalse(clock.isSynced)
        clock.add(exchange(2, 0, 5, 5))
        assertTrue(clock.isSynced)
    }

    @Test
    fun `keeps a sliding window`() {
        val clock = ClockSync(window = 2)
        clock.add(exchange(0, offset = 100, up = 1, down = 1))
        clock.add(exchange(10, offset = 200, up = 10, down = 10))
        clock.add(exchange(20, offset = 300, up = 20, down = 20))

        assertEquals(2, clock.sampleCount)
        assertEquals(200L, clock.offsetMs)
    }

    @Test
    fun `ignores impossible samples`() {
        val clock = ClockSync()
        clock.add(ClockSample(sentAt = 100, serverReceivedAt = 0, serverSentAt = 0, receivedAt = 50))
        clock.add(ClockSample(sentAt = 0, serverReceivedAt = 10, serverSentAt = 5, receivedAt = 50))
        assertEquals(0, clock.sampleCount)
    }

    @Test
    fun `converts both ways`() {
        val clock = ClockSync()
        repeat(3) { clock.add(exchange(it * 1_000L, offset = 1_234, up = 4, down = 4)) }
        assertEquals(11_234L, clock.toServer(10_000))
        assertEquals(10_000L, clock.toLocal(11_234))
    }
}

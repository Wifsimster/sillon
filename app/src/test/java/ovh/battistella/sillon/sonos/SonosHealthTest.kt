package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonosHealthTest {
    private var clock = 0L
    private val health = SonosHealth { clock }

    private fun status(transport: String) = SonosStatus.of(mapOf("CurrentTransportState" to transport), mapOf("Track" to "1"), emptyMap())

    /** Polls once a second for [seconds], returning the first loss. */
    private fun pollFor(seconds: Int, transport: String, ours: Boolean = true): SonosLoss? {
        repeat(seconds) {
            clock += 1_000
            health.polled(status(transport), ours)?.let { return it }
        }
        return null
    }

    @Test
    fun `a speaker that never starts is given up after 20 s`() {
        health.playAsked()
        assertNull(pollFor(20, "TRANSITIONING"))
        assertEquals(SonosLoss.COULD_NOT_PLAY, pollFor(2, "TRANSITIONING"))
    }

    @Test
    fun `a pause in the first second, before the speaker played, ends the wait`() {
        health.playAsked()
        clock += 400
        health.polled(status("TRANSITIONING"), ours = true)
        health.pauseAsked()
        // The speaker may still say TRANSITIONING or STOPPED for a while after the pause.
        assertNull(pollFor(30, "TRANSITIONING"))
        assertNull(pollFor(30, "STOPPED"))
    }

    @Test
    fun `playing, or a pause from the Sonos app, ends the wait`() {
        health.playAsked()
        assertNull(pollFor(1, "PLAYING"))
        assertNull(pollFor(60, "STOPPED"))
        health.playAsked()
        assertNull(pollFor(1, "PAUSED_PLAYBACK"))
        assertNull(pollFor(60, "STOPPED"))
    }

    @Test
    fun `a new play after a pause gets its own 20 s`() {
        health.playAsked()
        assertNull(pollFor(15, "TRANSITIONING"))
        health.pauseAsked()
        health.playAsked()
        assertNull(pollFor(15, "TRANSITIONING"))
    }

    @Test
    fun `three foreign polls in a row mean the speaker was taken over`() {
        assertNull(pollFor(2, "PLAYING", ours = false))
        assertNull(pollFor(1, "PLAYING", ours = true))
        assertNull(pollFor(2, "PLAYING", ours = false))
        assertEquals(SonosLoss.TAKEN_OVER, pollFor(1, "PLAYING", ours = false))
    }

    @Test
    fun `eight failed polls in a row mean the speaker is gone`() {
        repeat(7) { assertNull(health.pollFailed()) }
        health.polled(status("PLAYING"), ours = true)
        repeat(7) { assertNull(health.pollFailed()) }
        assertEquals(SonosLoss.UNREACHABLE, health.pollFailed())
    }
}

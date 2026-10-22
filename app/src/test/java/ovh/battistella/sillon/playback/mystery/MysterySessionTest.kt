package ovh.battistella.sillon.playback.mystery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.playback.PlayableTrack

class MysterySessionTest {
    private fun track(key: String) = PlayableTrack(key, "https://srv/$key.flac", "Title $key", "Artist", "Album", 1, 1_000, null)

    private fun session(vararg keys: String) = MysterySession().apply {
        start(Mystery("album", keys.mapIndexed { i, k -> MysteryTrack(track(k), 1, i + 1) }))
    }

    @Test
    fun `reveals after three tracks or at the end of the album`() {
        assertFalse(RevealRule.shouldReveal(tracksLeft = 2, albumEnded = false))
        assertTrue(RevealRule.shouldReveal(tracksLeft = 3, albumEnded = false))
        assertTrue(RevealRule.shouldReveal(tracksLeft = 0, albumEnded = true))
    }

    @Test
    fun `the third track left reveals the album`() {
        val s = session("1", "2", "3", "4", "5")
        s.trackLeft("1")
        s.trackLeft("2")
        assertFalse(s.state.value!!.revealed)
        s.trackLeft("3")
        assertTrue(s.state.value!!.revealed)
    }

    @Test
    fun `going back over a track does not count it twice`() {
        val s = session("1", "2", "3", "4")
        s.trackLeft("1")
        s.trackLeft("2")
        s.trackLeft("2")
        s.trackLeft("1")
        assertFalse(s.state.value!!.revealed)
        assertEquals(setOf("1", "2"), s.state.value!!.left)
    }

    @Test
    fun `tracks from outside the album do not count`() {
        val s = session("1", "2", "3", "4")
        listOf("x", "y", "z").forEach(s::trackLeft)
        assertFalse(s.state.value!!.revealed)
    }

    @Test
    fun `the end of the album reveals it`() {
        val s = session("1", "2", "3", "4")
        s.albumEnded()
        assertTrue(s.state.value!!.revealed)
    }

    @Test
    fun `reveal on demand, then the animation is marked as seen`() {
        val s = session("1", "2", "3", "4")
        s.markRevealSeen()
        assertFalse(s.state.value!!.revealSeen)
        s.reveal()
        assertTrue(s.state.value!!.revealed)
        assertFalse(s.state.value!!.revealSeen)
        s.markRevealSeen()
        assertTrue(s.state.value!!.revealSeen)
    }

    @Test
    fun `nothing happens without a surprise`() {
        val s = MysterySession()
        s.trackLeft("1")
        s.albumEnded()
        s.reveal()
        assertNull(s.state.value)
    }

    @Test
    fun `clearing forgets the surprise`() {
        val s = session("1", "2", "3", "4")
        s.clear()
        assertNull(s.state.value)
    }
}

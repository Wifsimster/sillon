package ovh.battistella.sillon.sonos

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosStatusTest {
    private val uuid = "RINCON_000E58AAAAAA01400"
    private val queue = "x-rincon-queue:$uuid#0"
    private val ours = setOf("http://server/1.flac", "http://server/2.flac")

    private fun status(
        transport: String = "PLAYING",
        track: String = "2",
        rel: String = "0:00:42",
        duration: String = "0:03:10",
        trackUri: String = "http://server/2.flac",
        mediaUri: String = queue,
    ) = SonosStatus.of(
        mapOf("CurrentTransportState" to transport),
        mapOf("Track" to track, "RelTime" to rel, "TrackDuration" to duration, "TrackURI" to trackUri),
        mapOf("CurrentURI" to mediaUri),
    )

    @Test
    fun `a poll maps to an index, a position and a duration`() {
        val s = status()
        assertEquals(1, s.index)
        assertEquals(42_000L, s.positionMs)
        assertEquals(190_000L, s.durationMs)
        assertTrue(s.isPlaying)
        assertEquals(Player.STATE_READY, s.playbackState)
        assertTrue(s.playWhenReady)
    }

    @Test
    fun `transport states map to Media3`() {
        assertEquals(Transport.PAUSED, Transport.of("PAUSED_PLAYBACK"))
        assertEquals(Transport.STOPPED, Transport.of("STOPPED"))
        assertEquals(Transport.NO_MEDIA, Transport.of("NO_MEDIA_PRESENT"))
        assertEquals(Transport.STOPPED, Transport.of("SOMETHING_NEW"))
        val buffering = status(transport = "TRANSITIONING")
        assertEquals(Player.STATE_BUFFERING, buffering.playbackState)
        assertTrue(buffering.playWhenReady)
        assertFalse(buffering.isPlaying)
        val paused = status(transport = "PAUSED_PLAYBACK")
        assertFalse(paused.playWhenReady)
        assertEquals(Player.STATE_READY, paused.playbackState)
    }

    @Test
    fun `nothing loaded has no index and no duration`() {
        val s = status(track = "0", duration = "0:00:00", rel = "NOT_IMPLEMENTED")
        assertNull(s.index)
        assertNull(s.durationMs)
        assertEquals(0L, s.positionMs)
    }

    @Test
    fun `the speaker plays ours while on our queue and one of our tracks`() {
        assertTrue(status().playsOurs(uuid, ours))
        // Between two tracks the URI may be blank.
        assertTrue(status(trackUri = "").playsOurs(uuid, ours))
    }

    @Test
    fun `another source, another queue or a foreign track means it was taken over`() {
        assertFalse(status(mediaUri = "x-sonos-spotify:spotify%3atrack%3a123").playsOurs(uuid, ours))
        assertFalse(status(mediaUri = "x-rincon-queue:RINCON_OTHER#0").playsOurs(uuid, ours))
        assertFalse(status(trackUri = "x-sonos-http:song.mp3").playsOurs(uuid, ours))
        assertFalse(status(mediaUri = "").playsOurs(uuid, ours))
    }

    @Test
    fun `play modes go both ways`() {
        val modes = listOf(false, true).flatMap { shuffle ->
            listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL).map { shuffle to it }
        }
        modes.forEach { (shuffle, repeat) -> assertEquals(shuffle to repeat, PlayMode.parse(PlayMode.of(shuffle, repeat))) }
        assertEquals("NORMAL", PlayMode.of(false, Player.REPEAT_MODE_OFF))
        assertEquals("SHUFFLE_NOREPEAT", PlayMode.of(true, Player.REPEAT_MODE_OFF))
        assertEquals(false to Player.REPEAT_MODE_OFF, PlayMode.parse("SOMETHING_ELSE"))
    }
}

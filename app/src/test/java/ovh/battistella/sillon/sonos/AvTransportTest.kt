package ovh.battistella.sillon.sonos

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvTransportTest {
    private fun SoapCall.arg(name: String) = args.first { it.first == name }.second

    private fun track(n: Int) = SonosTrack("k$n", "http://server/$n.flac", "<DIDL-Lite>$n</DIDL-Lite>")

    @Test
    fun `tracks are added in one call, space-separated, at the end by default`() {
        val call = AvTransport.addTracks(listOf(track(1), track(2)), firstIndex = null)
        assertEquals("AddMultipleURIsToQueue", call.action)
        assertEquals("0", call.arg("InstanceID"))
        assertEquals("2", call.arg("NumberOfURIs"))
        assertEquals("http://server/1.flac http://server/2.flac", call.arg("EnqueuedURIs"))
        assertEquals("<DIDL-Lite>1</DIDL-Lite> <DIDL-Lite>2</DIDL-Lite>", call.arg("EnqueuedURIsMetaData"))
        assertEquals("0", call.arg("DesiredFirstTrackNumberEnqueued"))
    }

    @Test
    fun `tracks inserted in the queue land at a 1-based track number`() {
        assertEquals("5", AvTransport.addTracks(listOf(track(1)), firstIndex = 4).arg("DesiredFirstTrackNumberEnqueued"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `more than sixteen tracks don't fit in one call`() {
        AvTransport.addTracks((1..17).map(::track), null)
    }

    @Test
    fun `the group plays from its own queue`() {
        val call = AvTransport.playFromQueue("RINCON_000E58AAAAAA01400")
        assertEquals("SetAVTransportURI", call.action)
        assertEquals("x-rincon-queue:RINCON_000E58AAAAAA01400#0", call.arg("CurrentURI"))
        assertEquals("", call.arg("CurrentURIMetaData"))
    }

    @Test
    fun `seeks go to a 1-based track or a time`() {
        val track = AvTransport.seekToTrack(0)
        assertEquals("TRACK_NR", track.arg("Unit"))
        assertEquals("1", track.arg("Target"))
        val time = AvTransport.seekTo(3_725_400)
        assertEquals("REL_TIME", time.arg("Unit"))
        assertEquals("1:02:05", time.arg("Target"))
    }

    @Test
    fun `removing a range counts from 1`() {
        val call = AvTransport.removeTracks(fromIndex = 2, count = 3)
        assertEquals("3", call.arg("StartingIndex"))
        assertEquals("3", call.arg("NumberOfTracks"))
    }

    @Test
    fun `a move gives the place to insert before, counted before the move`() {
        // [a b c d e]: move b (1) to index 3 → [a c d b e]: insert before e, the 5th.
        val down = AvTransport.moveTracks(fromIndex = 1, toIndex = 2, newIndex = 3)
        assertEquals("2", down.arg("StartingIndex"))
        assertEquals("1", down.arg("NumberOfTracks"))
        assertEquals("5", down.arg("InsertBefore"))
        // [a b c d e]: move d (3) to index 0 → [d a b c e]: insert before a, the 1st.
        val up = AvTransport.moveTracks(fromIndex = 3, toIndex = 4, newIndex = 0)
        assertEquals("4", up.arg("StartingIndex"))
        assertEquals("1", up.arg("InsertBefore"))
    }

    @Test
    fun `play, the group volume and the play mode`() {
        assertEquals("1", AvTransport.play().arg("Speed"))
        assertEquals("100", AvTransport.setGroupVolume(140).arg("DesiredVolume"))
        assertEquals("0", AvTransport.setGroupVolume(-3).arg("DesiredVolume"))
        assertEquals("SHUFFLE", AvTransport.setPlayMode(true, Player.REPEAT_MODE_ALL).arg("NewPlayMode"))
        assertEquals("1", AvTransport.setGroupMute(true).arg("DesiredMute"))
    }

    @Test
    fun `Sonos times read and write as H MM SS`() {
        assertEquals("0:00:00", SonosTime.format(0))
        assertEquals("0:00:59", SonosTime.format(59_999))
        assertEquals("0:00:00", SonosTime.format(-5))
        assertEquals("10:00:00", SonosTime.format(36_000_000))
        assertEquals(62_000L, SonosTime.parse("0:01:02"))
        assertEquals(3_725_000L, SonosTime.parse("1:02:05"))
        assertEquals(62_000L, SonosTime.parse("0:01:02.500"))
        assertNull(SonosTime.parse("NOT_IMPLEMENTED"))
        assertNull(SonosTime.parse(""))
        assertNull(SonosTime.parse(null))
        assertNull(SonosTime.parse("0:75:00"))
    }
}

package ovh.battistella.sillon.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.TimelineState

@RunWith(RobolectricTestRunner::class)
class TimelineReporterTest {

    private val track = PlayableTrack(
        ratingKey = "101",
        streamUrl = "https://10-0-0-2.abc.plex.direct:32400/library/parts/7/1/file.flac?X-Plex-Token=t0k",
        title = "Intro",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        durationMs = 61_000,
        artworkUrl = null,
    )

    @Test
    fun `maps player states to timeline states`() {
        assertEquals(TimelineState.Playing, TimelineReporter.stateOf(true, Player.STATE_READY, true))
        assertEquals(TimelineState.Paused, TimelineReporter.stateOf(false, Player.STATE_READY, false))
        assertEquals(TimelineState.Buffering, TimelineReporter.stateOf(false, Player.STATE_BUFFERING, true))
        assertEquals(TimelineState.Paused, TimelineReporter.stateOf(false, Player.STATE_BUFFERING, false))
        assertEquals(TimelineState.Stopped, TimelineReporter.stateOf(false, Player.STATE_ENDED, true))
    }

    @Test
    fun `reports to the server the track streams from`() {
        val expected = ServerEndpoint("https://10-0-0-2.abc.plex.direct:32400", "t0k")
        val item = MediaItems.from(track)

        assertEquals(expected, TimelineReporter.endpointOf(item))
    }

    @Test
    fun `a downloaded track has no server of its own`() {
        val item = MediaItems.from(track.copy(streamUrl = "file:///data/user/0/x/files/music/s/101.flac"))
        assertNull(TimelineReporter.endpointOf(item))
    }

    @Test
    fun `ignores items with no stream URL`() {
        val item = androidx.media3.common.MediaItem.Builder().setMediaId("1").build()
        assertNull(TimelineReporter.endpointOf(item))
    }
}

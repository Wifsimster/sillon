package ovh.battistella.sillon.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ovh.battistella.sillon.playback.SourceRecovery.Action

@RunWith(RobolectricTestRunner::class)
class SourceRecoveryTest {
    private val transcode = "https://192-168-1-10.abc.plex.direct:32400/music/:/transcode/universal/start.m3u8?session=old"
    private val original = "https://192-168-1-10.abc.plex.direct:32400/library/parts/7/file.flac"
    private val badStatus = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    private val noNetwork = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    private val decoder = PlaybackException.ERROR_CODE_DECODING_FAILED

    @Test
    fun `a transcode that went stale is asked again, then played from the original, then left in error`() {
        val recovery = SourceRecovery()
        assertEquals(Action.REFRESH, recovery.decide("7", transcode, badStatus, nowMs = 0))
        assertEquals(Action.ORIGINAL, recovery.decide("7", transcode, badStatus, nowMs = 1_000))
        assertEquals(Action.GIVE_UP, recovery.decide("7", transcode, badStatus, nowMs = 2_000))
    }

    @Test
    fun `an original file on a server address that went stale is asked again once`() {
        val recovery = SourceRecovery()
        assertEquals(Action.REFRESH, recovery.decide("7", original, noNetwork, nowMs = 0))
        assertEquals(Action.GIVE_UP, recovery.decide("7", original, noNetwork, nowMs = 1_000))
    }

    @Test
    fun `a later pause can go stale again, the budget comes back after the window`() {
        val recovery = SourceRecovery(windowMs = 60_000)
        recovery.decide("7", transcode, badStatus, nowMs = 0)
        recovery.decide("7", transcode, badStatus, nowMs = 1_000)
        assertEquals(Action.GIVE_UP, recovery.decide("7", transcode, badStatus, nowMs = 59_000))
        assertEquals(Action.REFRESH, recovery.decide("7", transcode, badStatus, nowMs = 60_000))
    }

    @Test
    fun `each track has its own budget`() {
        val recovery = SourceRecovery()
        recovery.decide("7", original, noNetwork, nowMs = 0)
        assertEquals(Action.REFRESH, recovery.decide("8", original, noNetwork, nowMs = 1))
    }

    @Test
    fun `a transcode the phone can't decode still falls back to the original, without asking again`() {
        val recovery = SourceRecovery()
        assertEquals(Action.ORIGINAL, recovery.decide("7", transcode, decoder, nowMs = 0))
        assertEquals(Action.GIVE_UP, recovery.decide("7", transcode, decoder, nowMs = 1))
    }

    @Test
    fun `an original the phone can't decode, a download and a blank id are left alone`() {
        val recovery = SourceRecovery()
        assertEquals(Action.GIVE_UP, recovery.decide("7", original, decoder, nowMs = 0))
        assertEquals(Action.GIVE_UP, recovery.decide("9", "file:///data/downloads/9.flac", noNetwork, nowMs = 0))
        assertEquals(Action.GIVE_UP, recovery.decide("", original, noNetwork, nowMs = 0))
    }

    @Test
    fun `reading errors count as source errors, decoding and audio output ones don't`() {
        assertTrue(SourceRecovery.isSourceError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertTrue(SourceRecovery.isSourceError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(SourceRecovery.isSourceError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertFalse(SourceRecovery.isSourceError(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(SourceRecovery.isSourceError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertFalse(SourceRecovery.isSourceError(PlaybackException.ERROR_CODE_TIMEOUT))
    }

    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()

    @After fun release() = exo.release()

    @Test
    fun `the fresh stream takes the track's place where it stopped, with the track's own metadata`() {
        val fresh = swap(playing = true)
        assertEquals(3, exo.mediaItemCount)
        assertEquals(1, exo.currentMediaItemIndex)
        assertEquals("7", exo.currentMediaItem?.mediaId)
        assertEquals(fresh, exo.currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals("Surprise", exo.currentMediaItem?.mediaMetadata?.title)
        assertEquals(95_000, exo.currentPosition)
        assertTrue(exo.playWhenReady)
    }

    @Test
    fun `a paused track stays paused once its stream is fresh`() {
        swap(playing = false)
        assertEquals(95_000, exo.currentPosition)
        assertFalse(exo.playWhenReady)
    }

    /** Puts a stale transcode at index 1, 95 s in, then swaps in a fresh stream; returns its URL. */
    private fun swap(playing: Boolean): String {
        val masked = MediaMetadata.Builder().setTitle("Surprise").build()
        val stale = MediaItem.Builder().setMediaId("7").setUri(transcode).setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(masked).build()
        exo.setMediaItems(listOf(item("6"), stale, item("8")), 1, 95_000)
        exo.playWhenReady = playing
        val freshUri = transcode.replace("session=old", "session=new")
        val fresh = MediaItems.withStreamOf(
            stale,
            MediaItem.Builder().setMediaId("7").setUri(freshUri).setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Real title").build()).build(),
        )
        SourceRecovery.resume(exo, 1, fresh)
        shadowOf(Looper.getMainLooper()).idle()
        return freshUri
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://server/$id.flac").build()
}

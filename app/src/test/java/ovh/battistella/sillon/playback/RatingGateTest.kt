package ovh.battistella.sillon.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.playback.RatingGate.Verdict
import ovh.battistella.sillon.playback.mystery.MysteryMask

/** Ratings through the media session: only trusted controllers, and only the track playing. */
@RunWith(RobolectricTestRunner::class)
class RatingGateTest {
    private val playing = MediaItem.Builder().setMediaId("t1").build()

    private val surprise = MediaItem.Builder()
        .setMediaId("t1")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setExtras(Bundle().apply { putBoolean(MysteryMask.EXTRA_MYSTERY, true) })
                .build(),
        )
        .build()

    private val denied = Verdict.Refuse(SessionError.ERROR_PERMISSION_DENIED)
    private val bad = Verdict.Refuse(SessionError.ERROR_BAD_VALUE)

    @Test
    fun `another app is denied, even for the track playing`() {
        assertEquals(denied, RatingGate.check(trusted = false, requestedKey = "t1", current = playing))
        assertEquals(denied, RatingGate.check(trusted = false, requestedKey = null, current = playing))
    }

    @Test
    fun `another app isn't offered setRating, nor Sillon's rating commands`() {
        val commands = RatingGate.commands(trusted = false)

        assertFalse(commands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
        assertFalse(commands.contains(SessionCommand(PlaybackConnection.ACTION_RATE, Bundle.EMPTY)))
        assertFalse(commands.contains(SessionCommand(PlaybackConnection.ACTION_LOVE, Bundle.EMPTY)))
        assertFalse(commands.contains(SessionCommand(PlaybackConnection.ACTION_DISLIKE, Bundle.EMPTY)))
        assertTrue(commands.contains(SessionCommand(SideSession.ACTION_FLIP, Bundle.EMPTY)))
    }

    @Test
    fun `a trusted controller is offered every way to rate`() {
        val commands = RatingGate.commands(trusted = true)

        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
        assertTrue(commands.contains(SessionCommand(PlaybackConnection.ACTION_RATE, Bundle.EMPTY)))
        assertTrue(commands.contains(SessionCommand(PlaybackConnection.ACTION_LOVE, Bundle.EMPTY)))
        assertTrue(commands.contains(SessionCommand(PlaybackConnection.ACTION_DISLIKE, Bundle.EMPTY)))
    }

    @Test
    fun `a key sent along for another track is refused`() {
        assertEquals(bad, RatingGate.check(trusted = true, requestedKey = "elsewhere", current = playing))
    }

    @Test
    fun `a trusted controller rates the track playing`() {
        assertEquals(Verdict.Rate("t1"), RatingGate.check(trusted = true, requestedKey = "t1", current = playing))
        assertEquals(Verdict.Rate("t1"), RatingGate.check(trusted = true, requestedKey = null, current = playing))
    }

    @Test
    fun `nothing playing, nothing rated`() {
        assertEquals(bad, RatingGate.check(trusted = true, requestedKey = "t1", current = null))
        assertEquals(bad, RatingGate.check(trusted = true, requestedKey = null, current = MediaItem.Builder().build()))
    }

    @Test
    fun `a surprise sleeve not revealed yet keeps its rating`() {
        assertEquals(bad, RatingGate.check(trusted = true, requestedKey = null, current = surprise))
    }

    @Test
    fun `1 star on Sonos goes to the speaker's next track, not the phone's`() {
        val speaker = mockk<Player>(relaxed = true) { every { hasNextMediaItem() } returns true }
        var phoneSkipped = false

        OneStarSkip.moveOn(speaker = speaker, skipCurrent = { phoneSkipped = true })

        verify { speaker.seekToNextMediaItem() }
        assertFalse(phoneSkipped)
    }

    @Test
    fun `1 star on the phone skips on the phone`() {
        var phoneSkipped = false

        OneStarSkip.moveOn(speaker = null, skipCurrent = { phoneSkipped = true })

        assertTrue(phoneSkipped)
    }
}

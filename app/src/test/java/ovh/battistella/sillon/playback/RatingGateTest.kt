package ovh.battistella.sillon.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Test
    fun `another app can't rate, not even the track playing`() {
        assertNull(RatingGate.target(trusted = false, requestedKey = "t1", current = playing))
        assertNull(RatingGate.target(trusted = false, requestedKey = null, current = playing))
    }

    @Test
    fun `a key sent along for another track is refused`() {
        assertNull(RatingGate.target(trusted = true, requestedKey = "elsewhere", current = playing))
    }

    @Test
    fun `a trusted controller rates the track playing`() {
        assertEquals("t1", RatingGate.target(trusted = true, requestedKey = "t1", current = playing))
        assertEquals("t1", RatingGate.target(trusted = true, requestedKey = null, current = playing))
    }

    @Test
    fun `nothing playing, nothing rated`() {
        assertNull(RatingGate.target(trusted = true, requestedKey = "t1", current = null))
        assertNull(RatingGate.target(trusted = true, requestedKey = null, current = MediaItem.Builder().build()))
    }

    @Test
    fun `a surprise sleeve not revealed yet keeps its rating`() {
        assertNull(RatingGate.target(trusted = true, requestedKey = null, current = surprise))
    }
}

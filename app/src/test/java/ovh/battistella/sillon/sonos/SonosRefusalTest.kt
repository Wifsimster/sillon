package ovh.battistella.sillon.sonos

import androidx.media3.common.MediaItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.mystery.Mystery
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysteryTrack
import ovh.battistella.sillon.playback.mystery.SleeveLabels

@RunWith(RobolectricTestRunner::class)
class SonosRefusalTest {
    private fun track(key: String) = PlayableTrack(key, "http://server/$key.flac", "T$key", "A", "Album", 1, 1L, null)
    private fun item(key: String) = MediaItem.Builder().setMediaId(key).build()
    private val sleeve = Mystery("album", listOf(MysteryTrack(track("s1"), 1, 1), MysteryTrack(track("s2"), 1, 2)))

    @Test
    fun `masked items are refused`() {
        val masked = MysteryMask.mask(MysteryTrack(track("s1"), 1, 1), SleeveLabels({ _, _ -> "?" }, "?"))
        assertTrue(SonosRefusal.refuses(listOf(item("a"), masked), mystery = null))
    }

    @Test
    fun `a track of a sleeve not yet revealed is refused, even unmasked`() {
        assertTrue(SonosRefusal.refuses(listOf(item("a"), item("s2")), sleeve))
    }

    @Test
    fun `a revealed sleeve and other music go`() {
        assertFalse(SonosRefusal.refuses(listOf(item("s1"), item("s2")), sleeve.copy(revealed = true)))
        assertFalse(SonosRefusal.refuses(listOf(item("a"), item("b")), sleeve))
        assertFalse(SonosRefusal.refuses(listOf(item("a")), mystery = null))
    }
}

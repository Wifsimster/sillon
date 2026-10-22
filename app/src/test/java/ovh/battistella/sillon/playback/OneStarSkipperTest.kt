package ovh.battistella.sillon.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** "Skip 1-star tracks" on a real ExoPlayer: a1 b1 (1★) c1 d1 (1★) e1. */
@RunWith(RobolectricTestRunner::class)
class OneStarSkipperTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    private val oneStar = setOf("b1", "d1")
    private var grouped = false
    private var enabled = true

    private val skipper = OneStarSkipper(
        player = exo,
        isOneStar = { it.mediaId in oneStar },
        enabled = { enabled },
        grouped = { grouped },
        holding = { false },
    ).also(exo::addListener)

    private fun queue(vararg ids: String, start: Int = 0) {
        exo.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).setUri("https://jf.test/$it").build() }, start, 0L)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun current() = exo.currentMediaItem?.mediaId

    @After
    fun tearDown() = exo.release()

    @Test
    fun `next jumps over a 1-star track`() {
        queue("a1", "b1", "c1", "d1", "e1")

        exo.seekToNextMediaItem()
        idle()

        assertEquals("c1", current())
    }

    @Test
    fun `a 1-star track you choose plays`() {
        queue("a1", "b1", "c1", "d1", "e1", start = 1)

        assertEquals("b1", current())
    }

    @Test
    fun `a 1-star track picked from the queue plays`() {
        queue("a1", "b1", "c1", "d1", "e1")

        skipper.picking(3) { exo.seekToDefaultPosition(3) }
        idle()

        assertEquals("d1", current())
    }

    @Test
    fun `after a pick, next jumps over 1-star tracks again`() {
        queue("a1", "b1", "c1", "d1", "e1")
        skipper.picking(3) { exo.seekToDefaultPosition(3) }
        idle()

        exo.seekToPreviousMediaItem()
        idle()
        exo.seekToNextMediaItem()
        idle()

        assertEquals("e1", current())
    }

    @Test
    fun `a pick whose seek never happens doesn't spare a later next`() {
        queue("a1", "b1", "c1", "d1", "e1", start = 2)

        skipper.picking(3) { /* the group holds the queue: no seek */ }
        exo.seekToNextMediaItem()
        idle()

        assertEquals("e1", current())
    }

    @Test
    fun `going back to a 1-star track plays it`() {
        queue("a1", "b1", "c1", "d1", "e1", start = 2)

        exo.seekToPreviousMediaItem()
        idle()

        assertEquals("b1", current())
    }

    @Test
    fun `rating the current track 1 star moves on past the next 1-star track`() {
        queue("a1", "b1", "c1", "d1", "e1", start = 2)

        skipper.skipCurrent()
        idle()

        assertEquals("e1", current())
    }

    @Test
    fun `with only 1-star tracks after it, the player stops there`() {
        queue("a1", "b1", "c1", "d1")
        exo.playWhenReady = true

        exo.seekTo(2, 0)
        idle()
        skipper.skipCurrent()
        idle()

        assertEquals("c1", current())
        assertFalse(exo.playWhenReady)
    }

    @Test
    fun `listening together, nothing is skipped`() {
        grouped = true
        queue("a1", "b1", "c1")

        exo.seekToNextMediaItem()
        idle()
        assertEquals("b1", current())

        skipper.skipCurrent()
        idle()
        assertEquals("b1", current())
    }

    @Test
    fun `turned off, next plays the 1-star track`() {
        enabled = false
        queue("a1", "b1", "c1")

        exo.seekToNextMediaItem()
        idle()

        assertEquals("b1", current())
    }
}

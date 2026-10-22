package ovh.battistella.sillon.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SidePlayerTest {
    private val ids = listOf("a1", "a2", "b1", "b2", "c1", "c2")
    private val plan = SidePlan.of(
        listOf(Side("A", listOf("a1", "a2"), 0), Side("B", listOf("b1", "b2"), 0), Side("C", listOf("c1", "c2"), 0)),
        ids,
    )!!

    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    private val sides = SideSession(ApplicationProvider.getApplicationContext())
    private var grouped = false
    private lateinit var player: SidePlayer
    private val group = ExoGroupPlayer(exo, afterExit = { player.sync() })
    private val ends = mutableListOf<SideEnd?>()

    private fun start(index: Int) {
        sides.setPlan(plan)
        player = SidePlayer(
            base = exo,
            exo = exo,
            grouped = { grouped },
            inGroupMode = { group.inGroupMode },
            sides = sides,
            describe = { "End of side ${it.ended}" to "Flip to ${it.next}" },
            onEndedChanged = { ends += it },
        )
        exo.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).setUri("https://jf.test/$it").build() }, index, 0)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @After
    fun tearDown() = exo.release()

    @Test
    fun `pauses at the end of the last track of a side`() {
        start(1)
        assertTrue(exo.pauseAtEndOfMediaItems)
        assertEquals("A", sides.state.value.current)
        player.seekTo(0, 0)
        idle()
        assertFalse(exo.pauseAtEndOfMediaItems)
    }

    @Test
    fun `joining a session on the last track of a side drops the pending pause`() {
        start(1)
        assertTrue(exo.pauseAtEndOfMediaItems)

        grouped = true
        player.sync()

        assertFalse(exo.pauseAtEndOfMediaItems)
        assertFalse(sides.state.value.active)
    }

    @Test
    fun `in a session the group's pause at every track is left alone`() {
        start(0)
        grouped = true
        player.sync()
        group.enterGroupMode()
        assertTrue(exo.pauseAtEndOfMediaItems)

        player.seekTo(2, 0)
        idle()
        player.sync()

        assertTrue(exo.pauseAtEndOfMediaItems)
        assertFalse(sides.state.value.active)
    }

    @Test
    fun `an ended side is dropped when a session starts`() {
        start(1)
        sides.machine.restore(plan, SideEnd("A", "B", 2))
        player.sync()
        assertEquals("End of side A", player.mediaMetadata.title)

        grouped = true
        player.sync()

        assertNull(sides.state.value.ended)
        assertNull(ends.last())
        player.flip()
        assertEquals(1, exo.currentMediaItemIndex)
    }

    @Test
    fun `leaving the session gives the side pause back after the group lets go`() {
        start(1)
        grouped = true
        player.sync()
        group.enterGroupMode()

        grouped = false
        group.exitGroupMode()

        assertTrue(exo.pauseAtEndOfMediaItems)
        assertEquals("A", sides.state.value.current)
    }

    @Test
    fun `seeking after an end of side replays instead of flipping`() {
        start(1)
        sides.machine.restore(plan, SideEnd("A", "B", 2))
        player.sync()
        assertEquals(SideEnd("A", "B", 2), sides.state.value.ended)

        player.seekTo(0)
        idle()

        assertNull(sides.state.value.ended)
        assertEquals(1, exo.currentMediaItemIndex)
        assertTrue(exo.pauseAtEndOfMediaItems)
    }
}

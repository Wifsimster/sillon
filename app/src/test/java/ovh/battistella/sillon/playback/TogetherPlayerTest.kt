package ovh.battistella.sillon.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.together.GroupControls
import ovh.battistella.sillon.together.UserIntent

@RunWith(RobolectricTestRunner::class)
class TogetherPlayerTest {
    private class Controls(private val inGroup: Boolean) : GroupControls {
        val sent = mutableListOf<UserIntent>()

        override fun intercept(intent: UserIntent): Boolean {
            if (inGroup) sent += intent
            return inGroup
        }
    }

    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()

    private fun items(vararg ids: String) =
        ids.map { MediaItem.Builder().setMediaId(it).setUri("https://jf.test/$it").build() }.toMutableList()

    @After
    fun tearDown() = exo.release()

    @Test
    fun `outside a session every control reaches ExoPlayer`() {
        val controls = Controls(inGroup = false)
        val player = TogetherPlayer(exo, controls)

        player.setMediaItems(items("a", "b", "c"))
        assertEquals(3, exo.mediaItemCount)
        player.play()
        assertTrue(exo.playWhenReady)
        player.pause()
        assertFalse(exo.playWhenReady)
        player.seekTo(1, 5_000)
        assertEquals(1, exo.currentMediaItemIndex)
        assertEquals(5_000, exo.currentPosition)
        player.seekToNextMediaItem()
        assertEquals(2, exo.currentMediaItemIndex)
        player.addMediaItems(items("d"))
        player.removeMediaItems(0, 1)
        assertEquals(listOf("b", "c", "d"), (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId })
        player.setShuffleModeEnabled(true)
        assertTrue(exo.shuffleModeEnabled)
        player.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, exo.playbackParameters.speed)
        assertTrue(controls.sent.isEmpty())
    }

    @Test
    fun `in a session controls become group requests and ExoPlayer waits for the server`() {
        exo.setMediaItems(items("a", "b", "c"))
        val controls = Controls(inGroup = true)
        val player = TogetherPlayer(exo, controls)

        player.play()
        player.seekTo(2, 0)
        player.removeMediaItems(2, 3)
        player.setPlaybackSpeed(1.5f)
        player.setMediaItems(items("x", "y"))

        assertFalse(exo.playWhenReady)
        assertEquals(0, exo.currentMediaItemIndex)
        assertEquals(3, exo.mediaItemCount)
        assertEquals(1f, exo.playbackParameters.speed)
        assertEquals(
            listOf(
                UserIntent.Play,
                UserIntent.SeekToItem(2, 0),
                UserIntent.Remove(2, 3),
                UserIntent.GroupOwned,
                UserIntent.NewQueue(listOf("x", "y"), 0, 0),
            ),
            controls.sent,
        )
    }

    @Test
    fun `leaving the group gives shuffle and repeat back`() {
        exo.shuffleModeEnabled = true
        exo.repeatMode = Player.REPEAT_MODE_ALL
        val group = ExoGroupPlayer(exo)

        group.enterGroupMode()
        assertFalse(exo.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, exo.repeatMode)
        group.exitGroupMode()

        assertTrue(exo.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, exo.repeatMode)
    }
}

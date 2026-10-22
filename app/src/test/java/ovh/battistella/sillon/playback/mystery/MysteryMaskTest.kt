package ovh.battistella.sillon.playback.mystery

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ovh.battistella.sillon.playback.MediaItems
import ovh.battistella.sillon.playback.PlayableTrack

@RunWith(AndroidJUnit4::class)
class MysteryMaskTest {
    private val sleeve = Uri.parse("file:///cache/mystery/sleeve-v1.png")
    private val art = byteArrayOf(1, 2, 3)
    private val labels = SleeveLabels(
        track = { side, number -> "Mystery sleeve · side $side, track $number" },
        album = "Mystery album",
        artworkUri = sleeve,
        artworkData = art,
    )

    private fun track(key: String, title: String = "Secret song $key") = PlayableTrack(
        ratingKey = key,
        streamUrl = "https://srv.example/library/parts/$key/file.flac?X-Plex-Token=t",
        title = title,
        artist = "Secret Artist",
        album = "Secret Album",
        trackNumber = key.toInt(),
        durationMs = 180_000,
        artworkUrl = "https://srv.example/thumb/$key",
        gainDb = -6f,
    )

    private fun MediaItem.visibleText(): String = with(mediaMetadata) {
        listOf(title, artist, albumTitle, albumArtist, displayTitle, subtitle, description, writer, composer, genre)
            .joinToString("|")
    }

    @Test
    fun `a masked item hides the title, the artist, the album and the cover`() {
        val item = MysteryMask.mask(MysteryTrack(track("3"), side = 1, number = 3), labels)

        assertEquals("Mystery sleeve · side 1, track 3", item.mediaMetadata.title.toString())
        assertEquals("Mystery album", item.mediaMetadata.artist.toString())
        assertNull(item.mediaMetadata.albumTitle)
        assertFalse(item.visibleText().contains("Secret"))
        assertEquals(sleeve, item.mediaMetadata.artworkUri)
        assertArrayEquals(art, item.mediaMetadata.artworkData)
        assertTrue(MysteryMask.isMasked(item))
    }

    @Test
    fun `a masked item still plays the same stream at the same gain`() {
        val real = MediaItems.from(track("3"))
        val masked = MysteryMask.mask(MysteryTrack(track("3"), side = 1, number = 3), labels)

        assertEquals(real.mediaId, masked.mediaId)
        assertEquals(real.localConfiguration, masked.localConfiguration)
        assertEquals(-6f, masked.mediaMetadata.extras!!.getFloat(MediaItems.EXTRA_GAIN_DB), 0f)
    }

    @Test
    fun `unmasking brings back the real metadata on the same stream`() {
        val masked = MysteryMask.mask(MysteryTrack(track("3"), side = 1, number = 3), labels)
        val revealed = MysteryMask.unmask(masked, track("3"))

        assertFalse(MysteryMask.isMasked(revealed))
        assertEquals("Secret song 3", revealed.mediaMetadata.title.toString())
        assertEquals("Secret Album", revealed.mediaMetadata.albumTitle.toString())
        assertEquals(masked.localConfiguration, revealed.localConfiguration)
        assertFalse(MysteryMask.isMasked(MediaItems.from(track("4"))))
    }

    @Test
    fun `sides follow the disc numbers, tracks without a number take their place`() {
        val tracks = MysteryMask.sides(
            listOf(
                track("1") to TrackPosition(side = null, number = null),
                track("2") to TrackPosition(side = null, number = 0),
                track("3") to TrackPosition(side = 2, number = 1),
                track("4") to TrackPosition(side = 2, number = 5),
            ),
        )
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 5), tracks.map { it.side to it.number })
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `the media session shows the mystery sleeve until the reveal`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = ExoPlayer.Builder(context).build()
        val session = MediaSession.Builder(context, player).setId("mystery-test").build()
        val mystery = MysterySession()
        val tracks = (1..4).map { MysteryTrack(track("$it"), side = 1, number = it) }
        mystery.start(Mystery("album", tracks))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val watcher = MysteryWatcher(player, mystery, scope).also { it.start() }
        try {
            player.setMediaItems(tracks.map { MysteryMask.mask(it, labels) })
            shadowOf(Looper.getMainLooper()).idle()

            val platform = android.media.session.MediaController(context, session.platformToken)
            val hidden = (0 until session.player.mediaItemCount).map { session.player.getMediaItemAt(it) }
            assertTrue(hidden.none { it.visibleText().contains("Secret") })
            assertEquals("Mystery sleeve · side 1, track 1", session.player.mediaMetadata.title.toString())
            platform.metadata?.let { meta ->
                val text = meta.keySet().mapNotNull { meta.getString(it) }.joinToString("|")
                assertFalse("platform metadata leaks: $text", text.contains("Secret"))
            }

            mystery.reveal()
            shadowOf(Looper.getMainLooper()).idle()

            val shown = (0 until session.player.mediaItemCount).map { session.player.getMediaItemAt(it) }
            assertTrue(shown.none(MysteryMask::isMasked))
            assertEquals("Secret song 1", session.player.mediaMetadata.title.toString())
            assertEquals("Secret Album", session.player.mediaMetadata.albumTitle.toString())
        } finally {
            watcher.release()
            scope.cancel()
            session.release()
            player.release()
        }
    }

    @Test
    fun `playing something else forgets the surprise`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = ExoPlayer.Builder(context).build()
        val mystery = MysterySession()
        val tracks = (1..4).map { MysteryTrack(track("$it"), side = 1, number = it) }
        mystery.start(Mystery("album", tracks))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val watcher = MysteryWatcher(player, mystery, scope).also { it.start() }
        try {
            player.setMediaItems(tracks.map { MysteryMask.mask(it, labels) })
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("album", mystery.state.value?.albumKey)

            player.setMediaItems(listOf(MediaItems.from(track("9"))))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(mystery.state.value)
        } finally {
            watcher.release()
            scope.cancel()
            player.release()
        }
    }
}

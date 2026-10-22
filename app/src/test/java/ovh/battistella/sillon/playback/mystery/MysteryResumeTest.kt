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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.settings.SavedQueue
import ovh.battistella.sillon.playback.MediaItems
import ovh.battistella.sillon.playback.PlayableTrack

@RunWith(AndroidJUnit4::class)
class MysteryResumeTest {
    private val labels = SleeveLabels(
        track = { side, number -> "Mystery sleeve · side $side, track $number" },
        album = "Mystery album",
        artworkUri = Uri.parse("content://ovh.battistella.sillon.artwork/sleeve/v1"),
        artworkData = byteArrayOf(1, 2, 3),
    )

    private fun track(key: String) = PlayableTrack(
        ratingKey = key,
        streamUrl = "https://srv.example/library/parts/$key/file.flac?X-Plex-Token=t",
        title = "Secret song $key",
        artist = "Secret Artist",
        album = "Secret Album",
        trackNumber = key.toInt(),
        durationMs = 180_000,
        artworkUrl = "https://srv.example/thumb/$key",
        gainDb = -6f,
        albumKey = "secret-album",
    )

    private val tracks = (1..4).map { track("$it") }
    private val drawn = Mystery(
        albumKey = "secret-album",
        tracks = tracks.mapIndexed { i, t -> MysteryTrack(t, side = if (i < 2) 1 else 2, number = i % 2 + 1) },
    )

    private fun MediaItem.visibleText(): String = with(mediaMetadata) {
        listOf(title, artist, albumTitle, albumArtist, displayTitle, subtitle, description).joinToString("|")
    }

    /** What the service writes on pause or on destroy, read back after the process died. */
    private fun savedThenReloaded(mystery: Mystery): SavedQueue {
        val queue = SavedQueue(
            keys = tracks.map { it.ratingKey },
            index = 1,
            positionMs = 42_000,
            title = "Mystery sleeve · side 1, track 2",
            artist = "Mystery album",
            mystery = mystery.toSaved(),
        )
        return PlexJson.decodeFromString(SavedQueue.serializer(), PlexJson.encodeToString(SavedQueue.serializer(), queue))
    }

    @Test
    fun `after the process died, the resumed queue is masked again`() {
        val saved = savedThenReloaded(drawn.copy(left = setOf("1")))
        val session = MysterySession()

        val items = MysteryResume.restore(saved.mystery, tracks, labels, session)

        assertEquals(tracks.map { it.ratingKey }, items.map { it.mediaId })
        assertTrue(items.all(MysteryMask::isMasked))
        assertTrue(items.none { it.visibleText().contains("Secret") })
        assertTrue(items.none { it.mediaMetadata.extras!!.containsKey(MediaItems.EXTRA_ALBUM_KEY) })
        assertEquals("Mystery sleeve · side 2, track 1", items[2].mediaMetadata.title.toString())
        assertEquals(labels.artworkUri, items[0].mediaMetadata.artworkUri)
        val restored = session.state.value!!
        assertEquals("secret-album", restored.albumKey)
        assertFalse(restored.revealed)
        assertEquals(setOf("1"), restored.left)
        assertEquals(drawn.tracks.map { it.side to it.number }, restored.tracks.map { it.side to it.number })
    }

    @Test
    fun `when only the service restarted, the saved surprise replaces the one left in memory`() {
        val session = MysterySession().apply { start(drawn.copy(albumKey = "stale")) }

        val items = MysteryResume.restore(savedThenReloaded(drawn).mystery, tracks, labels, session)

        assertTrue(items.all(MysteryMask::isMasked))
        assertEquals("secret-album", session.state.value?.albumKey)
    }

    @Test
    fun `a saved queue without a surprise forgets the one left in memory`() {
        val session = MysterySession().apply { start(drawn) }
        val plain = PlexJson.decodeFromString(
            SavedQueue.serializer(),
            """{"keys":["1","2","3","4"],"index":0,"positionMs":0,"title":"Secret song 1","artist":"Secret Artist"}""",
        )
        assertNull(plain.mystery)

        val items = MysteryResume.restore(plain.mystery, tracks, labels, session)

        assertTrue(items.none(MysteryMask::isMasked))
        assertNull("the player would draw the sleeve over the real title", session.state.value)
    }

    @Test
    fun `an album already revealed comes back revealed, without a second reveal`() {
        val session = MysterySession()

        val items = MysteryResume.restore(savedThenReloaded(drawn.copy(revealed = true)).mystery, tracks, labels, session)

        assertTrue(items.none(MysteryMask::isMasked))
        assertEquals("Secret song 1", items[0].mediaMetadata.title.toString())
        assertTrue(session.state.value!!.revealed)
        assertTrue(session.state.value!!.revealSeen)
    }

    @Test
    fun `tracks the server no longer returns are dropped, the rest stays hidden`() {
        val session = MysterySession()

        val items = MysteryResume.restore(savedThenReloaded(drawn).mystery, tracks.drop(2), labels, session)

        assertEquals(listOf("3", "4"), items.map { it.mediaId })
        assertTrue(items.all(MysteryMask::isMasked))
        assertEquals(listOf("3", "4"), session.state.value!!.tracks.map { it.track.ratingKey })
    }

    @Test
    fun `nothing left of the surprise plays the queue as it is`() {
        val session = MysterySession().apply { start(drawn) }

        val items = MysteryResume.restore(savedThenReloaded(drawn).mystery, listOf(track("9")), labels, session)

        assertFalse(MysteryMask.isMasked(items.single()))
        assertNull(session.state.value)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `the notification and the car see the sleeve on a resumed surprise`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = ExoPlayer.Builder(context).build()
        val session = MediaSession.Builder(context, player).setId("mystery-resume-test").build()
        try {
            val items = MysteryResume.restore(savedThenReloaded(drawn).mystery, tracks, labels, MysterySession())
            player.setMediaItems(items, 1, 42_000)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("Mystery sleeve · side 1, track 2", session.player.mediaMetadata.title.toString())
            val platform = android.media.session.MediaController(context, session.platformToken)
            platform.metadata?.let { meta ->
                val text = meta.keySet().mapNotNull { meta.getString(it) }.joinToString("|")
                assertFalse("platform metadata leaks: $text", text.contains("Secret"))
            }
        } finally {
            session.release()
            player.release()
        }
    }
}

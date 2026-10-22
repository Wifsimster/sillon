package ovh.battistella.sillon.playback.mystery

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SavedQueue
import ovh.battistella.sillon.playback.MediaLibraryTree
import ovh.battistella.sillon.playback.PlayableTrack

/** The service resumes through [MediaLibraryTree.resumption]; a surprise must come back masked from there too. */
@RunWith(AndroidJUnit4::class)
class MediaLibraryTreeResumeTest {
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

    private fun tree(saved: SavedQueue, session: MysterySession): MediaLibraryTree {
        val repository = mockk<PlexRepository> { coEvery { restoreQueue(saved.keys) } returns tracks }
        val playbackState = mockk<PlaybackStateStore> { coEvery { load() } returns saved }
        val sleeve = mockk<SleeveArt> { every { labels() } returns labels }
        return MediaLibraryTree(
            context = ApplicationProvider.getApplicationContext(),
            repository = repository,
            playbackState = playbackState,
            neverPlayed = mockk(relaxed = true),
            mystery = session,
            sleeve = sleeve,
            settings = mockk(relaxed = true),
            sides = mockk(relaxed = true),
        )
    }

    private fun queue(mystery: Mystery?) = SavedQueue(
        keys = tracks.map { it.ratingKey },
        index = 1,
        positionMs = 42_000,
        mystery = mystery?.toSaved(),
    )

    @Test
    fun `resuming a hidden surprise from the service masks the queue and restores the session`() = runTest {
        val session = MysterySession()

        val resumed = tree(queue(drawn), session).resumption()

        assertEquals(tracks.map { it.ratingKey }, resumed.mediaItems.map { it.mediaId })
        assertTrue("resumption() must go through MysteryResume", resumed.mediaItems.all(MysteryMask::isMasked))
        assertTrue(resumed.mediaItems.none { it.mediaMetadata.title.toString().contains("Secret") })
        assertEquals(labels.artworkUri, resumed.mediaItems[0].mediaMetadata.artworkUri)
        assertEquals(1, resumed.startIndex)
        assertEquals(42_000L, resumed.startPositionMs)
        assertEquals("secret-album", session.state.value?.albumKey)
        assertFalse(session.state.value!!.revealed)
    }

    @Test
    fun `resuming a plain queue from the service forgets a surprise left in memory`() = runTest {
        val session = MysterySession().apply { start(drawn) }

        val resumed = tree(queue(null), session).resumption()

        assertTrue(resumed.mediaItems.none(MysteryMask::isMasked))
        assertNull(session.state.value)
    }
}

package ovh.battistella.sillon.ui.screens.queue

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.SidePlan
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysteryTrack
import ovh.battistella.sillon.playback.mystery.SleeveLabels

@RunWith(RobolectricTestRunner::class)
class QueueModelTest {
    private fun item(key: String, title: String = "Song $key") = MediaItem.Builder()
        .setMediaId(key)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Demo Artist").build())
        .build()

    private fun snapshot(keys: List<String>, current: Int, order: List<Int> = keys.indices.toList(), shuffle: Boolean = false) =
        QueueSnapshot(QueueItem.of(keys.map { item(it) }), order, current, shuffle)

    @Test
    fun splitsPlayedCurrentAndUpNext() {
        val ui = QueueModel.build(snapshot(listOf("a", "b", "c", "d"), current = 1), QueueContext())
        assertEquals("Song b", ui.current?.item?.title)
        assertEquals(listOf("Song c", "Song d"), ui.upNext.map { it.item.title })
        assertEquals(listOf("Song a"), ui.played.map { it.item.title })
        assertTrue(ui.editable)
        assertTrue(ui.canReorder)
    }

    @Test
    fun followsTheShuffleOrder() {
        val ui = QueueModel.build(
            snapshot(listOf("a", "b", "c", "d"), current = 0, order = listOf(2, 0, 3, 1), shuffle = true),
            QueueContext(),
        )
        assertEquals(listOf("Song d", "Song b"), ui.upNext.map { it.item.title })
        assertEquals(listOf("Song c"), ui.played.map { it.item.title })
        // Moves act on the list order, which isn't the play order while shuffling.
        assertTrue(ui.editable)
        assertFalse(ui.canReorder)
    }

    @Test
    fun emptyQueueHasNothingPlaying() {
        val ui = QueueModel.build(QueueSnapshot(), QueueContext())
        assertNull(ui.current)
        assertTrue(ui.upNext.isEmpty())
    }

    @Test
    fun listeningTogetherIsReadOnly() {
        val ui = QueueModel.build(snapshot(listOf("a", "b"), current = 0), QueueContext(together = true))
        assertEquals(QueueLock.TOGETHER, ui.lock)
        assertFalse(ui.editable)
        assertFalse(ui.canReorder)
    }

    @Test
    fun sonosDrivesTheQueueUnlessItShuffles() {
        val onSpeaker = QueueModel.build(snapshot(listOf("a", "b"), current = 0), QueueContext(speaker = "Living room"))
        assertEquals(QueueLock.NONE, onSpeaker.lock)
        assertTrue(onSpeaker.canReorder)
        assertEquals("Living room", onSpeaker.speaker)

        val shuffling = QueueModel.build(
            snapshot(listOf("a", "b"), current = 0, shuffle = true),
            QueueContext(speaker = "Living room"),
        )
        assertEquals(QueueLock.SPEAKER_SHUFFLE, shuffling.lock)
        assertFalse(shuffling.editable)
    }

    @Test
    fun marksTheEndOfEachSideButTheLast() {
        val keys = listOf("a1", "a2", "a3", "b1", "b2")
        val plan = SidePlan(keys, sides = listOf(0, 0, 0, 1, 1), labels = listOf("A", "B"))
        val ui = QueueModel.build(snapshot(keys, current = 0), QueueContext(sides = plan))
        assertTrue(ui.sidesActive)
        assertEquals(listOf(null, SideBreak("A", "B"), null, null), ui.upNext.map { it.sideBreak })
        assertNull(ui.current?.sideBreak)
    }

    @Test
    fun radioKeepsGoing() {
        val ui = QueueModel.build(snapshot(listOf("a"), current = 0), QueueContext(radioContinues = true))
        assertTrue(ui.radioContinues)
    }

    @Test
    fun movesMapRowsToMediaIndices() {
        val ui = QueueModel.build(snapshot(listOf("a", "b", "c", "d", "e"), current = 1), QueueContext())
        assertEquals(2 to 4, QueueModel.move(ui.upNext, 0, 2))
        assertEquals(4 to 3, QueueModel.move(ui.upNext, 2, 1))
        assertNull(QueueModel.move(ui.upNext, 0, -1))
        assertNull(QueueModel.move(ui.upNext, 2, 3))
        assertNull(QueueModel.move(ui.upNext, 1, 1))
    }

    @Test
    fun dragCrossesWholeRows() {
        assertEquals(0, QueueModel.dragSteps(40f, 144f))
        assertEquals(1, QueueModel.dragSteps(100f, 144f))
        assertEquals(-2, QueueModel.dragSteps(-300f, 144f))
        assertEquals(0, QueueModel.dragSteps(300f, 0f))
    }

    @Test
    fun duplicatesGetDistinctIds() {
        val items = QueueItem.of(listOf(item("a"), item("b"), item("a")))
        assertEquals(3, items.map { it.id }.toSet().size)
        assertEquals(listOf(0, 1, 2), items.map { it.index })
    }

    @Test
    fun hiddenSurpriseKeepsOnlyItsNumber() {
        val real = PlayableTrack(
            ratingKey = "real-1",
            streamUrl = "https://demo.invalid/1.flac",
            title = "Secret Song",
            artist = "Secret Artist",
            album = "Secret Album",
            trackNumber = 3,
            durationMs = 200_000,
            artworkUrl = "https://demo.invalid/cover.jpg",
            albumKey = "album-1",
        )
        val masked = MysteryMask.mask(
            MysteryTrack(real, side = 1, number = 3),
            SleeveLabels(track = { side, number -> "Mystery sleeve · side $side, track $number" }, album = "Mystery album"),
        )
        val item = QueueItem.of(listOf(masked)).single()
        assertTrue(item.hidden)
        assertEquals(3, item.trackNumber)
        assertEquals("", item.title)
        assertNull(item.artist)
        assertNull(item.artworkUri)
    }
}

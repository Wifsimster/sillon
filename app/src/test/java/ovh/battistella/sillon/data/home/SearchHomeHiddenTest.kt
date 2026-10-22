package ovh.battistella.sillon.data.home

import dagger.Lazy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SavedMystery
import ovh.battistella.sillon.data.settings.SavedQueue
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.mystery.Mystery
import ovh.battistella.sillon.playback.mystery.MysterySession
import ovh.battistella.sillon.playback.mystery.MysteryTrack

/** The surprise sleeve stays out of Recently played, live or waiting to be resumed after a restart. */
class SearchHomeHiddenTest {
    private val mystery = MysterySession()
    private val saved = mockk<PlaybackStateStore>()
    private val home = SearchHomeRepository(
        repository = mockk(relaxed = true),
        store = mockk(relaxed = true),
        wear = mockk(relaxed = true),
        mystery = mystery,
        saved = saved,
        playback = Lazy { mockk(relaxed = true) },
    )

    private fun sleeve(albumKey: String, revealed: Boolean) = Mystery(
        albumKey = albumKey,
        tracks = listOf(MysteryTrack(PlayableTrack("t1", "u", "Track", null, null, 1, null, null, albumKey = albumKey), 1, 1)),
        revealed = revealed,
    )

    @Test
    fun `the mystery playing unrevealed is hidden`() = runTest {
        coEvery { saved.load() } returns null
        mystery.start(sleeve("m", revealed = false))
        val shown = SearchHome.mergeRecent(
            listOf(HomeAlbum("m", "Secret", playedAt = 2), HomeAlbum("a", "A", playedAt = 1)),
            emptyList(),
            home.hidden(emptySet()),
        )
        assertEquals(listOf("a"), shown.map { it.key })
    }

    @Test
    fun `once revealed it shows like any album`() = runTest {
        coEvery { saved.load() } returns null
        mystery.start(sleeve("m", revealed = true))
        assertEquals(emptySet<String>(), home.hidden(emptySet()))
    }

    @Test
    fun `a saved mystery not resumed yet is hidden too`() = runTest {
        coEvery { saved.load() } returns SavedQueue(
            keys = listOf("t1"),
            index = 0,
            positionMs = 0,
            mystery = SavedMystery(albumKey = "m", tracks = emptyList(), revealed = false),
        )
        assertEquals(setOf("m", "old"), home.hidden(setOf("old")))
    }
}

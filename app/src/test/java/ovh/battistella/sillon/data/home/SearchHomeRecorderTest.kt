package ovh.battistella.sillon.data.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.PlayingAlbum
import ovh.battistella.sillon.playback.mystery.Mystery
import ovh.battistella.sillon.playback.mystery.MysterySession
import ovh.battistella.sillon.playback.mystery.MysteryTrack

/**
 * The recorder itself: a fake player, a real mystery session and the real disk cache. Covers sealing a
 * mystery, leaving it before the reveal, signing out and back in, and the reveal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SearchHomeRecorderTest {
    private val ready = Session.Ready("tok", SelectedServer("m1", "Demo", ServerEndpoint("https://demo", "tok")))
    private val session = MutableStateFlow<Session>(ready)
    private val playing = MutableStateFlow<PlayingAlbum?>(null)
    private val connection = mockk<PlaybackConnection> { every { playingAlbum } returns playing }
    private val serverRecent = MutableStateFlow<List<HomeAlbum>>(emptyList())
    private val plex = mockk<PlexRepository>(relaxed = true) {
        every { session } returns this@SearchHomeRecorderTest.session
        coEvery { recentAlbums() } coAnswers { serverRecent.value }
    }
    private val mystery = MysterySession()
    private val saved = mockk<PlaybackStateStore> { coEvery { load() } returns null }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val home = SearchHomeRepository(
        repository = plex,
        store = SearchHomeCache(ApplicationProvider.getApplicationContext()),
        wear = mockk(relaxed = true),
        mystery = mystery,
        saved = saved,
        playback = Lazy { connection },
        scope = scope,
        main = Dispatchers.Unconfined,
    ).also { it.start() }

    @After
    fun stop() = scope.cancel()

    private fun sleeve(albumKey: String) = Mystery(
        albumKey = albumKey,
        tracks = listOf(MysteryTrack(PlayableTrack("t1", "u", "Track", null, null, 1, null, null, albumKey = albumKey), 1, 1)),
    )

    private fun cached(): CachedHome? = runBlocking { home.cached.first() }

    private fun eventually(check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!check()) {
            check(System.currentTimeMillis() < until) { "condition not met in time" }
            Thread.sleep(10)
        }
    }

    private fun shown(): Pair<List<String>, List<String>> = runBlocking {
        val c = cached()
        val (recent, classics) = SearchHome.rows(c, home.hidden(c?.sealed.orEmpty()))
        recent.map { it.key } to classics.map { it.key }
    }

    @Test
    fun `a mystery playing unrevealed is sealed, and stays sealed once left`() {
        mystery.start(sleeve("m"))
        eventually { cached()?.sealed == setOf("m") }
        mystery.clear()
        Thread.sleep(50)
        assertEquals(setOf("m"), cached()?.sealed)
    }

    @Test
    fun `signing out and back in keeps the sealed album out of both rows`() {
        mystery.start(sleeve("m"))
        eventually { cached()?.sealed == setOf("m") }
        mystery.clear()

        session.value = Session.SignedOut
        eventually { cached() == null }
        session.value = ready
        serverRecent.value = listOf(HomeAlbum("m", "Secret", playedAt = 2), HomeAlbum("a", "A", playedAt = 1))
        runBlocking { home.refreshRecent() }

        assertEquals(setOf("m"), cached()?.sealed)
        val c = cached()!!
        runBlocking {
            val hidden = home.hidden(c.sealed)
            val (recent, classics) = SearchHome.rows(c.copy(classics = listOf(HomeAlbum("m", "Secret"), HomeAlbum("a", "A"))), hidden)
            assertEquals(listOf("a"), recent.map { it.key })
            assertEquals(listOf("a"), classics.map { it.key })
        }
    }

    @Test
    fun `the reveal unseals the album`() {
        mystery.start(sleeve("m"))
        eventually { cached()?.sealed == setOf("m") }
        mystery.reveal()
        eventually { cached()?.sealed?.isEmpty() == true }
        serverRecent.value = listOf(HomeAlbum("m", "Revealed", playedAt = 2))
        runBlocking { home.refreshRecent() }
        assertEquals(listOf("m"), shown().first)
    }

    @Test
    fun `an album played in the open is recorded, and the position ticker is left alone`() {
        playing.value = PlayingAlbum("a", "Album A", "Demo Artist")
        eventually { cached()?.local?.map { it.key } == listOf("a") }
        assertEquals("Album A", cached()!!.local.single().title)
        assertTrue(cached()!!.local.single().thumb!!.contains("a"))
        assertEquals(listOf("a"), shown().first)
        verify(exactly = 0) { connection.state }
    }

    @Test
    fun `a left mystery played in the open later shows again`() {
        mystery.start(sleeve("m"))
        eventually { cached()?.sealed == setOf("m") }
        mystery.clear()
        playing.value = PlayingAlbum("m", "Now known", "Demo Artist")
        eventually { cached()?.sealed?.isEmpty() == true }
        assertFalse(cached()!!.local.isEmpty())
        assertEquals(listOf("m"), shown().first)
    }
}

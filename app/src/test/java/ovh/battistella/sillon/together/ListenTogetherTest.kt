package ovh.battistella.sillon.together

import androidx.media3.common.Player
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.SyncPlayApi
import ovh.battistella.sillon.data.jellyfin.SyncPlayGroupInfo
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import ovh.battistella.sillon.playback.TogetherPlayer
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherTest {
    private val dispatcher = StandardTestDispatcher()
    private val endpoint = ServerEndpoint("http://jf.test", "tok", ServerKind.JELLYFIN, "u1")
    private val api = mockk<SyncPlayApi>(relaxed = true)
    private val sessionStore = mockk<SessionStore>()
    private val groupPlayer = mockk<GroupPlayer>(relaxed = true)
    private val socket = mockk<WebSocket>(relaxed = true)
    private val listener = slot<WebSocketListener>()
    private lateinit var together: ListenTogether

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { sessionStore.session } returns flowOf(Session.Ready("user", SelectedServer("m1", "Home", endpoint)))
        coEvery { sessionStore.clientId() } returns "phone-1"
        coEvery { api.clockSample(any(), any(), any()) } returns ClockSample(0, 0, 0, 0)
        every { api.openSocket(any(), any(), capture(listener)) } returns socket
        together = ListenTogether(api, sessionStore, mockk<PlexRepository>(relaxed = true))
        together.attach(groupPlayer)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun session(body: TestScope.() -> Unit) = runTest(dispatcher, timeout = 20.seconds) {
        body()
        together.leave()
        runCurrent()
    }

    private fun TestScope.connect() {
        together.join(SyncPlayGroupInfo("g1", "Road trip", "Idle", listOf("ana")))
        runCurrent()
        listener.captured.onOpen(socket, mockk(relaxed = true))
        runCurrent()
        assertEquals(TogetherState.Connecting("Road trip"), together.state.value)
    }

    private fun joined() =
        """{"MessageType":"SyncPlayGroupUpdate","MessageId":"m1","Data":{"GroupId":"g1","Type":"GroupJoined","Data":{"GroupId":"g1","GroupName":"Road trip","State":"Idle","Participants":["ana","phone"],"LastUpdatedAt":"2026-10-21T20:00:00.0000000Z"}}}"""

    @Test
    fun `controls reach the phone's player while connecting`() = session {
        connect()
        val base = mockk<Player>(relaxed = true)
        val player = TogetherPlayer(base, together)

        assertFalse(together.intercept(UserIntent.Play))
        player.play()
        player.pause()
        player.seekToNextMediaItem()

        verify { base.play() }
        verify { base.pause() }
        verify { base.seekToNextMediaItem() }
    }

    @Test
    fun `the socket dropping before GroupJoined ends the attempt`() = session {
        connect()
        listener.captured.onFailure(socket, IOException("reset"), null)
        runCurrent()

        assertEquals(TogetherState.Ended(EndReason.Failed), together.state.value)
        verify { groupPlayer.exitGroupMode() }
        assertFalse(together.intercept(UserIntent.Play))
    }

    @Test
    fun `no GroupJoined within 15 s ends the attempt and leaves the group`() = session {
        connect()
        advanceTimeBy(14_000)
        runCurrent()
        assertEquals(TogetherState.Connecting("Road trip"), together.state.value)

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(TogetherState.Ended(EndReason.TimedOut), together.state.value)
        coVerify { api.leave(endpoint, "phone-1") }
        verify { groupPlayer.exitGroupMode() }
    }

    @Test
    fun `cancel goes back to the lobby and leaves the group`() = session {
        connect()
        together.cancel()
        runCurrent()

        assertEquals(TogetherState.Off, together.state.value)
        coVerify { api.leave(endpoint, "phone-1") }
        verify { groupPlayer.exitGroupMode() }
        verify { socket.close(1000, null) }
    }

    @Test
    fun `once joined, controls go to the group and the timeout no longer applies`() = session {
        connect()
        listener.captured.onMessage(socket, joined())
        runCurrent()
        assertTrue(together.state.value is TogetherState.InGroup)
        assertTrue(together.intercept(UserIntent.Play))

        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(together.state.value is TogetherState.InGroup)
    }

    @Test
    fun `a GroupJoined queued behind a cancel does not put the player in group mode`() = session {
        connect()
        together.cancel()
        listener.captured.onMessage(socket, joined())
        runCurrent()

        assertEquals(TogetherState.Off, together.state.value)
        verify(exactly = 0) { groupPlayer.enterGroupMode() }
        verify(exactly = 0) { groupPlayer.pause() }
    }
}

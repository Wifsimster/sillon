package ovh.battistella.sillon.data.jellyfin

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.together.ClockSync
import ovh.battistella.sillon.together.CommandPlanner
import ovh.battistella.sillon.together.PlayerAction

class SyncPlayServerTest {
    private val baseUrl: String? = System.getenv("SILLON_JELLYFIN_URL")
    private val users: List<Pair<String, String>> = System.getenv("SILLON_JELLYFIN_USERS").orEmpty()
        .split(',').filter { ':' in it }.map { it.substringBefore(':') to it.substringAfter(':') }

    private val client = OkHttpClient()
    private val device = PlexDevice(product = "Sillon", version = "test", platformVersion = "test", deviceName = "Test")
    private val jellyfin = JellyfinApi(client, device)
    private val syncPlay = SyncPlayApi(client, device)

    private inner class Member(val deviceId: String, user: Pair<String, String>) {
        val server: ServerEndpoint
        val clock = ClockSync()
        val events = Channel<SyncPlayEvent>(Channel.UNLIMITED)
        lateinit var socket: WebSocket

        init {
            val auth = runBlocking { jellyfin.authenticate(baseUrl!!, deviceId, user.first, user.second) }
            server = ServerEndpoint(baseUrl!!, auth.accessToken, ServerKind.JELLYFIN, auth.user.id)
        }

        suspend fun connect() {
            val opened = kotlinx.coroutines.CompletableDeferred<Unit>()
            socket = syncPlay.openSocket(server, deviceId, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    SyncPlayMessages.parse(text)?.let { events.trySend(it) }
                }
            })
            withTimeout(5_000) { opened.await() }
            repeat(ClockSync.MIN_SAMPLES) { syncPlay.clockSample(server, deviceId) { System.nanoTime() / 1_000_000 }?.let(clock::add) }
            assertTrue(clock.isSynced)
        }

        suspend inline fun <reified T : SyncPlayEvent> next(crossinline matches: (T) -> Boolean = { true }): T =
            withTimeout(8_000) {
                var found: T? = null
                while (found == null) {
                    val event = events.receive()
                    if (event is T && matches(event)) found = event
                }
                found
            }

        fun drain() {
            while (events.tryReceive().isSuccess) Unit
        }

        fun serverNow() = clock.toServer(System.nanoTime() / 1_000_000)
    }

    @Test
    fun `two members play, pause, seek and skip together`() = runBlocking<Unit> {
        assumeTrue("set SILLON_JELLYFIN_URL and SILLON_JELLYFIN_USERS to run", baseUrl != null && users.size >= 2)
        val a = Member("sillon-it-a", users[0]).apply { connect() }
        val b = Member("sillon-it-b", users[1]).apply { connect() }
        val tracks = jellyfin.randomTracks(a.server, a.deviceId, jellyfin.sections(a.server, a.deviceId).first { it.isMusic }.key)
            .map { it.ratingKey }.sorted().take(3)
        assertEquals(3, tracks.size)

        syncPlay.create(a.server, a.deviceId, "Demo session")
        val group = a.next<SyncPlayEvent.Joined>().group
        assertTrue(syncPlay.groups(b.server, b.deviceId).any { it.groupId == group.groupId })
        syncPlay.join(b.server, b.deviceId, group.groupId)
        assertEquals(2, b.next<SyncPlayEvent.Joined>().group.participants.size)
        a.next<SyncPlayEvent.UserJoined>()

        syncPlay.setNewQueue(a.server, a.deviceId, tracks, 0, 0)
        val queue = a.next<SyncPlayEvent.Queue>().queue
        assertEquals(queue.items, b.next<SyncPlayEvent.Queue>().queue.items)
        val first = queue.current!!.playlistItemId
        for (m in listOf(a, b)) syncPlay.reportReadiness(m.server, m.deviceId, true, m.serverNow(), 0, false, first)

        val unpauseA = a.next<SyncPlayEvent.Command> { it.command.type == SyncCommandType.Unpause }.command
        val unpauseB = b.next<SyncPlayEvent.Command> { it.command.type == SyncCommandType.Unpause }.command
        assertEquals(unpauseA.whenMs, unpauseB.whenMs)
        val planA = CommandPlanner.plan(unpauseA, first, a.serverNow(), 0) as PlayerAction.PlayAt
        val planB = CommandPlanner.plan(unpauseB, first, b.serverNow(), 0) as PlayerAction.PlayAt
        assertTrue("the server schedules ahead: ${planA.delayMs} ms", planA.delayMs > 0)
        val startA = a.clock.toLocal(unpauseA.whenMs)
        val startB = b.clock.toLocal(unpauseB.whenMs)
        val bound = (a.clock.roundTripMs + b.clock.roundTripMs) / 2 + 2
        assertTrue("start instants ${startA - startB} ms apart, bound $bound", kotlin.math.abs(startA - startB) <= bound)
        println("unpause lead ${planA.delayMs}/${planB.delayMs} ms, start gap ${startA - startB} ms, rtt ${a.clock.roundTripMs}/${b.clock.roundTripMs} ms")

        Thread.sleep(1_200)
        a.drain(); b.drain()
        syncPlay.pause(b.server, b.deviceId)
        val pauseA = a.next<SyncPlayEvent.Command> { it.command.type == SyncCommandType.Pause }.command
        val pauseB = b.next<SyncPlayEvent.Command> { it.command.type == SyncCommandType.Pause }.command
        assertEquals(pauseA.positionMs, pauseB.positionMs)

        syncPlay.seek(a.server, a.deviceId, 20_000)
        assertEquals(20_000L, a.next<SyncPlayEvent.Command> { it.command.type == SyncCommandType.Seek }.command.positionMs)
        assertEquals(20_000L, b.next<SyncPlayEvent.Command> { it.command.type == SyncCommandType.Seek }.command.positionMs)

        a.drain(); b.drain()
        syncPlay.nextItem(a.server, a.deviceId, first)
        syncPlay.nextItem(b.server, b.deviceId, first)
        assertEquals(1, a.next<SyncPlayEvent.Queue>().queue.playingIndex)

        b.socket.cancel()
        assertEquals("the member whose connection died is out", users[1].first, a.next<SyncPlayEvent.UserLeft>().name)

        syncPlay.leave(a.server, a.deviceId)
        a.next<SyncPlayEvent.Left>()
        a.socket.close(1000, null)
    }
}

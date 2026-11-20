package ovh.battistella.sillon.desktop

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.desktop.discord.CoverLookup
import ovh.battistella.sillon.desktop.discord.DiscordClient
import ovh.battistella.sillon.desktop.discord.DiscordException
import ovh.battistella.sillon.desktop.discord.DiscordRichPresence
import ovh.battistella.sillon.desktop.discord.DiscordSockets
import ovh.battistella.sillon.desktop.discord.DiscordStatus
import ovh.battistella.sillon.desktop.discord.IpcFrames
import ovh.battistella.sillon.desktop.discord.IpcPipe
import ovh.battistella.sillon.desktop.discord.Listening
import ovh.battistella.sillon.desktop.discord.Presence
import ovh.battistella.sillon.desktop.discord.PresenceConnection
import ovh.battistella.sillon.desktop.discord.PresenceLabels
import ovh.battistella.sillon.desktop.player.EngineState
import ovh.battistella.sillon.playback.PlayableTrack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class DiscordPresenceTest {
    private val labels = PresenceLabels(playing = "Playing", paused = "Paused")

    private fun track(key: String, title: String = "Track $key") = PlayableTrack(
        ratingKey = key,
        streamUrl = "https://server.local/$key?X-Plex-Token=secret",
        title = title,
        artist = "Air",
        album = "Moon Safari",
        trackNumber = 1,
        durationMs = 300_000,
        artworkUrl = "https://server.local/thumb/$key?X-Plex-Token=secret",
    )

    private class FakePipe(replies: List<Pair<Int, String>>) : IpcPipe {
        override val input = ByteArrayInputStream(replies.fold(ByteArray(0)) { acc, (op, body) -> acc + IpcFrames.encode(op, body.toByteArray()) })
        override val output = ByteArrayOutputStream()
        var closed = false

        fun sent(): List<Pair<Int, JsonObject>> {
            val stream = ByteArrayInputStream(output.toByteArray())
            val frames = mutableListOf<Pair<Int, JsonObject>>()
            while (stream.available() > 0) {
                val (op, payload) = IpcFrames.read(stream)
                frames += op to runCatching { Json.parseToJsonElement(payload.decodeToString()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            }
            return frames
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun framesRoundTripInLittleEndian() {
        val frame = IpcFrames.encode(IpcFrames.FRAME, "hello".toByteArray())
        assertEquals(listOf<Byte>(1, 0, 0, 0, 5, 0, 0, 0), frame.take(8))
        val (op, payload) = IpcFrames.read(ByteArrayInputStream(frame))
        assertEquals(IpcFrames.FRAME, op)
        assertEquals("hello", payload.decodeToString())
    }

    @Test
    fun clientHandshakesSetsActivityAndAnswersPings() {
        val pipe = FakePipe(
            listOf(
                IpcFrames.FRAME to """{"cmd":"DISPATCH","evt":"READY","data":{}}""",
                IpcFrames.PING to """{"n":1}""",
                IpcFrames.FRAME to """{"cmd":"SET_ACTIVITY","evt":null,"data":{}}""",
            ),
        )
        val client = DiscordClient(pipe)
        client.handshake("123")
        client.setActivity(Presence.activity(Listening("1", "Kelly", "Air", "Moon Safari", true, 1_000, 300_000, null), labels))
        client.close()

        val sent = pipe.sent()
        assertEquals(listOf(IpcFrames.HANDSHAKE, IpcFrames.FRAME, IpcFrames.PONG), sent.map { it.first })
        assertEquals("123", sent[0].second["client_id"]!!.jsonPrimitive.content)
        val args = sent[1].second["args"]!!.jsonObject
        assertEquals("SET_ACTIVITY", sent[1].second["cmd"]!!.jsonPrimitive.content)
        assertEquals("Kelly", args["activity"]!!.jsonObject["details"]!!.jsonPrimitive.content)
        assertEquals(1, sent[2].second["n"]!!.jsonPrimitive.content.toInt())
        assertTrue(pipe.closed)
    }

    @Test
    fun clearingSendsANullActivity() {
        val pipe = FakePipe(listOf(IpcFrames.FRAME to """{"evt":null}"""))
        DiscordClient(pipe).setActivity(null)
        assertEquals(JsonNull, pipe.sent().single().second["args"]!!.jsonObject["activity"])
    }

    @Test
    fun discordErrorsAndClosesSurface() {
        val rejected = FakePipe(listOf(IpcFrames.FRAME to """{"evt":"ERROR","data":{"code":4000,"message":"bad"}}"""))
        val error = assertThrows(DiscordException::class.java) { DiscordClient(rejected).setActivity(null) }
        assertEquals(4000, error.code)

        val closed = FakePipe(listOf(IpcFrames.CLOSE to """{"code":4000,"message":"Invalid Client ID"}"""))
        assertThrows(DiscordException::class.java) { DiscordClient(closed).handshake("1") }

        assertThrows(IOException::class.java) { DiscordClient(FakePipe(emptyList())).handshake("1") }
    }

    @Test
    fun socketCandidatesCoverWindowsPipesAndLinuxSandboxes() {
        val windows = DiscordSockets.candidates(emptyMap(), windows = true)
        assertEquals("\\\\.\\pipe\\discord-ipc-0", windows.first())
        assertEquals(10, windows.size)

        val linux = DiscordSockets.candidates(mapOf("XDG_RUNTIME_DIR" to "/run/user/1000"), windows = false)
        assertEquals("/run/user/1000/discord-ipc-0", linux.first())
        assertTrue("/run/user/1000/app/com.discordapp.Discord/discord-ipc-0" in linux)
        assertTrue("/run/user/1000/snap.discord/discord-ipc-3" in linux)
        assertTrue("/tmp/discord-ipc-9" in linux)
    }

    @Test
    fun playingActivityHasAProgressBarAndThePlayIcon() {
        val activity = Presence.activity(Listening("1", "Kelly Watch the Stars", "Air", "Moon Safari", true, 10_000, 225_000, "https://is1.mzstatic.com/a.jpg"), labels)
        assertEquals(Presence.LISTENING, activity["type"]!!.jsonPrimitive.content.toInt())
        assertEquals("Kelly Watch the Stars", activity["details"]!!.jsonPrimitive.content)
        assertEquals("Air", activity["state"]!!.jsonPrimitive.content)
        val assets = activity["assets"]!!.jsonObject
        assertEquals("https://is1.mzstatic.com/a.jpg", assets["large_image"]!!.jsonPrimitive.content)
        assertEquals("Moon Safari", assets["large_text"]!!.jsonPrimitive.content)
        assertEquals("play", assets["small_image"]!!.jsonPrimitive.content)
        val timestamps = activity["timestamps"]!!.jsonObject
        assertEquals(10_000, timestamps["start"]!!.jsonPrimitive.long)
        assertEquals(235_000, timestamps["end"]!!.jsonPrimitive.long)
    }

    @Test
    fun pausedActivityHasNoTimestampsAndNeverLeaksServerUrls() {
        val activity = Presence.activity(Listening("1", "K", null, null, false, null, 0, "http://server.local/thumb?X-Plex-Token=secret"), labels)
        assertNull(activity["timestamps"])
        assertNull(activity["state"])
        val assets = activity["assets"]!!.jsonObject
        assertNull(assets["large_image"])
        assertEquals("pause", assets["small_image"]!!.jsonPrimitive.content)
        assertEquals("Paused", assets["small_text"]!!.jsonPrimitive.content)
        assertEquals(2, activity["details"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun longTextIsCutForDiscord() {
        val text = Presence.text("x".repeat(300))!!
        assertEquals(128, text.length)
        assertTrue(text.endsWith("…"))
        assertNull(Presence.text("   "))
    }

    private class Recorder {
        val shown = mutableListOf<JsonObject?>()
        var connects = 0
        var closes = 0
        var failConnect = false
        var failShow = false

        fun connect(): PresenceConnection {
            connects++
            if (failConnect) throw IOException("Discord isn't running")
            return object : PresenceConnection {
                override fun show(activity: JsonObject?) {
                    if (failShow) throw IOException("pipe broken")
                    shown += activity
                }

                override fun close() {
                    closes++
                }
            }
        }
    }

    private class Harness(scope: TestScope, enabled: Boolean = true, covers: Boolean = false) {
        val recorder = Recorder()
        val state = MutableStateFlow(EngineState())
        val enabled = MutableStateFlow(enabled)
        val covers = MutableStateFlow(covers)
        val lookups = mutableListOf<Pair<String?, String?>>()
        val presence = DiscordRichPresence(
            playback = state,
            enabled = this.enabled,
            covers = this.covers,
            lookup = { artist, album -> lookups += artist to album; "https://is1.mzstatic.com/cover.jpg" },
            labels = PresenceLabels("Playing", "Paused"),
            connect = recorder::connect,
            clock = { scope.testScheduler.currentTime + 1_000_000 },
            retryMs = 15_000,
            minGapMs = 1_000,
            pauseLimitMs = 600_000,
        )

        init {
            presence.start(scope.backgroundScope, StandardTestDispatcher(scope.testScheduler))
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_100)
        runCurrent()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun offByDefaultNeverTouchesDiscord() = runTest {
        val h = Harness(this, enabled = false)
        h.state.value = EngineState(queue = listOf(track("1")), index = 0, playing = true)
        advanceTimeBy(60_000)
        assertEquals(0, h.recorder.connects)
        assertEquals(DiscordStatus.OFF, h.presence.status.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun showsTheTrackAndOnlyResendsOnRealChanges() = runTest {
        val h = Harness(this)
        settle()
        assertEquals(1, h.recorder.connects)
        assertEquals(listOf<JsonObject?>(null), h.recorder.shown)

        h.state.value = EngineState(queue = listOf(track("1", "Kelly"), track("2")), index = 0, playing = true, positionMs = 0, durationMs = 225_000)
        settle()
        assertEquals("Kelly", h.recorder.shown.last()!!["details"]!!.jsonPrimitive.content)
        assertEquals(DiscordStatus.LIVE, h.presence.status.value)
        val afterStart = h.recorder.shown.size

        h.state.value = h.state.value.copy(positionMs = 1_100)
        settle()
        assertEquals(afterStart, h.recorder.shown.size)

        h.state.value = h.state.value.copy(positionMs = 120_000)
        settle()
        assertEquals(afterStart + 1, h.recorder.shown.size)

        h.state.value = h.state.value.copy(playing = false)
        settle()
        assertEquals("pause", h.recorder.shown.last()!!["assets"]!!.jsonObject["small_image"]!!.jsonPrimitive.content)

        h.state.value = h.state.value.copy(index = 1, playing = true, positionMs = 0)
        settle()
        assertEquals("Track 2", h.recorder.shown.last()!!["details"]!!.jsonPrimitive.content)

        h.state.value = EngineState()
        settle()
        assertNull(h.recorder.shown.last())
        assertTrue(h.lookups.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRestoredQueueStaysHiddenUntilPlayedAndALongPauseClears() = runTest {
        val h = Harness(this)
        h.state.value = EngineState(queue = listOf(track("1")), index = 0, playing = false, positionMs = 50_000)
        settle()
        assertEquals(listOf<JsonObject?>(null), h.recorder.shown)

        h.state.value = h.state.value.copy(playing = true)
        settle()
        h.state.value = h.state.value.copy(playing = false)
        settle()
        assertEquals("pause", h.recorder.shown.last()!!["assets"]!!.jsonObject["small_image"]!!.jsonPrimitive.content)

        advanceTimeBy(615_000)
        runCurrent()
        assertNull(h.recorder.shown.last())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun waitsForDiscordAndReconnectsAfterItQuits() = runTest {
        val h = Harness(this)
        h.recorder.failConnect = true
        h.state.value = EngineState(queue = listOf(track("1")), index = 0, playing = true)
        settle()
        assertEquals(DiscordStatus.WAITING, h.presence.status.value)
        assertEquals(1, h.recorder.connects)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, h.recorder.connects)

        h.recorder.failConnect = false
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(2, h.recorder.connects)
        assertEquals(DiscordStatus.LIVE, h.presence.status.value)
        assertEquals("Track 1", h.recorder.shown.last()!!["details"]!!.jsonPrimitive.content)

        h.recorder.failShow = true
        h.state.value = h.state.value.copy(playing = false)
        settle()
        assertEquals(DiscordStatus.WAITING, h.presence.status.value)
        assertEquals(1, h.recorder.closes)

        h.recorder.failShow = false
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, h.recorder.connects)
        assertEquals("pause", h.recorder.shown.last()!!["assets"]!!.jsonObject["small_image"]!!.jsonPrimitive.content)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun coversAreLookedUpOnlyWhenAllowedAndTurningOffDisconnects() = runTest {
        val h = Harness(this)
        h.state.value = EngineState(queue = listOf(track("1")), index = 0, playing = true)
        settle()
        assertNull(h.recorder.shown.last()!!["assets"]!!.jsonObject["large_image"])

        h.covers.value = true
        settle()
        assertEquals(listOf<Pair<String?, String?>>("Air" to "Moon Safari"), h.lookups)
        assertEquals("https://is1.mzstatic.com/cover.jpg", h.recorder.shown.last()!!["assets"]!!.jsonObject["large_image"]!!.jsonPrimitive.content)

        h.enabled.value = false
        settle()
        assertEquals(1, h.recorder.closes)
        assertEquals(DiscordStatus.OFF, h.presence.status.value)
        assertFalse(h.recorder.connects > 1)
    }

    @Test
    fun coverLookupSendsOnlyArtistAndAlbumAndCaches() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"resultCount":1,"results":[{"artworkUrl100":"https://is1.mzstatic.com/image/thumb/a/100x100bb.jpg"}]}"""),
        )
        server.start()
        try {
            val lookup = CoverLookup(OkHttpClient(), server.url("/"))
            assertEquals("https://is1.mzstatic.com/image/thumb/a/512x512bb.jpg", lookup.cover("Air", "Moon Safari"))
            assertEquals("https://is1.mzstatic.com/image/thumb/a/512x512bb.jpg", lookup.cover("air", "moon safari"))
            assertNull(lookup.cover("Air", null))
            assertEquals(1, server.requestCount)
            val url = server.takeRequest().requestUrl!!
            assertEquals("Air Moon Safari", url.queryParameter("term"))
            assertEquals("album", url.queryParameter("entity"))
        } finally {
            server.shutdown()
        }
    }
}

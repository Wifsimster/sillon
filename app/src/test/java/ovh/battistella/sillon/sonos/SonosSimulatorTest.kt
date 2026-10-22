package ovh.battistella.sillon.sonos

import android.os.Looper
import androidx.media3.common.MediaItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.SonosAccess
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * The whole phone → Sonos → phone path over real HTTP: the real [SonosClient] talks SOAP to a
 * simulated speaker (a [MockWebServer] answering AVTransport and GroupRenderingControl the way a
 * Sonos coordinator does). No real speaker is needed. The exchange is written to
 * `app/build/sonos-sim/` for the PR.
 */
@RunWith(RobolectricTestRunner::class)
class SonosSimulatorTest {
    private val speaker = FakeSpeaker()
    private val transcript = StringBuilder()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer(request)
        }
        start()
    }
    private val zone = SonosZone("RINCON_SIM01400", "Salon", server.url("/").toString().trimEnd('/'))
    private val group = SonosGroup("RINCON_SIM01400:1", zone, listOf(zone))
    private val phone = FakePhone()
    private val told = mutableListOf<OutputNotice>()
    private var closed = 0

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun answer(request: RecordedRequest): MockResponse {
        val action = request.getHeader("SOAPACTION").orEmpty().trim('"')
        val (type, name) = action.substringBefore('#') to action.substringAfter('#')
        val service = SonosService.entries.firstOrNull { it.type == type && it.path == request.path }
            ?: return MockResponse().setResponseCode(404)
        val body = request.body.readUtf8()
        val args = parseXml(body).descendants().first { it.localName() == name }.childElements()
            .map { it.localName() to it.textContent.orEmpty() }
        synchronized(transcript) {
            val shown = args.joinToString { (k, v) -> "$k=${if (v.length > 90) v.take(90) + "…" else v}" }
            transcript.append("POST ").append(request.path).append("  ").append(name).append("(").append(shown).append(")\n")
        }
        val out = runBlocking { speaker.call(zone.baseUrl, SoapCall(service, name, args)) }
        val xml = buildString {
            append("<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>")
            append("<u:").append(name).append("Response xmlns:u=\"").append(type).append("\">")
            out.forEach { (k, v) -> append('<').append(k).append('>').append(Soap.escape(v)).append("</").append(k).append('>') }
            append("</u:").append(name).append("Response></s:Body></s:Envelope>")
        }
        return MockResponse().setHeader("Content-Type", "text/xml; charset=\"utf-8\"").setBody(xml)
    }

    private fun item(key: String) = MediaItem.Builder()
        .setMediaId(key)
        .setUri("http://192.168.1.5:32400/library/parts/$key/file.flac?X-Plex-Token=account")
        .build()

    private fun uri(key: String) = "http://192.168.1.5:32400/library/parts/$key/file.flac?X-Plex-Token=transient"

    private fun TestScope.handover() = SonosHandover(
        phone = phone,
        scope = backgroundScope,
        client = SonosClient(OkHttpClient()),
        openAccess = { SonosAccess(ServerEndpoint("http://192.168.1.5:32400", "transient")) { closed++ } },
        tracks = { keys, _ -> keys.associateWith { SonosTrack(it, uri(it), Didl.track(it, uri(it), "audio/flac", "Track $it", "Artist", "Album", null)) } },
        refuse = { false },
        togetherActive = { false },
        show = {},
        tell = { told += it },
        attach = {},
    )

    /** Real HTTP round-trips finish off the test thread: let them land, then run what they posted. */
    private suspend fun TestScope.until(what: String, check: () -> Boolean) {
        withTimeout(10_000) {
            while (!check()) {
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()
                if (!check()) Thread.sleep(5)
            }
        }
        transcript.append("-- ").append(what).append('\n')
    }

    @Test
    fun `a queue goes to the simulated speaker and comes back to the phone`() = runTest(timeout = 5.minutes) {
        phone.setMediaItems(listOf("101", "102", "103").map(::item), 1, 42_000)
        phone.play()
        val handover = handover()
        transcript.append("== Play on Salon (phone at track 2, 0:42)\n")

        handover.playOn(group)
        val player = handover.player!!
        assertEquals(listOf(uri("101"), uri("102"), uri("103")), speaker.queue)
        assertEquals("x-rincon-queue:RINCON_SIM01400#0", speaker.mediaUri)
        assertEquals(1, speaker.index)
        assertEquals(42_000L, speaker.positionMs)
        assertEquals("PLAYING", speaker.transport)
        assertFalse(phone.playWhenReady)

        transcript.append("== Volume key up ×3\n")
        repeat(3) { player.increaseDeviceVolume(0) }
        until("speaker volume 18") { speaker.volume == 18 }

        transcript.append("== Next from the notification\n")
        player.seekToNext()
        until("speaker on track 3") { speaker.index == 2 && player.currentMediaItemIndex == 2 }

        transcript.append("== Back to This phone\n")
        handover.backToPhone(resume = true)
        assertEquals("PAUSED_PLAYBACK", speaker.transport)
        assertEquals(2, phone.currentMediaItemIndex)
        assertTrue(phone.playWhenReady)
        assertNull(handover.player)
        assertEquals(1, closed)
        assertTrue(told.isEmpty())

        val wire = transcript.toString()
        assertFalse("the account token never goes on the wire", wire.contains("X-Plex-Token=account"))
        File("build/sonos-sim").apply { mkdirs() }.resolve("transcript.txt").writeText(wire)
    }

    @Test
    fun `an unplugged simulated speaker gives the music back to the phone`() = runTest(timeout = 5.minutes) {
        phone.setMediaItems(listOf("101", "102").map(::item), 0, 0)
        phone.play()
        val handover = handover()
        handover.playOn(group)

        server.shutdown()
        // Eight failed polls, one second apart while playing; each refused connection lands off the test thread.
        var rounds = 0
        while (handover.player != null && rounds++ < 2_000) {
            testScheduler.advanceTimeBy(1_000)
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }

        assertEquals(listOf(OutputNotice.UNREACHABLE), told)
        assertFalse(phone.playWhenReady)
        assertEquals(1, closed)
    }
}

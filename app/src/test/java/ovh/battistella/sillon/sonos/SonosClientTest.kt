package ovh.battistella.sillon.sonos

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SonosClientTest {
    private val server = MockWebServer()
    private val client = SonosClient(OkHttpClient())
    private lateinit var base: String

    @Before
    fun start() {
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `an action is posted to the service with its SOAP action`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>""" +
                    """<u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">""" +
                    "<CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState></u:GetTransportInfoResponse></s:Body></s:Envelope>",
            ),
        )
        val values = client.call(base, AvTransport.transportInfo())
        assertEquals("PAUSED_PLAYBACK", values["CurrentTransportState"])
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/MediaRenderer/AVTransport/Control", request.path)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"", request.getHeader("SOAPACTION"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("text/xml"))
        assertTrue(request.body.readUtf8().contains("<InstanceID>0</InstanceID>"))
    }

    @Test
    fun `a UPnP fault becomes an error with its code`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><detail>""" +
                    "<UPnPError><errorCode>711</errorCode></UPnPError></detail></s:Fault></s:Body></s:Envelope>",
            ),
        )
        try {
            client.call(base, AvTransport.next())
            fail("expected a SonosException")
        } catch (e: SonosException) {
            assertEquals(711, e.code)
        }
    }

    @Test
    fun `the household's groups come from ZoneGroupTopology`() = runTest {
        val state = """<ZoneGroupState><ZoneGroups><ZoneGroup Coordinator="RINCON_A" ID="RINCON_A:1">""" +
            """<ZoneGroupMember UUID="RINCON_A" Location="http://192.168.1.21:1400/xml/device_description.xml" ZoneName="Salon"/>""" +
            "</ZoneGroup></ZoneGroups></ZoneGroupState>"
        server.enqueue(
            MockResponse().setBody(
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>""" +
                    """<u:GetZoneGroupStateResponse xmlns:u="urn:schemas-upnp-org:service:ZoneGroupTopology:1">""" +
                    "<ZoneGroupState>${Soap.escape(state)}</ZoneGroupState></u:GetZoneGroupStateResponse></s:Body></s:Envelope>",
            ),
        )
        val groups = client.zoneGroups(base)
        assertEquals(listOf("Salon"), groups.map { it.name })
        assertEquals("/ZoneGroupTopology/Control", server.takeRequest().path)
    }
}

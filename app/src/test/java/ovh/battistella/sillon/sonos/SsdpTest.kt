package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpTest {
    private val answer = "HTTP/1.1 200 OK\r\n" +
        "CACHE-CONTROL: max-age = 1800\r\n" +
        "EXT:\r\n" +
        "LOCATION: http://192.168.1.20:1400/xml/device_description.xml\r\n" +
        "SERVER: Linux UPnP/1.0 Sonos/97.1-80312 (ZPS62)\r\n" +
        "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n" +
        "USN: uuid:RINCON_000E58AAAAAA01400::urn:schemas-upnp-org:device:ZonePlayer:1\r\n" +
        "X-RINCON-HOUSEHOLD: Sonos_household\r\n\r\n"

    @Test
    fun `a speaker's answer gives its address and its id`() {
        val response = Ssdp.parse(answer)!!
        assertEquals("http://192.168.1.20:1400", response.baseUrl)
        assertEquals("RINCON_000E58AAAAAA01400", response.uuid)
        assertEquals("Sonos_household", response.household)
        assertTrue(response.isSonos)
    }

    @Test
    fun `headers are read whatever their case, with bare newlines too`() {
        val response = Ssdp.parse(answer.replace("\r\n", "\n").replace("LOCATION:", "Location:"))
        assertEquals("http://192.168.1.20:1400", response?.baseUrl)
    }

    @Test
    fun `another device on the network is not a Sonos`() {
        val router = "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.1:5000/rootDesc.xml\r\nSERVER: Linux UPnP/1.1 MiniUPnPd/2.3\r\n" +
            "USN: uuid:1234::upnp:rootdevice\r\n\r\n"
        assertFalse(Ssdp.parse(router)!!.isSonos)
    }

    @Test
    fun `a device saying Sonos without a Sonos id is not one`() {
        assertFalse(Ssdp.parse(answer.replace("uuid:RINCON_000E58AAAAAA01400", "uuid:fake-living-room"))!!.isSonos)
    }

    @Test
    fun `only a private or link-local address is kept`() {
        listOf("10.0.0.7", "172.16.4.2", "172.31.255.1", "192.168.1.20", "169.254.3.3").forEach {
            assertEquals("http://$it:1400", Ssdp.parse(answer.replace("192.168.1.20", it))!!.baseUrl)
        }
        listOf("8.8.8.8", "172.32.0.1", "speaker.example.com", "[fe80::1]", "192.168.1").forEach {
            assertNull(it, Ssdp.parse(answer.replace("192.168.1.20", it))?.baseUrl)
        }
    }

    @Test
    fun `a search, a notify or junk is not an answer`() {
        assertNull(Ssdp.parse(Ssdp.search()))
        assertNull(Ssdp.parse("NOTIFY * HTTP/1.1\r\nLOCATION: http://192.168.1.20:1400/x.xml\r\n\r\n"))
        assertNull(Ssdp.parse("garbage"))
        assertNull(Ssdp.parse(""))
    }

    @Test
    fun `an answer without a plain http location is dropped`() {
        assertNull(Ssdp.parse(answer.replace("http://192.168.1.20:1400", "https://192.168.1.20:1443")))
        assertNull(Ssdp.parse(answer.replace("LOCATION: http://192.168.1.20:1400/xml/device_description.xml\r\n", "")))
    }

    @Test
    fun `the search asks for zone players on the SSDP group`() {
        val search = Ssdp.search(waitSeconds = 2)
        assertTrue(search.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(search.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(search.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(search.contains("MX: 2\r\n"))
        assertTrue(search.contains("ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n"))
        assertTrue(search.endsWith("\r\n\r\n"))
    }
}

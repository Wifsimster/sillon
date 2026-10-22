package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoapTest {
    @Test
    fun `the envelope names the action in its service, arguments in order and escaped`() {
        val body = Soap.envelope(
            "urn:schemas-upnp-org:service:AVTransport:1",
            "Seek",
            listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to "<&\"'>"),
        )
        assertTrue(body.contains("<u:Seek xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"))
        assertTrue(body.contains("<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>&lt;&amp;&quot;&apos;&gt;</Target>"))
        assertTrue(body.endsWith("</u:Seek></s:Body></s:Envelope>"))
    }

    @Test
    fun `the SOAP action header is the service type and the action`() {
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Play\"", AvTransport.play().soapAction)
        assertEquals("/MediaRenderer/AVTransport/Control", AvTransport.play().service.path)
        assertEquals("/MediaRenderer/GroupRenderingControl/Control", AvTransport.groupVolume().service.path)
        assertEquals("/ZoneGroupTopology/Control", AvTransport.zoneGroupState().service.path)
    }

    @Test
    fun `a response gives its output arguments, unescaped once`() {
        val xml = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
            """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""" +
            """<u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">""" +
            "<Track>3</Track><TrackDuration>0:04:12</TrackDuration>" +
            "<TrackMetaData>&lt;DIDL-Lite&gt;&lt;/DIDL-Lite&gt;</TrackMetaData>" +
            "<TrackURI>http://192.168.1.5:32400/library/parts/1/file.flac?X-Plex-Token=t&amp;a=b</TrackURI>" +
            "<RelTime>0:01:02</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>"
        val values = Soap.values(xml)
        assertEquals("3", values["Track"])
        assertEquals("<DIDL-Lite></DIDL-Lite>", values["TrackMetaData"])
        assertEquals("http://192.168.1.5:32400/library/parts/1/file.flac?X-Plex-Token=t&a=b", values["TrackURI"])
        assertEquals("0:01:02", values["RelTime"])
    }

    @Test
    fun `a fault gives its UPnP error code and no values`() {
        val fault = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>""" +
            "<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>" +
            """<UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode></UPnPError>""" +
            "</detail></s:Fault></s:Body></s:Envelope>"
        assertEquals(701, Soap.faultCode(fault))
        assertTrue(Soap.values(fault).isEmpty())
        assertNull(Soap.faultCode("<html>500</html>"))
        assertNull(Soap.faultCode("not xml"))
    }
}

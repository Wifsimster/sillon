package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneGroupsTest {
    private val state = """<ZoneGroupState><ZoneGroups>""" +
        """<ZoneGroup Coordinator="RINCON_B" ID="RINCON_B:12">""" +
        """<ZoneGroupMember UUID="RINCON_A" Location="http://192.168.1.21:1400/xml/device_description.xml" ZoneName="Kitchen"/>""" +
        """<ZoneGroupMember UUID="RINCON_B" Location="http://192.168.1.22:1400/xml/device_description.xml" ZoneName="Living room">""" +
        """<Satellite UUID="RINCON_S" Location="http://192.168.1.23:1400/xml/device_description.xml" ZoneName="Living room" Invisible="1"/>""" +
        """</ZoneGroupMember>""" +
        """<ZoneGroupMember UUID="RINCON_SUB" Location="http://192.168.1.24:1400/xml/device_description.xml" ZoneName="Living room" Invisible="1"/>""" +
        """</ZoneGroup>""" +
        """<ZoneGroup Coordinator="RINCON_C" ID="RINCON_C:3">""" +
        """<ZoneGroupMember UUID="RINCON_C" Location="http://192.168.1.25:1400/xml/device_description.xml" ZoneName="Bedroom &amp; desk"/>""" +
        """</ZoneGroup>""" +
        """<ZoneGroup Coordinator="RINCON_GONE" ID="RINCON_GONE:1"></ZoneGroup>""" +
        """</ZoneGroups><VanishedDevices/></ZoneGroupState>"""

    @Test
    fun `groups come with their coordinator first and without hidden speakers`() {
        val groups = ZoneGroups.parse(state)
        assertEquals(listOf("Bedroom & desk", "Living room + Kitchen"), groups.map { it.name })
        val living = groups[1]
        assertEquals("RINCON_B", living.coordinator.uuid)
        assertEquals("http://192.168.1.22:1400", living.coordinator.baseUrl)
        assertEquals(listOf("RINCON_A", "RINCON_B"), living.members.map { it.uuid })
        assertEquals("RINCON_B:12", living.id)
    }

    @Test
    fun `the older layout without a ZoneGroupState wrapper reads the same`() {
        val old = state.removePrefix("<ZoneGroupState>").removeSuffix("<VanishedDevices/></ZoneGroupState>")
        assertEquals(2, ZoneGroups.parse(old).size)
    }

    @Test
    fun `nothing readable gives no groups`() {
        assertTrue(ZoneGroups.parse("").isEmpty())
        assertTrue(ZoneGroups.parse("not xml").isEmpty())
        assertTrue(ZoneGroups.parse("<ZoneGroups/>").isEmpty())
    }
}

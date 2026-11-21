package ovh.battistella.sillon.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosGroupingTest {
    private val salon = SonosZone("RINCON_A", "Salon", "http://192.168.1.21:1400")
    private val cuisine = SonosZone("RINCON_B", "Cuisine", "http://192.168.1.22:1400")
    private val chambre = SonosZone("RINCON_C", "Chambre", "http://192.168.1.23:1400")

    private val alone = listOf(
        SonosGroup("RINCON_A:1", salon, listOf(salon)),
        SonosGroup("RINCON_B:2", cuisine, listOf(cuisine)),
        SonosGroup("RINCON_C:3", chambre, listOf(chambre)),
    )

    @Test
    fun `every room is listed once, by name, whatever group it plays in`() {
        val topology = listOf(SonosGroup("RINCON_B:2", cuisine, listOf(cuisine, salon)), SonosGroup("RINCON_C:3", chambre, listOf(chambre)))
        assertEquals(listOf("Chambre", "Cuisine", "Salon"), SonosGrouping.rooms(topology).map { it.name })
    }

    @Test
    fun `the speaker already playing keeps leading while it stays picked`() {
        assertEquals(salon, SonosGrouping.leader(listOf(cuisine, salon), alone, current = salon))
    }

    @Test
    fun `without one, a room already leading a group leads`() {
        val topology = listOf(SonosGroup("RINCON_B:2", cuisine, listOf(cuisine, salon)), SonosGroup("RINCON_C:3", chambre, listOf(chambre)))
        assertEquals(cuisine, SonosGrouping.leader(listOf(salon, cuisine), topology, current = null))
    }

    @Test
    fun `three lone rooms picked: two join the first`() {
        val plan = SonosGrouping.plan(listOf(chambre, cuisine, salon), alone, chambre)
        assertTrue(plan.leave.isEmpty())
        assertEquals(listOf(cuisine, salon), plan.join)
    }

    @Test
    fun `a room unpicked from the leader's group leaves it, nothing else moves`() {
        val topology = listOf(SonosGroup("RINCON_A:1", salon, listOf(salon, cuisine, chambre)))
        val plan = SonosGrouping.plan(listOf(salon, chambre), topology, salon)
        assertEquals(listOf(cuisine), plan.leave)
        assertTrue(plan.join.isEmpty())
    }

    @Test
    fun `a room picked alone out of a group it doesn't lead stands alone first`() {
        val topology = listOf(SonosGroup("RINCON_B:2", cuisine, listOf(cuisine, salon)))
        val plan = SonosGrouping.plan(listOf(salon), topology, salon)
        assertEquals(listOf(salon), plan.leave)
        assertTrue(plan.join.isEmpty())
    }

    @Test
    fun `the group as already formed needs nothing`() {
        val topology = listOf(SonosGroup("RINCON_A:1", salon, listOf(salon, cuisine)))
        assertTrue(SonosGrouping.plan(listOf(cuisine, salon), topology, salon).isEmpty)
    }

    @Test
    fun `the formed group takes Sonos' id, the leader first in its name`() {
        val topology = listOf(SonosGroup("RINCON_A:7", salon, listOf(cuisine, salon)))
        val group = SonosGrouping.formed(listOf(cuisine, salon), topology, salon)
        assertEquals("RINCON_A:7", group.id)
        assertEquals("Salon + Cuisine", group.name)
        val asked = SonosGrouping.formed(listOf(cuisine, salon), emptyList(), salon)
        assertEquals("Salon + Cuisine", asked.name)
    }
}

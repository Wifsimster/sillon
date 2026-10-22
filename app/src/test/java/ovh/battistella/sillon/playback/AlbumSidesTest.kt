package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSidesTest {
    private fun min(m: Int, s: Int = 0) = (m * 60 + s) * 1_000L

    private fun tracks(vararg durations: Long, disc: (Int) -> Int? = { null }) =
        durations.mapIndexed { i, d -> SideTrack("t$i", d, disc(i), "Track $i") }

    private fun List<Side>.keys() = map { it.keys }

    @Test
    fun `single disc is cut at the track boundary closest to half the length`() {
        val sides = AlbumSides.split(tracks(min(4), min(5), min(6), min(3), min(4), min(5)))
        assertEquals(listOf("A", "B"), sides.map { it.label })
        assertEquals(listOf(listOf("t0", "t1", "t2"), listOf("t3", "t4", "t5")), sides.keys())
        assertEquals(min(15), sides[0].durationMs)
        assertEquals(min(12), sides[1].durationMs)
    }

    @Test
    fun `uneven tracks still cut on the closest boundary`() {
        val sides = AlbumSides.split(tracks(min(2), min(2), min(2), min(10), min(2), min(2)))
        assertEquals(listOf(listOf("t0", "t1", "t2"), listOf("t3", "t4", "t5")), sides.keys())
    }

    @Test
    fun `a very long opening track gets a side of its own`() {
        val sides = AlbumSides.split(tracks(min(30), min(2), min(2), min(2)))
        assertEquals(listOf(listOf("t0"), listOf("t1", "t2", "t3")), sides.keys())
    }

    @Test
    fun `a very long closing track gets a side of its own`() {
        val sides = AlbumSides.split(tracks(min(3), min(3), min(3), min(25)))
        assertEquals(listOf(listOf("t0", "t1", "t2"), listOf("t3")), sides.keys())
    }

    @Test
    fun `short EP is not split`() {
        assertTrue(AlbumSides.split(tracks(min(3), min(3), min(2), min(3, 30))).isEmpty())
    }

    @Test
    fun `fewer than four tracks is not split even when long`() {
        assertTrue(AlbumSides.split(tracks(min(20), min(18), min(22))).isEmpty())
    }

    @Test
    fun `album of exactly twelve minutes and four tracks is split`() {
        assertEquals(2, AlbumSides.split(tracks(min(3), min(3), min(3), min(3))).size)
    }

    @Test
    fun `double album gives sides A and B for disc 1 then C and D for disc 2`() {
        val sides = AlbumSides.split(
            tracks(min(5), min(5), min(5), min(5), min(6), min(6), min(6), min(6)) { if (it < 4) 1 else 2 },
        )
        assertEquals(listOf("A", "B", "C", "D"), sides.map { it.label })
        assertEquals(
            listOf(listOf("t0", "t1"), listOf("t2", "t3"), listOf("t4", "t5"), listOf("t6", "t7")),
            sides.keys(),
        )
    }

    @Test
    fun `box set with three discs gives six sides`() {
        val sides = AlbumSides.split(tracks(*LongArray(12) { min(5) }) { it / 4 + 1 })
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), sides.map { it.label })
        sides.forEach { assertEquals(2, it.keys.size) }
    }

    @Test
    fun `a short bonus disc stays a single side`() {
        val sides = AlbumSides.split(
            tracks(min(5), min(5), min(5), min(5), min(3), min(3)) { if (it < 4) 1 else 2 },
        )
        assertEquals(listOf("A", "B", "C"), sides.map { it.label })
        assertEquals(listOf("t4", "t5"), sides[2].keys)
    }

    @Test
    fun `a single disc number falls back to the half cut`() {
        val sides = AlbumSides.split(tracks(min(5), min(5), min(5), min(5)) { 1 })
        assertEquals(listOf(listOf("t0", "t1"), listOf("t2", "t3")), sides.keys())
    }

    @Test
    fun `missing disc numbers on some tracks fall back to the half cut`() {
        val sides = AlbumSides.split(tracks(min(5), min(5), min(5), min(5)) { if (it == 0) null else 2 })
        assertEquals(listOf("A", "B"), sides.map { it.label })
    }

    @Test
    fun `vinyl positions in titles take priority`() {
        val titles = listOf("A1 Intro", "A2. Song", "A3 - Another", "B1 Long one", "B2: Outro")
        val durations = listOf(min(2), min(2), min(2), min(10), min(2))
        val sides = AlbumSides.split(titles.indices.map { SideTrack("t$it", durations[it], 1, titles[it]) })
        assertEquals(listOf("A", "B"), sides.map { it.label })
        assertEquals(listOf(listOf("t0", "t1", "t2"), listOf("t3", "t4")), sides.keys())
    }

    @Test
    fun `side words in titles take priority over disc numbers`() {
        val titles = listOf("Opening (Side A)", "Middle [Side A]", "Interlude, face a", "Closing - Side B")
        val sides = AlbumSides.split(titles.indices.map { SideTrack("t$it", min(5), it / 2 + 1, titles[it]) })
        assertEquals(listOf("A", "B"), sides.map { it.label })
        assertEquals(listOf(listOf("t0", "t1", "t2"), listOf("t3")), sides.keys())
    }

    @Test
    fun `markers on only some tracks are ignored`() {
        val titles = listOf("A1 Intro", "Song", "Another", "Outro")
        val sides = AlbumSides.split(titles.indices.map { SideTrack("t$it", min(5), null, titles[it]) })
        assertEquals(listOf(listOf("t0", "t1"), listOf("t2", "t3")), sides.keys())
    }

    @Test
    fun `side markers recognise vinyl positions but not ordinary words`() {
        assertEquals('C', AlbumSides.sideMarker("C4 Finale"))
        assertEquals('B', AlbumSides.sideMarker("Reprise (side b)"))
        assertNull(AlbumSides.sideMarker("Walk on the Wild Side"))
        assertNull(AlbumSides.sideMarker("B52s"))
        assertNull(AlbumSides.sideMarker("a1 lowercase"))
    }

    @Test
    fun `unknown durations count as zero and keep the album from splitting`() {
        assertTrue(AlbumSides.split(List(6) { SideTrack("t$it", null) }).isEmpty())
    }

    @Test
    fun `labels go past Z with numbers`() {
        assertEquals("A", AlbumSides.label(0))
        assertEquals("Z", AlbumSides.label(25))
        assertEquals("27", AlbumSides.label(26))
    }

    @Test
    fun `plan keeps only the playable tracks and drops empty sides`() {
        val sides = listOf(
            Side("A", listOf("a1", "a2"), 0),
            Side("B", listOf("b1", "b2"), 0),
            Side("C", listOf("c1"), 0),
        )
        val plan = SidePlan.of(sides, listOf("a1", "a2", "c1"))!!
        assertEquals(listOf(0, 0, 1), plan.sides)
        assertEquals(listOf("A", "C"), plan.labels)
    }

    @Test
    fun `plan is refused for a foreign or reordered queue`() {
        val sides = listOf(Side("A", listOf("a1", "a2"), 0), Side("B", listOf("b1", "b2"), 0))
        assertNull(SidePlan.of(sides, listOf("a1", "x", "b1")))
        assertNull(SidePlan.of(sides, listOf("b1", "a1")))
        assertNull(SidePlan.of(sides, listOf("a1", "a2")))
        assertNull(SidePlan.of(emptyList(), listOf("a1")))
    }
}

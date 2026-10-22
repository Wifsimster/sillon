package ovh.battistella.sillon.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRulesTest {
    @Test
    fun `a short note is kept as typed, on one line`() {
        assertEquals("Le disque de l'été 1994", CardRules.note("  Le disque\nde l'été   1994 "))
    }

    @Test
    fun `an empty note puts nothing on the card`() {
        assertNull(CardRules.note(null))
        assertNull(CardRules.note("   \n "))
    }

    @Test
    fun `a long note is cut at 140 characters`() {
        val note = CardRules.note("a".repeat(200))!!
        assertEquals(CardRules.NOTE_MAX, note.length)
    }

    @Test
    fun `a note cut on a space loses the trailing space`() {
        val note = CardRules.note("b".repeat(139) + " suite")!!
        assertEquals("b".repeat(139), note)
    }

    @Test
    fun `the cut never splits an emoji`() {
        // 139 letters, then a two-code-unit emoji: it fits in 140 code points.
        val text = "x".repeat(139) + "🎶" + "y"
        assertEquals("x".repeat(139) + "🎶", CardRules.limit(text))
        // 139 letters, then a family (man ZWJ woman): the sequence would be cut, so it goes.
        val family = "x".repeat(139) + "👨‍👩" + "z"
        assertEquals("x".repeat(139), CardRules.limit(family))
        // A skin tone right after the limit takes its hand away with it.
        val wave = "x".repeat(139) + "👋🏽"
        assertEquals("x".repeat(139), CardRules.limit(wave))
    }

    @Test
    fun `typing stops at 140 characters`() {
        val typed = "é".repeat(150)
        assertEquals(140, CardRules.limit(typed).length)
        assertEquals("court", CardRules.limit("court"))
    }

    @Test
    fun `at most two lyric lines, in song order`() {
        var chosen = CardRules.toggleLyric(emptyList(), 7)
        chosen = CardRules.toggleLyric(chosen, 2)
        assertEquals(listOf(2, 7), chosen)
        assertEquals(listOf(2, 7), CardRules.toggleLyric(chosen, 4))
        assertEquals(listOf(7), CardRules.toggleLyric(chosen, 2))
    }

    @Test
    fun `stars round to halves and stay between 0 and 5`() {
        assertEquals(4.5f, CardRules.stars(4.4f))
        assertEquals(5f, CardRules.stars(7f))
        assertNull(CardRules.stars(0f))
        assertNull(CardRules.stars(null))
    }

    @Test
    fun `an artist's years span their albums`() {
        assertEquals("1971 – 1994", CardRules.yearSpan(listOf(1994, 1971, 1980)))
        assertEquals("2001", CardRules.yearSpan(listOf(2001, 2001)))
        assertNull(CardRules.yearSpan(listOf(0)))
        assertTrue(CardRules.yearSpan(emptyList()) == null)
    }
}

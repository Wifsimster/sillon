package ovh.battistella.sillon.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.settings.ThemeAccent
import ovh.battistella.sillon.data.settings.ThemeMode
import ovh.battistella.sillon.data.settings.ThemePreferences

class ThemeColorsTest {
    @Test
    fun `amber is the brand's own scheme`() {
        assertEquals(Brand.Label, sillonColorScheme(dark = true).primary)
        assertEquals(Color(0xFF8A5100), sillonColorScheme(dark = false).primary)
        assertEquals(Brand.Vinyl, sillonColorScheme(dark = true).background)
    }

    @Test
    fun `an accent changes the primary role only`() {
        val amber = sillonColorScheme(dark = true)
        val teal = sillonColorScheme(dark = true, accent = ThemeAccent.TEAL)
        assertEquals(Color(ThemeAccent.TEAL.dark.primary), teal.primary)
        assertNotEquals(amber.primary, teal.primary)
        assertEquals(amber.secondary, teal.secondary)
        assertEquals(amber.background, teal.background)
    }

    @Test
    fun `pure black only darkens the dark scheme`() {
        assertEquals(Color.Black, sillonColorScheme(dark = true, pureBlack = true).background)
        assertEquals(sillonColorScheme(dark = false).background, sillonColorScheme(dark = false, pureBlack = true).background)
    }

    @Test
    fun `every accent stays readable on its background`() {
        ThemeAccent.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                listOf(false, true).forEach { black ->
                    val scheme = sillonColorScheme(dark, accent, black)
                    val ratio = contrast(scheme.primary, scheme.background)
                    assertTrue("$accent dark=$dark black=$black: $ratio", ratio >= 4.5f)
                    assertTrue("$accent on-primary dark=$dark", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
                }
            }
        }
    }

    @Test
    fun `the mode decides light or dark`() {
        assertTrue(ThemePreferences(mode = ThemeMode.SYSTEM).isDark(systemDark = true))
        assertTrue(!ThemePreferences(mode = ThemeMode.SYSTEM).isDark(systemDark = false))
        assertTrue(ThemePreferences(mode = ThemeMode.DARK).isDark(systemDark = false))
        assertTrue(!ThemePreferences(mode = ThemeMode.LIGHT).isDark(systemDark = true))
    }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }
}

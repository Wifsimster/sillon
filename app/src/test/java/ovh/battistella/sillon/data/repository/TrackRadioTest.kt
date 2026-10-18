package ovh.battistella.sillon.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRadioTest {

    @Test
    fun `splits a track credit into performers`() {
        assertEquals(listOf("will.i.am", "Britney Spears"), performers("will.i.am/Britney Spears"))
        assertEquals(listOf("Linkin Park", "Aceyalone"), performers("Linkin Park feat. Aceyalone"))
        assertEquals(listOf("Simon", "Garfunkel"), performers("Simon & Garfunkel"))
        assertEquals(emptyList<String>(), performers(null))
    }

    @Test
    fun `recognises compilation album artists`() {
        assertTrue(isCompilationArtist("Various Artists"))
        assertTrue(isCompilationArtist("Artistes divers"))
        assertFalse(isCompilationArtist("Wolfmother"))
        assertFalse(isCompilationArtist(null))
    }
}

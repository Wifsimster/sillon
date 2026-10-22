package ovh.battistella.sillon.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {
    @Test
    fun `timed lyrics lose their timings and tags`() {
        val lrc = """
            [ar:Synthetic Artist]
            [ti:Synthetic Song]
            [00:12.30]First line of the song
            [00:15.10][01:15.10]The chorus <00:15.50>comes back
            [00:18.00]
            [00:19.00]Last line
        """.trimIndent()
        assertEquals(listOf("First line of the song", "The chorus comes back", "Last line"), Lyrics.parse(lrc))
    }

    @Test
    fun `plain lyrics keep their lines and drop the blank ones`() {
        assertEquals(listOf("One", "Two"), Lyrics.parse("One\n\n  Two  \n"))
    }

    @Test
    fun `a line repeated right after itself is kept once`() {
        assertEquals(listOf("La la la", "Fin"), Lyrics.parse("La la la\nLa la la\nFin"))
    }

    @Test
    fun `reads Jellyfin's lyrics`() {
        val body = """{"Metadata": {"Artist": "Synthetic", "IsSynced": true},
            "Lyrics": [{"Text": "First line", "Start": 120000000}, {"Text": "", "Start": 1}, {"Text": "Second line", "Start": 150000000}]}"""
        assertEquals(listOf("First line", "Second line"), Lyrics.parse(body))
    }

    @Test
    fun `reads Plex's structured lyrics`() {
        val body = """{"MediaContainer": {"size": 1, "Lyrics": [{"Line": [
            {"Span": [{"text": "Hello "}, {"text": "world"}]},
            {"Span": [{"text": "Second"}]}
        ]}]}}"""
        assertEquals(listOf("Hello world", "Second"), Lyrics.parse(body))
    }

    @Test
    fun `reads Plex's lyrics as XML`() {
        val body = """<MediaContainer><Lyrics><Line startOffset="1"><Span text="Rock &amp; roll"/></Line><Line><Span text="again"/></Line></Lyrics></MediaContainer>"""
        assertEquals(listOf("Rock & roll", "again"), Lyrics.parse(body))
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals(emptyList<String>(), Lyrics.parse(null))
        assertEquals(emptyList<String>(), Lyrics.parse("  "))
        assertEquals(emptyList<String>(), Lyrics.parse("{not json"))
    }
}

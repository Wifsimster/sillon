package ovh.battistella.sillon.playback.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TranscodeUrlsTest {
    private val known = setOf("abc")

    @Test
    fun `a Plex transcode names its session in the query, then in the path`() {
        val start = "https://10-0-0-2.x.plex.direct:32400/music/:/transcode/universal/start.m3u8?session=abc&X-Plex-Token=secret"
        val playlist = "https://10-0-0-2.x.plex.direct:32400/music/:/transcode/universal/session/abc/base/index.m3u8"
        val segment = "https://10-0-0-2.x.plex.direct:32400/music/:/transcode/universal/session/abc/base/00003.ts"
        assertEquals("abc", TranscodeUrls.sessionIn(start, known::contains))
        assertEquals("abc", TranscodeUrls.sessionIn(segment, known::contains))
        assertEquals("start.m3u8", TranscodeUrls.partOf(start, "abc"))
        assertEquals("session/base/index.m3u8", TranscodeUrls.partOf(playlist, "abc"))
        assertEquals("session/base/00003.ts", TranscodeUrls.partOf(segment, "abc"))
    }

    @Test
    fun `a Jellyfin transcode names its session in the query`() {
        val main = "https://music.example/jf/Audio/42/main.m3u8?playSessionId=abc&api_key=secret"
        val segment = "https://music.example/jf/Audio/42/hls1/main/7.ts?playSessionId=abc&api_key=secret"
        assertEquals("abc", TranscodeUrls.sessionIn(segment, known::contains))
        assertEquals("main.m3u8", TranscodeUrls.partOf(main, "abc"))
        assertEquals("hls1/main/7.ts", TranscodeUrls.partOf(segment, "abc"))
    }

    @Test
    fun `the part never holds a host, a token or the session`() {
        val part = TranscodeUrls.partOf("https://host.example/music/:/transcode/universal/session/abc/base/1.ts?X-Plex-Token=secret", "abc")!!
        listOf("host", "secret", "Token", "abc", "http").forEach { assertFalse(part.contains(it)) }
    }

    @Test
    fun `an unknown session, an original or a file is not a transcode`() {
        assertNull(TranscodeUrls.sessionIn("https://server/music/:/transcode/universal/session/zzz/base/1.ts", known::contains))
        assertNull(TranscodeUrls.sessionIn("https://server/library/parts/1/file.flac?X-Plex-Token=t", known::contains))
        assertNull(TranscodeUrls.sessionIn("file:///data/1.flac", known::contains))
    }
}

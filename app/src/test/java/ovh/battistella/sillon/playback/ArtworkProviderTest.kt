package ovh.battistella.sillon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtworkProviderTest {

    @Test
    fun `a cover URI names the cover, never a URL with the token`() {
        val thumb = "/library/metadata/100/thumb/1700000000"
        val uri = ArtworkProvider.uriFor(thumb)!!

        assertEquals("content", uri.scheme)
        assertEquals(ArtworkProvider.AUTHORITY, uri.authority)
        assertFalse(uri.toString().contains("Token"))
        assertEquals(thumb, ArtworkProvider.thumbOf(uri))
    }

    @Test
    fun `Jellyfin cover references round trip too`() {
        val uri = ArtworkProvider.uriFor("jellyfin-item:a1")!!
        assertEquals("jellyfin-item:a1", ArtworkProvider.thumbOf(uri))
    }

    @Test
    fun `no cover, no URI`() {
        assertNull(ArtworkProvider.uriFor(null))
        assertNull(ArtworkProvider.uriFor(""))
    }
}

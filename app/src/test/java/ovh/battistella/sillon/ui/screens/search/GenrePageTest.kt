package ovh.battistella.sillon.ui.screens.search

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.genres.GenreAlbum
import ovh.battistella.sillon.data.genres.GenreView
import ovh.battistella.sillon.data.genres.Genres

/** What a genre page shows online and offline. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GenrePageTest {
    private val albums = listOf(
        GenreAlbum("1", "One", "Demo Band", "ar1", "/t/1", "/a/1", listOf("Rock")),
        GenreAlbum("2", "Two", "Demo Band", "ar1", "/t/2", "/a/1", listOf("Rock")),
    )
    private val art = { thumb: String? -> thumb?.let { "https://demo$it" } }

    @Test
    fun `online, artists and albums with server covers`() {
        val genre = Genres.group(albums).single()
        val ui = genrePage(genre, GenreView(listOf(genre), online = true), art)
        assertEquals("Rock", ui.label)
        assertEquals(listOf("Demo Band"), ui.artists.map { it.title })
        assertEquals("https://demo/a/1", ui.artists.single().artworkUrl)
        assertEquals(listOf("https://demo/t/1", "https://demo/t/2"), ui.albums.map { it.artworkUrl })
        assertTrue(ui.found)
    }

    @Test
    fun `offline, no artist row, and the saved cover first`() {
        val genre = Genres.group(albums, onPhone = setOf("1")).single()
        val cover = java.io.File.createTempFile("cover", ".jpg").apply { deleteOnExit() }
        val ui = genrePage(genre, GenreView(listOf(genre), online = false, onPhone = mapOf("1" to cover.path)), art)
        assertEquals(emptyList<Any>(), ui.artists)
        assertEquals(listOf("1"), ui.albums.map { it.key })
        assertTrue(ui.albums.single().artworkUrl!!.startsWith("file:"))
        assertFalse(ui.online)
    }

    @Test
    fun `a genre gone from the library says so`() {
        val ui = genrePage(null, GenreView(online = true, known = true), art)
        assertFalse(ui.found)
        assertFalse(ui.loading)
    }
}

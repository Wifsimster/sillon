package ovh.battistella.sillon.data.genres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Spellings merged, real genres kept apart, the order of the tiles, offline and the surprise. */
class GenresTest {
    private fun album(key: String, vararg genres: String, artist: String = "Demo Artist", artistKey: String? = "a-$artist") =
        GenreAlbum(key, "Album $key", artist, artistKey, "/thumb/$key", "/artist/$artistKey", genres.toList())

    @Test
    fun `spellings of one genre share a key`() {
        assertEquals("rock", Genres.normalize("Rock"))
        assertEquals("rock", Genres.normalize("  rock "))
        assertEquals("heavy metal", Genres.normalize("Heavy-Metal"))
        assertEquals("heavy metal", Genres.normalize("Heavy  Metal"))
        assertEquals("heavy metal", Genres.normalize("heavy_metal"))
        assertEquals("metal", Genres.normalize("Métal"))
        assertEquals("hip hop", Genres.normalize("Hip–Hop"))
        assertEquals("electronique", Genres.normalize("Électronique"))
    }

    @Test
    fun `different genres stay apart`() {
        assertNotEquals(Genres.normalize("Metal"), Genres.normalize("Heavy Metal"))
        assertNotEquals(Genres.normalize("Rock"), Genres.normalize("Hard Rock"))
        assertNotEquals(Genres.normalize("Pop"), Genres.normalize("K-Pop"))
    }

    @Test
    fun `duplicates from a real library merge, under the spelling most albums use`() {
        val albums = listOf(
            album("1", "Rock"),
            album("2", "rock"),
            album("3", "Rock"),
            album("4", "Heavy Metal"),
            album("5", "Heavy-Metal"),
            album("6", "Heavy Metal"),
            album("7", "Métal"),
            album("8", "Metal"),
            album("9", "Metal"),
        )
        val genres = Genres.group(albums)
        assertEquals(listOf("Heavy Metal", "Metal", "Rock"), genres.map { it.label })
        assertEquals(listOf(3, 3, 3), genres.map { it.albums.size })
        assertEquals(setOf("7", "8", "9"), genres.single { it.label == "Metal" }.albums.map { it.key }.toSet())
    }

    @Test
    fun `an album tagged twice with one genre counts once`() {
        val genres = Genres.group(listOf(album("1", "Rock", "rock", "ROCK"), album("2", "Jazz")))
        assertEquals(listOf(1, 1), genres.map { it.albums.size })
        assertEquals("Rock", genres.first { it.key == "rock" }.label)
    }

    @Test
    fun `an album lands in each of its genres`() {
        val genres = Genres.group(listOf(album("1", "Metal", "Heavy Metal"), album("2", "Metal")))
        assertEquals(listOf("Metal" to 2, "Heavy Metal" to 1), genres.map { it.label to it.albums.size })
    }

    @Test
    fun `most albums first, then by name`() {
        val albums = listOf(
            album("1", "Jazz"),
            album("2", "Blues"),
            album("3", "rock"),
            album("4", "Rock"),
            album("5", "Rock"),
            album("6", "ambient"),
        )
        assertEquals(listOf("Rock", "ambient", "Blues", "Jazz"), Genres.group(albums).map { it.label })
    }

    @Test
    fun `blank genres and albums listed twice are ignored`() {
        val genres = Genres.group(listOf(album("1", " ", ""), album("2", "Folk"), album("2", "Folk")))
        assertEquals(listOf("Folk" to 1), genres.map { it.label to it.albums.size })
    }

    @Test
    fun `offline, only downloaded albums count, and a genre without one goes`() {
        val albums = listOf(album("1", "Rock"), album("2", "Rock"), album("3", "Jazz"), album("4", "Jazz"), album("5", "Jazz"))
        val genres = Genres.group(albums, onPhone = setOf("1", "2"))
        assertEquals(listOf("Rock" to 2), genres.map { it.label to it.albums.size })
        assertEquals(emptyList<Genre>(), Genres.group(albums, onPhone = emptySet()))
    }

    @Test
    fun `an unrevealed surprise is in no genre and counts nowhere`() {
        val albums = listOf(album("secret", "Rock", "Jazz"), album("2", "Rock"), album("3", "Rock"))
        val genres = Genres.group(albums, hidden = setOf("secret"))
        assertEquals(listOf("Rock" to 2), genres.map { it.label to it.albums.size })
        assertTrue(genres.flatMap { it.albums }.none { it.key == "secret" })
    }

    @Test
    fun `the surprise stays out offline too`() {
        val albums = listOf(album("secret", "Rock"), album("2", "Rock"))
        val genres = Genres.group(albums, hidden = setOf("secret"), onPhone = setOf("secret", "2"))
        assertEquals(listOf("2"), genres.single().albums.map { it.key })
    }

    @Test
    fun `artists of a genre, most albums first, without compilations`() {
        val genre = Genres.group(
            listOf(
                album("1", "Rock", artist = "Zed"),
                album("2", "Rock", artist = "Ann"),
                album("3", "Rock", artist = "Zed"),
                album("4", "Rock", artist = "Various Artists"),
                album("5", "Rock", artist = "Nobody", artistKey = null),
            ),
        ).single()
        assertEquals(listOf("Zed", "Ann"), Genres.artists(genre).map { it.name })
        assertEquals("/artist/a-Zed", Genres.artists(genre).first().thumb)
    }

    @Test
    fun `albums of a genre are sorted by artist then title`() {
        val genre = Genres.group(
            listOf(album("b", "Rock", artist = "Beta"), album("a2", "Rock", artist = "alpha"), album("a1", "Rock", artist = "Alpha")),
        ).single()
        assertEquals(listOf("a1", "a2", "b"), genre.albums.map { it.key })
    }

    @Test
    fun `a genre keeps its colour, from the palette`() {
        assertEquals(Genres.colorOf("rock"), Genres.colorOf("rock"))
        assertTrue(Genres.colorOf("heavy metal") in Genres.PALETTE)
        // Spellings share a key, so they share a colour.
        assertEquals(Genres.colorOf(Genres.normalize("Métal")), Genres.colorOf(Genres.normalize("metal")))
    }

    @Test
    fun `a shuffle draws from a handful of the genre's albums`() {
        val genre = Genres.group((1..40).map { album("$it", "Rock") }).single()
        val drawn = Genres.shuffleAlbums(genre, Random(7))
        assertEquals(Genres.SHUFFLE_ALBUMS, drawn.size)
        assertEquals(drawn.size, drawn.map { it.key }.toSet().size)
    }
}

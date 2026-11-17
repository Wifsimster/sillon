package ovh.battistella.sillon.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.desktop.ui.screens.ShelfAlbum
import ovh.battistella.sillon.desktop.ui.screens.ShelfKind
import ovh.battistella.sillon.desktop.ui.screens.Shelves
import java.time.LocalDate

class ShelvesTest {
    private var next = 0

    private fun album(title: String, artist: String = "Artist ${next % 7}", genres: List<String> = emptyList(), year: Int? = null) =
        ShelfAlbum("k${next++}", title, artist, null, genres, year)

    private val library = buildList {
        add(album("A Charlie Brown Christmas", genres = listOf("Jazz")))
        add(album("Noël ensemble"))
        add(album("Merry Xmas Everybody"))
        add(album("Lady Sings the Blues", artist = "Billie Holiday"))
        add(album("Thriller", genres = listOf("Pop")))
        add(album("The Nightmare Before Christmas", genres = listOf("Soundtrack")))
        add(album("Halloween (Original Motion Picture Soundtrack)", genres = listOf("Soundtrack")))
        add(album("Bela Lugosi's Dead", genres = listOf("Gothic Rock")))
        add(album("Monster Mash"))
        add(album("Blade Runner", genres = listOf("Soundtrack")))
        add(album("Amélie", genres = listOf("Bande originale")))
        repeat(6) { add(album("Nineties $it", artist = "Air", genres = listOf("Electronic"), year = 1990 + it)) }
        repeat(5) { add(album("Bop $it", artist = "Miles Davis", genres = listOf("Jazz"), year = 1955 + it)) }
        repeat(4) { add(album("Hits $it", artist = "Various Artists")) }
    }

    private fun kinds(date: LocalDate) = Shelves.of(library, date).map { it.kind }

    @Test
    fun `christmas comes first in december, and only then`() {
        val december = Shelves.of(library, LocalDate.of(2026, 12, 10))
        assertEquals(ShelfKind.CHRISTMAS, december.first().kind)
        val titles = december.first().albums.map { it.title }.toSet()
        assertEquals(setOf("A Charlie Brown Christmas", "Noël ensemble", "Merry Xmas Everybody", "The Nightmare Before Christmas"), titles)
        assertFalse(ShelfKind.CHRISTMAS in kinds(LocalDate.of(2026, 11, 17)))
        assertFalse(ShelfKind.CHRISTMAS in kinds(LocalDate.of(2026, 12, 27)))
    }

    @Test
    fun `halloween brings the horror around the end of october`() {
        val shelf = Shelves.of(library, LocalDate.of(2026, 10, 31)).first()
        assertEquals(ShelfKind.HALLOWEEN, shelf.kind)
        val titles = shelf.albums.map { it.title }.toSet()
        assertTrue(titles.containsAll(setOf("Thriller", "The Nightmare Before Christmas", "Bela Lugosi's Dead", "Monster Mash")))
        assertTrue(titles.any { it.startsWith("Halloween") })
        assertFalse(ShelfKind.HALLOWEEN in kinds(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `words are matched whole, so an artist's name is not a theme`() {
        val december = Shelves.of(library, LocalDate.of(2026, 12, 10)).first()
        assertFalse(december.albums.any { it.artist == "Billie Holiday" })
    }

    @Test
    fun `a day has its decade, genre, artist and a row to rediscover`() {
        val shelves = Shelves.of(library, LocalDate.of(2026, 11, 17))
        val decade = shelves.single { it.kind == ShelfKind.DECADE }
        assertTrue(decade.label in setOf("1950", "1990"))
        assertTrue(decade.albums.all { it.year!! / 10 * 10 == decade.label!!.toInt() })
        val artist = shelves.single { it.kind == ShelfKind.ARTIST }
        assertTrue(artist.label in setOf("Air", "Miles Davis") || artist.label!!.startsWith("Artist "))
        assertNotEquals("Various Artists", artist.label)
        assertTrue(shelves.any { it.kind == ShelfKind.GENRE })
        assertEquals(ShelfKind.REDISCOVER, shelves.last().kind)
        assertTrue(shelves.all { it.albums.size <= Shelves.SIZE && it.albums.map { a -> a.key }.toSet().size == it.albums.size })
    }

    @Test
    fun `the same day gives the same rows, another day may not`() {
        val day = LocalDate.of(2026, 11, 17)
        assertEquals(Shelves.of(library, day), Shelves.of(library, day))
        val week = (0..6L).map { Shelves.of(library, day.plusDays(it)).map { s -> s.id to s.albums.map { a -> a.key } } }
        assertTrue(week.toSet().size > 1)
    }

    @Test
    fun `an empty or tiny library gets no rows`() {
        assertEquals(emptyList<Any>(), Shelves.of(emptyList(), LocalDate.of(2026, 12, 10)))
        assertEquals(emptyList<Any>(), Shelves.of(listOf(album("One"), album("Two")), LocalDate.of(2026, 11, 17)))
    }
}

package ovh.battistella.sillon.desktop.ui.screens

import ovh.battistella.sillon.data.genres.Genres
import ovh.battistella.sillon.data.repository.isCompilationArtist
import java.time.LocalDate
import java.time.MonthDay
import kotlin.random.Random

data class ShelfAlbum(
    val key: String,
    val title: String,
    val artist: String?,
    val thumb: String?,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
)

enum class ShelfKind { HALLOWEEN, CHRISTMAS, LOVE, SUMMER, SOUNDTRACKS, LIVE, CHILL, PARTY, FOCUS, DECADE, GENRE, ARTIST, REDISCOVER }

data class Shelf(val kind: ShelfKind, val label: String?, val albums: List<ShelfAlbum>) {
    val id: String get() = "${kind.name}|${label.orEmpty()}"
}

object Shelves {
    const val SIZE = 20
    private const val MIN_THEME = 4
    private const val MIN_ARTIST = 3
    private const val THEMES_A_DAY = 2

    private class Theme(val kind: ShelfKind, val genres: List<String>, titles: List<String>, val from: MonthDay? = null, val to: MonthDay? = null) {
        val title: Regex? = titles.takeIf { it.isNotEmpty() }?.joinToString("|") { Regex.escape(it) }?.let { Regex("(?<![a-z0-9])($it)(?![a-z0-9])") }

        fun inSeason(date: LocalDate): Boolean {
            val start = from ?: return false
            val end = to ?: return false
            val day = MonthDay.from(date)
            return !day.isBefore(start) && !day.isAfter(end)
        }

        fun matches(album: ShelfAlbum): Boolean {
            if (genres.isNotEmpty() && album.genres.any { genre -> Genres.normalize(genre).let { g -> genres.any { it in g } } }) return true
            return title?.containsMatchIn(Genres.normalize(album.title)) == true
        }
    }

    private val seasonal = listOf(
        Theme(
            ShelfKind.HALLOWEEN,
            genres = listOf("horror", "halloween", "horreur", "gothic", "goth", "darkwave", "dark ambient", "psychobilly", "doom"),
            titles = listOf(
                "halloween", "horror", "horreur", "monster", "monsters", "monstre", "monstres", "zombie", "zombies", "vampire", "vampires",
                "ghost", "ghosts", "fantome", "fantomes", "haunted", "nightmare", "nightmares", "cauchemar", "cauchemars", "witch", "witches",
                "sorciere", "sorcieres", "spooky", "creepy", "dracula", "frankenstein", "thriller",
            ),
            from = MonthDay.of(10, 18),
            to = MonthDay.of(11, 2),
        ),
        Theme(
            ShelfKind.CHRISTMAS,
            genres = listOf("christmas", "noel", "xmas", "weihnacht"),
            titles = listOf("christmas", "xmas", "noel", "noels", "navidad", "natale", "weihnachten", "weihnacht", "santa", "santa claus", "jingle", "winter wonderland"),
            from = MonthDay.of(12, 1),
            to = MonthDay.of(12, 26),
        ),
        Theme(
            ShelfKind.LOVE,
            genres = listOf("love song", "romantic", "romantique", "chanson d amour"),
            titles = listOf("love", "love songs", "amour", "amours", "amore", "amor", "valentine", "romance", "romantic", "je t aime"),
            from = MonthDay.of(2, 8),
            to = MonthDay.of(2, 14),
        ),
        Theme(
            ShelfKind.SUMMER,
            genres = listOf("reggae", "surf", "bossa", "latin", "tropical", "ska", "calypso", "zouk", "samba", "salsa"),
            titles = listOf("summer", "ete", "sun", "sunshine", "soleil", "beach", "plage", "tropical", "verano", "holiday", "vacances"),
            from = MonthDay.of(6, 21),
            to = MonthDay.of(8, 31),
        ),
    )

    private val themes = listOf(
        Theme(
            ShelfKind.SOUNDTRACKS,
            genres = listOf("soundtrack", "bande originale", "score", "film", "musical", "game"),
            titles = listOf("soundtrack", "original motion picture", "bande originale", "original score", "ost", "original soundtrack"),
        ),
        Theme(ShelfKind.LIVE, genres = listOf("live"), titles = listOf("live", "en concert", "in concert", "unplugged", "live at", "en public")),
        Theme(
            ShelfKind.CHILL,
            genres = listOf("ambient", "chill", "downtempo", "lo fi", "lofi", "lounge", "trip hop", "new age", "easy listening", "acoustic", "folk"),
            titles = emptyList(),
        ),
        Theme(
            ShelfKind.PARTY,
            genres = listOf("dance", "disco", "funk", "house", "electro", "techno", "edm", "eurodance", "party", "club"),
            titles = listOf("party", "dancefloor", "club"),
        ),
        Theme(
            ShelfKind.FOCUS,
            genres = listOf("classical", "classique", "piano", "instrumental", "baroque", "minimal", "post rock", "jazz"),
            titles = emptyList(),
        ),
    )

    fun of(albums: List<ShelfAlbum>, date: LocalDate, seed: Long = date.toEpochDay()): List<Shelf> {
        val random = Random(seed)
        val shelves = mutableListOf<Shelf>()

        fun shelf(kind: ShelfKind, label: String?, matching: List<ShelfAlbum>) {
            shelves += Shelf(kind, label, matching.shuffled(random).take(SIZE))
        }

        seasonal.filter { it.inSeason(date) }.forEach { theme ->
            val matching = albums.filter(theme::matches)
            if (matching.size >= MIN_ARTIST) shelf(theme.kind, null, matching)
        }

        themes.map { it to albums.filter(it::matches) }
            .filter { it.second.size >= MIN_THEME }
            .shuffled(random)
            .take(THEMES_A_DAY)
            .forEach { (theme, matching) -> shelf(theme.kind, null, matching) }

        albums.filter { it.year != null && it.year in 1900..2100 }
            .groupBy { it.year!! / 10 * 10 }
            .filterValues { it.size >= MIN_THEME }
            .entries.sortedBy { it.key }
            .takeIf { it.isNotEmpty() }
            ?.let { decades -> decades[random.nextInt(decades.size)] }
            ?.let { (decade, matching) -> shelf(ShelfKind.DECADE, decade.toString(), matching) }

        val byGenre = LinkedHashMap<String, Pair<String, MutableList<ShelfAlbum>>>()
        albums.forEach { album ->
            album.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { Genres.normalize(it) }.forEach { name ->
                byGenre.getOrPut(Genres.normalize(name)) { name to mutableListOf() }.second += album
            }
        }
        byGenre.values.filter { it.second.size >= MIN_THEME }
            .takeIf { it.isNotEmpty() }
            ?.let { it[random.nextInt(it.size)] }
            ?.let { (name, matching) -> shelf(ShelfKind.GENRE, name, matching) }

        albums.filter { !it.artist.isNullOrBlank() && !isCompilationArtist(it.artist) }
            .groupBy { it.artist!! }
            .filterValues { it.size >= MIN_ARTIST }
            .entries.sortedBy { it.key }
            .takeIf { it.isNotEmpty() }
            ?.let { it[random.nextInt(it.size)] }
            ?.let { (artist, matching) -> shelf(ShelfKind.ARTIST, artist, matching) }

        if (albums.size >= MIN_THEME) shelf(ShelfKind.REDISCOVER, null, albums)

        return shelves.filter { it.albums.isNotEmpty() }
    }
}

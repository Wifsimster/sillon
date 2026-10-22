package ovh.battistella.sillon.data.genres

import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.jellyfin.JELLYFIN_IMAGE
import ovh.battistella.sillon.data.jellyfin.JellyfinItem
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.isCompilationArtist
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/** An album with its genres as the server spells them. [thumb] and [artistThumb] are server paths, never URLs with a token. */
@Serializable
data class GenreAlbum(
    val key: String,
    val title: String,
    val artist: String? = null,
    val artistKey: String? = null,
    val thumb: String? = null,
    val artistThumb: String? = null,
    val genres: List<String> = emptyList(),
)

/** The albums of the library with their genres, on disk per server and account. */
@Serializable
data class CachedGenres(
    val owner: String,
    val albums: List<GenreAlbum> = emptyList(),
    val refreshedAt: Long = 0,
)

/** One genre once the spellings are merged: [key] is the normalised name, [label] the spelling most albums use. */
data class Genre(
    val key: String,
    val label: String,
    val albums: List<GenreAlbum>,
)

data class GenreArtist(val key: String, val name: String, val thumb: String?)

object Genres {
    /** Genres shown on the search page before "All genres". */
    const val VISIBLE = 12

    /** Albums a genre shuffle draws its tracks from, and the tracks it keeps. */
    const val SHUFFLE_ALBUMS = 12
    const val SHUFFLE_TRACKS = 100

    const val PAGE_SIZE = 1_000

    private const val PLEX_ALBUM = "9"

    /**
     * Plex: every album of the section with its `Genre` tags, one page at a time. Plex keeps genres on
     * albums (and artists), not on tracks, so albums are what a genre is counted in.
     */
    fun plexAlbumsQuery(start: Int, size: Int = PAGE_SIZE): List<Pair<String, String>> = listOf(
        "type" to PLEX_ALBUM,
        "sort" to "titleSort",
        "X-Plex-Container-Start" to start.toString(),
        "X-Plex-Container-Size" to size.toString(),
    )

    /** Jellyfin: every album of the library with its `Genres` (not a default field). */
    fun jellyfinAlbumsQuery(sectionKey: String, start: Int, limit: Int = PAGE_SIZE): List<Pair<String, String>> = listOf(
        "parentId" to sectionKey,
        "includeItemTypes" to "MusicAlbum",
        "recursive" to "true",
        "fields" to "Genres",
        "sortBy" to "SortName",
        "enableUserData" to "false",
        "startIndex" to start.toString(),
        "limit" to limit.toString(),
    )

    fun fromPlex(album: PlexMetadata): GenreAlbum = GenreAlbum(
        key = album.ratingKey,
        title = album.title,
        artist = album.parentTitle,
        artistKey = album.parentRatingKey,
        thumb = album.thumb,
        artistThumb = album.parentThumb,
        genres = album.genres.map { it.tag },
    )

    fun fromJellyfin(album: JellyfinItem): GenreAlbum {
        val artist = album.albumArtists.firstOrNull()
        return GenreAlbum(
            key = album.id,
            title = album.name,
            artist = artist?.name ?: album.albumArtist,
            artistKey = artist?.id,
            thumb = if (album.imageTags.containsKey("Primary")) "$JELLYFIN_IMAGE${album.id}" else null,
            // Jellyfin serves an artist's picture by the artist's id; the cover placeholder shows when it has none.
            artistThumb = artist?.id?.let { "$JELLYFIN_IMAGE$it" },
            genres = album.genres,
        )
    }

    /**
     * The key two spellings of one genre share: lower case, without accents, hyphens or underscores, and
     * single spaces. "Heavy-Metal" and "heavy metal" meet; "Metal" and "Heavy Metal" stay apart.
     */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(SEPARATORS, " ")
            .replace(SPACES, " ")
            .trim()

    /**
     * The genres of [albums], spellings merged, most albums first. [hidden] albums (an unrevealed
     * surprise) count nowhere; with [onPhone], only those albums count (offline).
     */
    fun group(albums: List<GenreAlbum>, hidden: Set<String> = emptySet(), onPhone: Set<String>? = null): List<Genre> {
        val members = LinkedHashMap<String, MutableList<GenreAlbum>>()
        val spellings = HashMap<String, MutableMap<String, Int>>()
        albums.asSequence()
            .filter { it.key !in hidden && (onPhone == null || it.key in onPhone) }
            .distinctBy { it.key }
            .forEach { album ->
                album.genres.map { it.trim() }.filter { it.isNotEmpty() }
                    .groupBy { normalize(it) }
                    .filterKeys { it.isNotEmpty() }
                    .forEach { (key, names) ->
                        members.getOrPut(key) { mutableListOf() } += album
                        val counts = spellings.getOrPut(key) { HashMap() }
                        names.distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 }
                    }
            }
        return members.map { (key, list) -> Genre(key, label(spellings.getValue(key)), list.sortedWith(ALBUM_ORDER)) }
            .sortedWith(compareByDescending<Genre> { it.albums.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** The spelling most albums use; on a tie, the first in a stable order. */
    private fun label(counts: Map<String, Int>): String =
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key

    /** The artists of a genre, most albums first; compilations ("Various Artists") are left out. */
    fun artists(genre: Genre): List<GenreArtist> =
        genre.albums.filter { !it.artistKey.isNullOrBlank() && !it.artist.isNullOrBlank() && !isCompilationArtist(it.artist) }
            .groupBy { it.artistKey!! }
            .map { (key, albums) -> albums.size to GenreArtist(key, albums.first().artist!!, albums.firstNotNullOfOrNull { it.artistThumb }) }
            .sortedWith(compareByDescending<Pair<Int, GenreArtist>> { it.first }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.second.name })
            .map { it.second }

    /** The albums a genre shuffle draws from: a random handful, so a big genre still starts at once. */
    fun shuffleAlbums(genre: Genre, random: Random = Random, count: Int = SHUFFLE_ALBUMS): List<GenreAlbum> =
        genre.albums.shuffled(random).take(count)

    /** Genre colours: deep tones of the sleeve palette, all readable under Sleeve cream (#F6EFE4, AA). */
    val PALETTE: List<Long> = listOf(
        0xFF8A3B1F, // rust, deepened
        0xFF6B4A12, // label amber, deepened
        0xFF4E5B2E, // olive
        0xFF2F5050, // teal
        0xFF5A2E4A, // plum
        0xFF3B3F6B, // indigo
        0xFF7A2E2E, // oxblood
        0xFF2A241E, // groove
    )

    /** A genre keeps its colour from one launch to the next: it is drawn from its normalised name. */
    fun colorOf(key: String): Long = PALETTE[key.hashCode().mod(PALETTE.size)]

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[-_‐‑‒–—]")
    private val SPACES = Regex("\\s+")
    private val ALBUM_ORDER = compareBy<GenreAlbum, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
}

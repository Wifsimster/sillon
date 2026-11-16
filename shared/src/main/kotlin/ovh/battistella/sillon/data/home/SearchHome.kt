package ovh.battistella.sillon.data.home

import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.jellyfin.JELLYFIN_IMAGE
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.wear.AlbumPlays

/** An album on the search page before anything is typed. [thumb] is the server path, never a URL with a token. */
@Serializable
data class HomeAlbum(
    val key: String,
    val title: String,
    val artist: String? = null,
    val thumb: String? = null,
    val playedAt: Long? = null,
)

/**
 * What the empty search page shows, on disk per server and account. [local] holds the albums Sillon
 * played itself, which the server may not know yet (offline plays). [sealed] holds the mystery albums
 * played without being revealed: they stay out of both rows until they are played in the open. They are
 * also kept apart on disk, so that signing out does not forget them (see SearchHomeCache).
 */
@Serializable
data class CachedHome(
    val owner: String,
    val recent: List<HomeAlbum> = emptyList(),
    val classics: List<HomeAlbum> = emptyList(),
    val local: List<HomeAlbum> = emptyList(),
    val sealed: Set<String> = emptySet(),
)

object SearchHome {
    const val ROW_SIZE = 20

    /** Jellyfin keeps no play date on albums, so the recent albums come from the recent tracks. */
    const val JELLYFIN_RECENT_TRACKS = 200

    private const val PLEX_ALBUM = "9"
    private const val PLEX_START = "X-Plex-Container-Start"
    private const val PLEX_SIZE = "X-Plex-Container-Size"

    /** Plex rolls each track play up to its album, so the albums can be sorted by their last play. */
    fun plexRecentQuery(size: Int = ROW_SIZE * 2): List<Pair<String, String>> = listOf(
        "type" to PLEX_ALBUM,
        "sort" to "lastViewedAt:desc",
        PLEX_START to "0",
        PLEX_SIZE to size.toString(),
    )

    fun jellyfinRecentQuery(sectionKey: String, limit: Int = JELLYFIN_RECENT_TRACKS): List<Pair<String, String>> = listOf(
        "parentId" to sectionKey,
        "includeItemTypes" to "Audio",
        "recursive" to "true",
        "filters" to "IsPlayed",
        "sortBy" to "DatePlayed",
        "sortOrder" to "Descending",
        "limit" to limit.toString(),
        "enableUserData" to "true",
        "enableTotalRecordCount" to "false",
    )

    /** Albums never played sort last on Plex: they are dropped. */
    fun plexRecent(albums: List<PlexMetadata>): List<HomeAlbum> =
        albums.filter { it.ratingKey.isNotBlank() && (it.lastViewedAt ?: 0L) > 0L }
            .map { HomeAlbum(it.ratingKey, it.title, it.parentTitle, it.thumb, it.lastViewedAt?.times(1_000)) }

    /** Recent tracks, newest first, folded into their albums in the order of their latest track. */
    fun recentFromTracks(tracks: List<PlexMetadata>): List<HomeAlbum> =
        tracks.filter { !it.parentRatingKey.isNullOrBlank() }
            .distinctBy { it.parentRatingKey }
            .map { track ->
                HomeAlbum(
                    key = track.parentRatingKey!!,
                    title = track.parentTitle.orEmpty(),
                    artist = track.grandparentTitle ?: track.originalTitle,
                    thumb = track.parentThumb,
                    playedAt = track.lastViewedAt?.times(1_000),
                )
            }

    fun album(metadata: PlexMetadata): HomeAlbum =
        HomeAlbum(metadata.ratingKey, metadata.title, metadata.parentTitle, metadata.thumb)

    /** The cover of an album known only by its key: both servers serve it from a path built on the key. */
    fun thumbOf(kind: ServerKind, albumKey: String): String = when (kind) {
        ServerKind.PLEX -> "/library/metadata/$albumKey/thumb"
        ServerKind.JELLYFIN -> "$JELLYFIN_IMAGE$albumKey"
    }

    /**
     * The server's history and Sillon's own, newest first, one tile per album. A play known to both
     * keeps the server's title and cover. Sealed mystery albums never show.
     */
    fun mergeRecent(
        server: List<HomeAlbum>,
        local: List<HomeAlbum>,
        sealed: Set<String>,
        limit: Int = ROW_SIZE,
    ): List<HomeAlbum> {
        val byKey = LinkedHashMap<String, HomeAlbum>()
        server.forEach { byKey.putIfAbsent(it.key, it) }
        local.forEach { mine ->
            val known = byKey[mine.key]
            byKey[mine.key] = when {
                known == null -> mine
                (mine.playedAt ?: 0L) > (known.playedAt ?: 0L) -> known.copy(playedAt = mine.playedAt)
                else -> known
            }
        }
        // Stable sort: entries without a date keep the server's order, after the dated ones.
        return byKey.values
            .filter { it.key !in sealed }
            .sortedByDescending { it.playedAt ?: Long.MIN_VALUE }
            .take(limit)
    }

    /**
     * What the two rows show. [hidden] holds the sealed and live mystery albums: a mystery played
     * unrevealed counts on the server, so it could reach either row.
     */
    fun rows(cached: CachedHome?, hidden: Set<String>): Pair<List<HomeAlbum>, List<HomeAlbum>> {
        val recent = mergeRecent(cached?.recent.orEmpty(), cached?.local.orEmpty(), hidden)
        val classics = cached?.classics.orEmpty().filter { it.key !in hidden }
        return recent to classics
    }

    /** The most played albums, by total plays, as in sleeve wear. */
    fun classics(plays: Map<String, AlbumPlays>, limit: Int = ROW_SIZE): List<String> =
        plays.filter { it.value.plays > 0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, AlbumPlays>> { it.value.plays }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    /** [keys] in order, with what is known about each; unknown keys are left out. */
    fun ordered(keys: List<String>, known: Collection<HomeAlbum>): List<HomeAlbum> {
        val byKey = known.associateBy { it.key }
        return keys.mapNotNull { byKey[it] }
    }

    /** Sillon just played [album]: it moves to the front of its own history. */
    fun record(local: List<HomeAlbum>, album: HomeAlbum, limit: Int = ROW_SIZE): List<HomeAlbum> =
        (listOf(album) + local.filter { it.key != album.key }).take(limit)

    /**
     * A mystery album playing unrevealed is sealed, and stays sealed if the sleeve is left before the
     * reveal. The reveal unseals it, and so does playing it in the open later ([playedOpenly]).
     */
    fun seal(sealed: Set<String>, mysteryAlbum: String?, revealed: Boolean, playedOpenly: String? = null): Set<String> {
        var next = sealed
        if (mysteryAlbum != null) next = if (revealed) next - mysteryAlbum else next + mysteryAlbum
        if (playedOpenly != null && !(playedOpenly == mysteryAlbum && !revealed)) next = next - playedOpenly
        return next
    }

    /** Without a connection only what the phone holds can play. */
    fun offline(albums: List<HomeAlbum>, onPhone: Set<String>): List<HomeAlbum> = albums.filter { it.key in onPhone }
}

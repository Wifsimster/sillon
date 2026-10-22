package ovh.battistella.sillon.data.repository

import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.plex.PlexMetadata
import kotlin.random.Random
import kotlin.math.roundToInt

enum class NeverPlayedMode { TRACKS, ALBUMS }

const val NEVER_PLAYED_STATION = "sillon:never-played"
const val NEVER_PLAYED_ALBUMS_STATION = "sillon:never-played-albums"

fun neverPlayedModeOf(stationKey: String): NeverPlayedMode? = when (stationKey) {
    NEVER_PLAYED_STATION -> NeverPlayedMode.TRACKS
    NEVER_PLAYED_ALBUMS_STATION -> NeverPlayedMode.ALBUMS
    else -> null
}

fun NeverPlayedMode.stationKey(): String = when (this) {
    NeverPlayedMode.TRACKS -> NEVER_PLAYED_STATION
    NeverPlayedMode.ALBUMS -> NEVER_PLAYED_ALBUMS_STATION
}

fun PlexMetadata.isNeverPlayed(): Boolean = (viewCount ?: 0) == 0

fun neverPlayedPercent(neverPlayed: Int, total: Int): Int? {
    if (total <= 0) return null
    val count = neverPlayed.coerceIn(0, total)
    val rounded = (count * 100.0 / total).roundToInt()
    return when {
        count == 0 -> 0
        count == total -> 100
        rounded == 0 -> 1
        rounded == 100 -> 99
        else -> rounded
    }
}

object NeverPlayedQueries {
    const val TRACK_BATCH = 50
    const val ALBUM_CANDIDATES = 12

    private const val PLEX_TRACK = "10"
    private const val PLEX_ALBUM = "9"
    private const val PLEX_START = "X-Plex-Container-Start"
    private const val PLEX_SIZE = "X-Plex-Container-Size"

    fun plexTracks(size: Int = TRACK_BATCH): List<Pair<String, String>> = listOf(
        "type" to PLEX_TRACK,
        "viewCount" to "0",
        "sort" to "random",
        PLEX_START to "0",
        PLEX_SIZE to size.toString(),
    )

    fun plexTrackCount(neverPlayedOnly: Boolean): List<Pair<String, String>> = buildList {
        add("type" to PLEX_TRACK)
        if (neverPlayedOnly) {
            add("viewCount" to "0")
            add("sort" to "viewCount:desc")
        }
        add(PLEX_START to "0")
        add(PLEX_SIZE to "1")
    }

    fun plexAlbums(size: Int = ALBUM_CANDIDATES): List<Pair<String, String>> = listOf(
        "type" to PLEX_ALBUM,
        "sort" to "random",
        PLEX_START to "0",
        PLEX_SIZE to size.toString(),
    )

    fun jellyfinTracks(sectionKey: String, limit: Int = TRACK_BATCH): List<Pair<String, String>> = listOf(
        "parentId" to sectionKey,
        "includeItemTypes" to "Audio",
        "recursive" to "true",
        "isPlayed" to "false",
        "sortBy" to "Random",
        "limit" to limit.toString(),
        "enableTotalRecordCount" to "false",
    )

    fun jellyfinTrackCount(sectionKey: String, neverPlayedOnly: Boolean): List<Pair<String, String>> = buildList {
        add("parentId" to sectionKey)
        add("includeItemTypes" to "Audio")
        add("recursive" to "true")
        if (neverPlayedOnly) add("isPlayed" to "false")
        add("limit" to "0")
        add("enableTotalRecordCount" to "true")
    }

    fun jellyfinAlbums(sectionKey: String, limit: Int = ALBUM_CANDIDATES): List<Pair<String, String>> = listOf(
        "parentId" to sectionKey,
        "includeItemTypes" to "MusicAlbum",
        "recursive" to "true",
        "isPlayed" to "false",
        "sortBy" to "Random",
        "limit" to limit.toString(),
        "fields" to "ChildCount",
        "enableTotalRecordCount" to "false",
    )
}

class NeverPlayedSession(private val skipThresholdMs: Long = SKIP_THRESHOLD_MS) {
    private val queued = LinkedHashSet<String>()
    private val queuedAlbums = HashSet<String>()
    private val played = HashSet<String>()
    private val notForMe = HashSet<String>()

    @Synchronized
    fun begin() {
        queued.clear()
        queuedAlbums.clear()
    }

    @Synchronized
    fun reset() {
        begin()
        played.clear()
        notForMe.clear()
    }

    @Synchronized
    fun owns(key: String): Boolean = key in queued

    @Synchronized
    fun isExcluded(key: String): Boolean = key in queued || key in played || key in notForMe

    @Synchronized
    fun isNotForMe(key: String): Boolean = key in notForMe

    @Synchronized
    fun hasPlayed(key: String): Boolean = key in played

    @Synchronized
    fun isAlbumQueued(albumKey: String): Boolean = albumKey in queuedAlbums

    fun take(candidates: List<PlexMetadata>): List<PlexMetadata> {
        val keys = takeKeys(candidates.filter { it.isNeverPlayed() }.map { it.ratingKey }).toSet()
        return candidates.filter { it.ratingKey in keys }.distinctBy { it.ratingKey }
    }

    fun takeAlbum(albumKey: String, tracks: List<PlexMetadata>): List<PlexMetadata>? {
        if (!tracks.all { it.isNeverPlayed() }) return null
        return takeAlbumKeys(albumKey, tracks.map { it.ratingKey })?.let { tracks }
    }

    @Synchronized
    fun takeKeys(candidates: List<String>): List<String> {
        val fresh = candidates.filter { it.isNotBlank() && !isExcluded(it) }.distinct()
        queued += fresh
        return fresh
    }

    @Synchronized
    fun takeAlbumKeys(albumKey: String, tracks: List<String>): List<String>? {
        if (albumKey in queuedAlbums || tracks.isEmpty()) return null
        if (tracks.any { it.isBlank() || isExcluded(it) }) return null
        queuedAlbums += albumKey
        queued += tracks
        return tracks
    }

    @Synchronized
    fun leave(key: String, listenedMs: Long, skipped: Boolean): Outcome {
        if (key !in queued) return Outcome.IGNORED
        return if (skipped && listenedMs < skipThresholdMs) {
            notForMe += key
            Outcome.NOT_FOR_ME
        } else {
            played += key
            Outcome.PLAYED
        }
    }

    enum class Outcome { PLAYED, NOT_FOR_ME, IGNORED }

    companion object {
        const val SKIP_THRESHOLD_MS = 30_000L
    }
}

fun pickOffline(
    downloaded: List<DownloadEntity>,
    neverPlayed: Set<String>,
    session: NeverPlayedSession,
    mode: NeverPlayedMode,
    random: Random = Random.Default,
    batch: Int = NeverPlayedQueries.TRACK_BATCH,
): List<DownloadEntity> {
    val byKey = downloaded.associateBy { it.ratingKey }
    return when (mode) {
        NeverPlayedMode.TRACKS -> {
            val candidates = downloaded.map { it.ratingKey }.filter { it in neverPlayed }.shuffled(random).take(batch)
            session.takeKeys(candidates).mapNotNull { byKey[it] }
        }
        NeverPlayedMode.ALBUMS -> {
            val albums = downloaded.filter { it.albumKey != null }.groupBy { it.albumKey!! }
                .filter { (_, tracks) -> tracks.all { it.ratingKey in neverPlayed } }
            albums.keys.shuffled(random).firstNotNullOfOrNull { albumKey ->
                val tracks = albums.getValue(albumKey).sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                session.takeAlbumKeys(albumKey, tracks.map { it.ratingKey })?.let { tracks }
            }.orEmpty()
        }
    }
}

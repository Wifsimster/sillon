package ovh.battistella.sillon.ui.screens.library

import ovh.battistella.sillon.data.plex.PlexMetadata
import kotlin.math.ln

/** An artist or album tile: key to open it, what to show, and its cover. */
data class TileUi(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    /** Shown as "N tracks" when there is no [subtitle] (playlists). */
    val trackCount: Int? = null,
)

data class TrackUi(
    val key: String,
    val number: Int?,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
    /** Popularity within the list it's shown in, 0–1; null when Plex has no figure. */
    val popularity: Float? = null,
    /** The user's rating in stars, 0–5. */
    val stars: Float? = null,
)

/**
 * Popularity of each track relative to the most popular one in [tracks], on a
 * log scale: listener counts span orders of magnitude, and a linear scale
 * would show every track but the hit as empty.
 */
fun popularityOf(tracks: List<PlexMetadata>): Map<String, Float> {
    val max = tracks.maxOfOrNull { it.ratingCount ?: 0L } ?: 0L
    if (max <= 0L) return emptyMap()
    val top = ln(max + 1.0)
    return tracks.mapNotNull { t ->
        t.ratingCount?.let { t.ratingKey to (ln(it + 1.0) / top).toFloat().coerceIn(0f, 1f) }
    }.toMap()
}

/** A track as a row, with its popularity among [popularity] and the user's rating. */
fun PlexMetadata.toTrackUi(
    number: Int?,
    artist: String?,
    popularity: Map<String, Float> = emptyMap(),
) = TrackUi(ratingKey, number, title, artist, duration, popularity[ratingKey], stars)

/** Cover sizes requested from the server, in pixels. */
internal const val TILE_ART_PX = 400
internal const val HEADER_ART_PX = 900

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
 * Popularity of each track within [tracks]: the least popular is 0, the most
 * popular 1, on a log scale in between. Relative to the list's own range, not
 * to zero: on a popular album every track has millions of listeners, and
 * measuring from zero lit every bar. Like Plexamp, it answers "which are the
 * hits of this album?".
 */
fun popularityOf(tracks: List<PlexMetadata>): Map<String, Float> {
    val counts = tracks.mapNotNull { t -> t.ratingCount?.takeIf { it > 0 }?.let { t.ratingKey to ln(it.toDouble()) } }
    if (counts.isEmpty()) return emptyMap()
    val low = counts.minOf { it.second }
    val high = counts.maxOf { it.second }
    val range = high - low
    return counts.associate { (key, value) ->
        key to if (range <= 0.0) 1f else ((value - low) / range).toFloat()
    }
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

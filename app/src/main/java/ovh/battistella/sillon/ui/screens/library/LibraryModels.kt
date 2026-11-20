package ovh.battistella.sillon.ui.screens.library

import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.sort.SortableTrack
import kotlin.math.ln

data class TileUi(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val trackCount: Int? = null,
)

data class TrackUi(
    val key: String,
    override val number: Int?,
    override val title: String,
    val artist: String?,
    override val durationMs: Long?,
    override val popularity: Float? = null,
    val stars: Float? = null,
    override val plays: Int? = null,
) : SortableTrack {
    override val sortStars: Float? get() = stars
}

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

fun PlexMetadata.toTrackUi(
    number: Int?,
    artist: String?,
    popularity: Map<String, Float> = emptyMap(),
) = TrackUi(ratingKey, number, title, artist, duration, popularity[ratingKey], stars, plays = viewCount)

internal const val TILE_ART_PX = 400
internal const val HEADER_ART_PX = 900

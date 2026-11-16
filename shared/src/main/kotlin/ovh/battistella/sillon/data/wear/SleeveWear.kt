package ovh.battistella.sillon.data.wear

import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexMetadata

@Serializable
data class AlbumPlays(val plays: Int, val tracks: Int)

object SleeveWear {
    const val SEALED = 0
    const val MAX = 4
    const val NO_SPREAD = 2

    fun measure(plays: AlbumPlays): Double =
        if (plays.plays <= 0) 0.0 else plays.plays.toDouble() / plays.tracks.coerceAtLeast(1)

    fun levels(library: Map<String, AlbumPlays>): Map<String, Int> {
        val measures = library.mapValues { measure(it.value) }
        val played = measures.values.filter { it > 0.0 }.sorted()
        return measures.mapValues { (_, m) -> if (m <= 0.0) SEALED else levelOf(percentile(played, m)) }
    }

    fun percentile(sorted: List<Double>, value: Double): Double? {
        if (sorted.size < 2 || sorted.first() == sorted.last()) return null
        val below = lowerBound(sorted, value)
        val equal = lowerBound(sorted, Math.nextUp(value)) - below
        return (below + (equal - 1).coerceAtLeast(0) / 2.0) / (sorted.size - 1)
    }

    fun levelOf(percentile: Double?): Int =
        if (percentile == null) NO_SPREAD else 1 + (percentile * MAX).toInt().coerceIn(0, MAX - 1)

    private fun lowerBound(sorted: List<Double>, value: Double): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sorted[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }
}

fun plexAlbumPlays(albums: List<PlexMetadata>): Map<String, AlbumPlays> =
    albums.filter { it.ratingKey.isNotBlank() }
        .associate { it.ratingKey to AlbumPlays(it.viewCount ?: 0, it.leafCount ?: 0) }

fun jellyfinAlbumPlays(albums: List<PlexMetadata>, playedTracks: List<PlexMetadata>): Map<String, AlbumPlays> {
    val plays = playedTracks.groupBy { it.parentRatingKey }.mapValues { (_, tracks) -> tracks.sumOf { it.viewCount ?: 0 } }
    return albums.filter { it.ratingKey.isNotBlank() }
        .associate { it.ratingKey to AlbumPlays(plays[it.ratingKey] ?: 0, it.leafCount ?: 0) }
}

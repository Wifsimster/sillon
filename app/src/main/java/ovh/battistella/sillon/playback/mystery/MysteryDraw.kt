package ovh.battistella.sillon.playback.mystery

import ovh.battistella.sillon.data.plex.PlexMetadata
import kotlin.math.pow
import kotlin.random.Random

data class AlbumCandidate(val key: String, val trackCount: Int?, val playCount: Int)

fun PlexMetadata.toAlbumCandidate() = AlbumCandidate(ratingKey, leafCount, viewCount ?: 0)

object MysteryDraw {
    const val MIN_TRACKS = 4

    fun eligible(albums: List<AlbumCandidate>): List<AlbumCandidate> =
        albums.filter { it.key.isNotBlank() && (it.trackCount ?: MIN_TRACKS) >= MIN_TRACKS }

    fun weight(album: AlbumCandidate, anyAlbum: Boolean): Double =
        if (anyAlbum) 1.0 else 1.0 / (1.0 + album.playCount.coerceAtLeast(0)).pow(2)

    fun pick(
        albums: List<AlbumCandidate>,
        anyAlbum: Boolean,
        random: Random = Random.Default,
        exclude: Set<String> = emptySet(),
    ): AlbumCandidate? {
        val eligible = eligible(albums)
        val pool = eligible.filter { it.key !in exclude }.ifEmpty { eligible }
        if (pool.isEmpty()) return null
        val weights = pool.map { weight(it, anyAlbum) }
        var roll = random.nextDouble() * weights.sum()
        pool.forEachIndexed { i, album ->
            roll -= weights[i]
            if (roll < 0) return album
        }
        return pool.last()
    }
}

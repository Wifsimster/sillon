package ovh.battistella.sillon.data.rating

import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.plex.PlexMetadata
import kotlin.math.ceil

/** Stars as people see them (1 to 5), against the 0–10 scale servers keep (Plex: stars × 2). */
object Stars {
    const val MAX = 5
    const val NONE = 0

    /** The stars of a 0–10 rating, half stars rounded up; null when unrated. */
    fun of(rating: Float?): Int? = rating?.takeIf { it > 0f }?.let { ceil(it / 2f).toInt().coerceIn(1, MAX) }

    fun toRating(stars: Int?): Int = stars?.coerceIn(1, MAX)?.times(2) ?: NONE

    fun isOneStar(rating: Float?): Boolean = of(rating) == 1

    fun isFiveStars(rating: Float?): Boolean = of(rating) == MAX

    /** Touching the star already given clears the rating. */
    fun toggle(current: Int?, touched: Int): Int? = if (current == touched) null else touched
}

/**
 * Jellyfin keeps no stars per user: 5★ is a favourite, 1★ is a dislike ("likes" = false),
 * and no rating is neither. Stars 2 to 4 don't exist there.
 */
object JellyfinStars {
    fun supports(stars: Int?): Boolean = stars == null || stars == 1 || stars == Stars.MAX

    fun rating(isFavorite: Boolean, likes: Boolean?): Float? = when {
        isFavorite -> 10f
        likes == false -> 2f
        else -> null
    }
}

/** What happened to the last rating sent for a track. */
enum class RatingSend { SENDING, WAITING_FOR_NETWORK, FAILED }

/**
 * The ratings the phone knows for one server: the ones given here (sent or waiting to be),
 * and the 1-star tracks seen on the server, so downloads (which carry no rating) are kept
 * out of shuffles and radios without a connection.
 *
 * Ratings are on the 0–10 scale; [Stars.NONE] means "no rating".
 */
@Serializable
data class RatingBook(
    val known: Map<String, Int> = emptyMap(),
    val pending: Map<String, Int> = emptyMap(),
    val waiting: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
) {
    /** The rating to show and to act on: the phone's own when it has one, else the server's. */
    fun ratingOf(key: String, server: Float?): Float? {
        val own = known[key] ?: return server?.takeIf { it > 0f }
        return own.takeIf { it > 0 }?.toFloat()
    }

    fun starsOf(key: String, server: Float?): Int? = Stars.of(ratingOf(key, server))

    fun isOneStar(key: String, server: Float?): Boolean = Stars.isOneStar(ratingOf(key, server))

    fun sendState(key: String): RatingSend? = when (key) {
        in failed -> RatingSend.FAILED
        in waiting -> RatingSend.WAITING_FOR_NETWORK
        in pending -> RatingSend.SENDING
        else -> null
    }

    fun rate(key: String, stars: Int?): RatingBook {
        val rating = Stars.toRating(stars)
        return copy(
            known = bounded(known + (key to rating), keep = key),
            pending = pending + (key to rating),
            waiting = waiting - key,
            failed = failed - key,
        )
    }

    /** The server took [rating]; a newer rating given meanwhile stays pending. */
    fun sent(key: String, rating: Int): RatingBook {
        if (pending[key] != rating) return this
        return copy(pending = pending - key, waiting = waiting - key, failed = failed - key)
    }

    /** No network: the rating stays in line and leaves when the phone is back online. */
    fun offline(key: String): RatingBook = if (key in pending) copy(waiting = waiting + key) else this

    /** The server refused it: no retry on its own, the player says so. */
    fun refused(key: String): RatingBook = copy(pending = pending - key, waiting = waiting - key, failed = failed + key)

    /**
     * Learns from what the server says. A rating waiting to be sent wins; otherwise the server
     * is right, and only 1-star tracks (and tracks already in the book) are remembered.
     */
    fun learn(tracks: List<PlexMetadata>): RatingBook {
        var next = known
        for (track in tracks) {
            val key = track.ratingKey
            if (key.isBlank() || key in pending) continue
            val rating = track.userRating?.takeIf { it > 0f }?.let { Math.round(it) } ?: Stars.NONE
            when {
                key in next -> if (next[key] != rating) next = next + (key to rating)
                Stars.isOneStar(rating.toFloat()) -> next = next + (key to rating)
            }
        }
        if (next === known) return this
        return copy(known = bounded(next, keep = null))
    }

    private fun bounded(map: Map<String, Int>, keep: String?): Map<String, Int> {
        if (map.size <= MAX_KNOWN) return map
        val excess = map.size - MAX_KNOWN
        // Cleared ratings go first: they only matter until the server has caught up.
        val drop = (map.filterValues { it == Stars.NONE }.keys + map.keys)
            .asSequence().filter { it != keep && it !in pending }.distinct().take(excess).toSet()
        return map - drop
    }

    companion object {
        const val MAX_KNOWN = 20_000
    }
}

/** The downloads to keep when a queue is drawn for you offline: they carry no rating, the book knows. */
fun RatingBook.withoutOneStarDownloads(rows: List<DownloadEntity>): List<DownloadEntity> =
    rows.filterNot { isOneStar(it.ratingKey, null) }

/** The tracks to keep when a queue is drawn for you: never a track rated 1 star. */
fun RatingBook.withoutOneStar(tracks: List<PlexMetadata>): List<PlexMetadata> =
    tracks.filterNot { isOneStar(it.ratingKey, it.userRating) }

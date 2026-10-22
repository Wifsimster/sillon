package ovh.battistella.sillon.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

data class PlayableTrack(
    val ratingKey: String,
    val streamUrl: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val gainDb: Float? = null,
    val albumKey: String? = null,
    /** Set when the original file is streamed: the listening cache keeps it under this key. */
    val cacheKey: String? = null,
    /** The server's rating (0–10), so the player knows the 1-star tracks of the queue. */
    val userRating: Float? = null,
)

object MediaItems {
    @OptIn(UnstableApi::class)
    fun from(track: PlayableTrack): MediaItem {
        val uri = Uri.parse(track.streamUrl)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setTrackNumber(track.trackNumber)
            .setArtworkUri(track.artworkUrl?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras(track))
            .build()
        return MediaItem.Builder()
            .setMediaId(track.ratingKey)
            .setUri(uri)
            .setMimeType(if (isTranscode(track.streamUrl)) MimeTypes.APPLICATION_M3U8 else null)
            .setCustomCacheKey(track.cacheKey)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun extras(track: PlayableTrack): Bundle? {
        if (track.gainDb == null && track.albumKey == null && track.userRating == null) return null
        return Bundle().apply {
            track.gainDb?.let { putFloat(EXTRA_GAIN_DB, it) }
            track.albumKey?.let { putString(EXTRA_ALBUM_KEY, it) }
            track.userRating?.let { putFloat(EXTRA_RATING, it) }
        }
    }

    /** The server's rating carried by [item], or null when it had none. */
    fun ratingOf(item: MediaItem?): Float? =
        item?.mediaMetadata?.extras?.takeIf { it.containsKey(EXTRA_RATING) }?.getFloat(EXTRA_RATING)

    const val EXTRA_GAIN_DB = "ovh.battistella.sillon.GAIN_DB"
    const val EXTRA_ALBUM_KEY = "ovh.battistella.sillon.ALBUM_KEY"
    const val EXTRA_RATING = "ovh.battistella.sillon.RATING"

    fun volumeFor(gainDb: Float?): Float =
        if (gainDb == null) 1f else Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(MIN_VOLUME, 1f)

    private const val MIN_VOLUME = 0.1f

    fun isTranscode(url: String): Boolean =
        url.contains("/transcode/universal/") || url.substringBefore('?').endsWith(".m3u8")
}

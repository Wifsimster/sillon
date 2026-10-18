package ovh.battistella.sillon.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

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
)

object MediaItems {
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
            .setExtras(track.gainDb?.let { gain -> Bundle().apply { putFloat(EXTRA_GAIN_DB, gain) } })
            .build()
        return MediaItem.Builder()
            .setMediaId(track.ratingKey)
            .setUri(uri)
            .setMimeType(if (isTranscode(track.streamUrl)) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(metadata)
            .build()
    }

    const val EXTRA_GAIN_DB = "ovh.battistella.sillon.GAIN_DB"

    fun volumeFor(gainDb: Float?): Float =
        if (gainDb == null) 1f else Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(MIN_VOLUME, 1f)

    private const val MIN_VOLUME = 0.1f

    fun isTranscode(url: String): Boolean =
        url.contains("/transcode/universal/") || url.substringBefore('?').endsWith(".m3u8")
}

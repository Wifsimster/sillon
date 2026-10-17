package ovh.battistella.sillon.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

/** A track ready for the player: the Plex key, where to stream it from, and what to display. */
data class PlayableTrack(
    val ratingKey: String,
    val streamUrl: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val artworkUrl: String?,
    /** Volume correction for loudness levelling, in dB; null when off or unknown. */
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
            // The URI (which carries the Plex token) stays in the local
            // configuration: Media3 strips it before handing items to another
            // process. A controller there sends back bare ids, which the service
            // resolves by key (see MediaLibraryTree.resolveEach).
            .setUri(uri)
            // A transcode is an HLS playlist; say so rather than rely on the extension.
            .setMimeType(if (isTranscode(track.streamUrl)) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(metadata)
            .build()
    }

    const val EXTRA_GAIN_DB = "ovh.battistella.sillon.GAIN_DB"

    /**
     * Player volume (0–1) for a loudness correction of [gainDb]. A track that
     * should get louder can't: the player volume can only attenuate, so it
     * stays at full volume.
     */
    fun volumeFor(gainDb: Float?): Float =
        if (gainDb == null) 1f else Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(MIN_VOLUME, 1f)

    /** Never turn a track down by more than 20 dB, whatever a broken tag says. */
    private const val MIN_VOLUME = 0.1f

    /** True for a server transcode (as opposed to the original file or a download). */
    fun isTranscode(url: String): Boolean =
        url.contains("/transcode/universal/") || url.substringBefore('?').endsWith(".m3u8")
}

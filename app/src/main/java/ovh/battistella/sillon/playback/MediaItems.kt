package ovh.battistella.sillon.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

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
            .build()
        return MediaItem.Builder()
            .setMediaId(track.ratingKey)
            // The URI (which carries the Plex token) stays in the local
            // configuration: Media3 strips it before handing items to another
            // process. A controller there sends back bare ids, which the service
            // resolves by key (see MediaLibraryTree.resolveEach).
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}

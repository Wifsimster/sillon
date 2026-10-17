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
            .setUri(uri)
            // The URI is also carried in the request metadata: a controller in
            // another process (Bluetooth, Android Auto) has the local
            // configuration stripped, and the service restores it from here.
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(metadata)
            .build()
    }

    /** Rebuild the playable URI of an item that crossed a process boundary without it. */
    fun restoreUri(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri ?: return null
        return item.buildUpon().setUri(uri).build()
    }
}

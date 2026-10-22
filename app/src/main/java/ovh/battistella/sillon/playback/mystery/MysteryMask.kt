package ovh.battistella.sillon.playback.mystery

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ovh.battistella.sillon.playback.MediaItems
import ovh.battistella.sillon.playback.PlayableTrack

data class SleeveLabels(
    val track: (side: Int, number: Int) -> String,
    val album: String,
    val artworkUri: Uri? = null,
    val artworkData: ByteArray? = null,
)

data class TrackPosition(val side: Int?, val number: Int?)

object MysteryMask {
    const val EXTRA_MYSTERY = "ovh.battistella.sillon.MYSTERY"
    const val HIDDEN_KEY = "mystery"

    fun mask(track: MysteryTrack, labels: SleeveLabels): MediaItem {
        val item = MediaItems.from(track.track)
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putBoolean(EXTRA_MYSTERY, true) }
        val metadata = MediaMetadata.Builder()
            .setTitle(labels.track(track.side, track.number))
            .setArtist(labels.album)
            .setTrackNumber(track.number)
            .setDiscNumber(track.side)
            .setArtworkUri(labels.artworkUri)
            .apply { labels.artworkData?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    fun isMasked(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getBoolean(EXTRA_MYSTERY, false) == true

    fun unmask(item: MediaItem, real: PlayableTrack): MediaItem =
        item.buildUpon().setMediaMetadata(MediaItems.from(real).mediaMetadata).build()

    fun sides(tracks: List<Pair<PlayableTrack, TrackPosition>>): List<MysteryTrack> {
        val counters = mutableMapOf<Int, Int>()
        return tracks.map { (track, position) ->
            val side = position.side?.takeIf { it > 0 } ?: 1
            val count = (counters[side] ?: 0) + 1
            counters[side] = count
            MysteryTrack(track, side, position.number?.takeIf { it > 0 } ?: count)
        }
    }
}

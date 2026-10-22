package ovh.battistella.sillon.sonos

import androidx.media3.common.Player
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** `CurrentTransportState` of a Sonos group. */
enum class Transport {
    PLAYING,
    PAUSED,
    STOPPED,
    TRANSITIONING,
    NO_MEDIA,
    ;

    companion object {
        fun of(value: String?): Transport = when (value?.trim()) {
            "PLAYING" -> PLAYING
            "PAUSED_PLAYBACK" -> PAUSED
            "TRANSITIONING" -> TRANSITIONING
            "NO_MEDIA_PRESENT" -> NO_MEDIA
            else -> STOPPED
        }
    }
}

/** Sonos play modes: shuffle and repeat in one word. */
object PlayMode {
    fun of(shuffle: Boolean, repeatMode: Int): String = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> if (shuffle) "SHUFFLE" else "REPEAT_ALL"
        Player.REPEAT_MODE_ONE -> if (shuffle) "SHUFFLE_REPEAT_ONE" else "REPEAT_ONE"
        else -> if (shuffle) "SHUFFLE_NOREPEAT" else "NORMAL"
    }

    /** (shuffle, Media3 repeat mode) of a Sonos play mode; unknown modes read as normal. */
    fun parse(value: String?): Pair<Boolean, Int> = when (value?.trim()) {
        "REPEAT_ALL" -> false to Player.REPEAT_MODE_ALL
        "REPEAT_ONE" -> false to Player.REPEAT_MODE_ONE
        "SHUFFLE" -> true to Player.REPEAT_MODE_ALL
        "SHUFFLE_REPEAT_ONE" -> true to Player.REPEAT_MODE_ONE
        "SHUFFLE_NOREPEAT" -> true to Player.REPEAT_MODE_OFF
        else -> false to Player.REPEAT_MODE_OFF
    }
}

/** What one poll of the coordinator says. */
data class SonosStatus(
    val transport: Transport,
    /** 0-based queue index, or null when nothing is loaded. */
    val index: Int?,
    val positionMs: Long,
    val durationMs: Long?,
    val trackUri: String?,
    val mediaUri: String?,
) {
    val isPlaying: Boolean get() = transport == Transport.PLAYING
    val isBuffering: Boolean get() = transport == Transport.TRANSITIONING

    /** The Media3 playback state the notification and the player screen show. */
    val playbackState: Int get() = when (transport) {
        Transport.TRANSITIONING -> Player.STATE_BUFFERING
        else -> Player.STATE_READY
    }

    /** Media3's play-when-ready: buffering towards playing counts as wanting to play. */
    val playWhenReady: Boolean get() = transport == Transport.PLAYING || transport == Transport.TRANSITIONING

    /**
     * Whether the group still plays what Sillon sent: its own queue (`x-rincon-queue:<RINCON>#0`,
     * a URI the speaker makes from its own id), and a track from Sillon's server. Tracks are
     * compared by [trackKey] (host, port and path), never by the whole URL: the speaker may hand
     * the query back re-encoded or reordered. Anything else means another controller took over.
     */
    fun playsOurs(coordinatorUuid: String, ourTracks: Set<String>): Boolean {
        if (!mediaUri.equals(AvTransport.queueUri(coordinatorUuid), ignoreCase = true)) return false
        if (trackUri.isNullOrBlank()) return true
        return trackKey(trackUri) in ourTracks
    }

    companion object {
        fun of(transportInfo: Map<String, String>, positionInfo: Map<String, String>, mediaInfo: Map<String, String>) =
            SonosStatus(
                transport = Transport.of(transportInfo["CurrentTransportState"]),
                index = positionInfo["Track"]?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.minus(1),
                positionMs = SonosTime.parse(positionInfo["RelTime"]) ?: 0L,
                durationMs = SonosTime.parse(positionInfo["TrackDuration"])?.takeIf { it > 0 },
                trackUri = positionInfo["TrackURI"]?.trim(),
                mediaUri = mediaInfo["CurrentURI"]?.trim(),
            )
    }
}

/**
 * What identifies a track URL across the speaker's round trip: host, port and decoded path, no
 * query (the token and the transcode session live there). Null for anything but http(s), such as
 * a streaming service's `x-sonos-…` track.
 */
fun trackKey(uri: String): String? {
    val url = uri.trim().toHttpUrlOrNull() ?: return null
    return "${url.host.lowercase()}:${url.port}/${url.pathSegments.joinToString("/")}"
}

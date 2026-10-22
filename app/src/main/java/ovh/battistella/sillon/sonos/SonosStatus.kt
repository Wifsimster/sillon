package ovh.battistella.sillon.sonos

import androidx.media3.common.Player

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
     * Whether the group still plays what Sillon sent: its own queue, and a track that came from
     * Sillon. Anything else means the Sonos app (or another controller) took the speaker over.
     */
    fun playsOurs(coordinatorUuid: String, ourUris: Set<String>): Boolean {
        if (mediaUri.isNullOrBlank() || mediaUri != AvTransport.queueUri(coordinatorUuid)) return false
        return trackUri.isNullOrBlank() || trackUri in ourUris
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

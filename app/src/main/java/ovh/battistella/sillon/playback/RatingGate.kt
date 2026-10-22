package ovh.battistella.sillon.playback

import androidx.media3.common.MediaItem
import ovh.battistella.sillon.playback.mystery.MysteryMask

/** Who may rate through the media session, and which track: only the one playing. */
object RatingGate {
    /**
     * The track a rating from a controller goes to, or null to refuse it. Only the app and the
     * controllers it trusts (notification, system, Android Auto) rate, and only the track playing:
     * a [requestedKey] for another track is refused rather than rated. A surprise sleeve not
     * revealed yet keeps its rating to itself.
     */
    fun target(trusted: Boolean, requestedKey: String?, current: MediaItem?): String? {
        if (!trusted) return null
        val key = current?.mediaId?.takeIf { it.isNotBlank() } ?: return null
        if (MysteryMask.isMasked(current)) return null
        if (requestedKey != null && requestedKey != key) return null
        return key
    }
}

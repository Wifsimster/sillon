package ovh.battistella.sillon.sonos

import androidx.media3.common.MediaItem
import ovh.battistella.sillon.playback.mystery.Mystery
import ovh.battistella.sillon.playback.mystery.MysteryMask

/** What never goes to a Sonos speaker. */
object SonosRefusal {
    /**
     * A hidden mystery sleeve: the Sonos app and any controller on the LAN show the speaker's
     * queue, so the album would show. Masked items, or any track of a sleeve not yet revealed.
     */
    fun refuses(items: List<MediaItem>, mystery: Mystery?): Boolean {
        if (items.any(MysteryMask::isMasked)) return true
        val hidden = mystery?.takeIf { !it.revealed } ?: return false
        return items.any { hidden.contains(it.mediaId) }
    }
}

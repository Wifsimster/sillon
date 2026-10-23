package ovh.battistella.sillon.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import ovh.battistella.sillon.playback.mystery.MysteryMask

/** Who may rate through the media session, and which track: only the one playing. */
object RatingGate {
    /** What a controller asking to rate gets: the track to rate, or the error to answer with. */
    sealed interface Verdict {
        data class Rate(val key: String) : Verdict
        data class Refuse(val error: Int) : Verdict
    }

    /**
     * The session commands a controller gets. Any app can connect to the session: only the trusted
     * ones (the app, the system, the notification, Android Auto) get to rate, with Media3's
     * setRating as with Sillon's own commands.
     */
    @OptIn(UnstableApi::class)
    fun commands(trusted: Boolean): SessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(SideSession.ACTION_FLIP, Bundle.EMPTY))
            .apply {
                if (trusted) {
                    add(SessionCommand(PlaybackConnection.ACTION_RATE, Bundle.EMPTY))
                    add(SessionCommand(PlaybackConnection.ACTION_LOVE, Bundle.EMPTY))
                    add(SessionCommand(PlaybackConnection.ACTION_DISLIKE, Bundle.EMPTY))
                } else {
                    remove(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
                }
            }
            .build()

    /**
     * Whether a rating from a controller goes through, checked again on every call: a controller
     * can send a command it wasn't offered. Only trusted controllers rate, and only the track
     * playing: a [requestedKey] for another track is refused rather than rated. A surprise sleeve
     * not revealed yet keeps its rating to itself.
     */
    @OptIn(UnstableApi::class)
    fun check(trusted: Boolean, requestedKey: String?, current: MediaItem?): Verdict {
        if (!trusted) return Verdict.Refuse(SessionError.ERROR_PERMISSION_DENIED)
        val key = current?.mediaId?.takeIf { it.isNotBlank() } ?: return Verdict.Refuse(SessionError.ERROR_BAD_VALUE)
        if (MysteryMask.isMasked(current)) return Verdict.Refuse(SessionError.ERROR_BAD_VALUE)
        if (requestedKey != null && requestedKey != key) return Verdict.Refuse(SessionError.ERROR_BAD_VALUE)
        return Verdict.Rate(key)
    }
}

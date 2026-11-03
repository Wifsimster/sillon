package ovh.battistella.sillon.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * What to do when the player can't read the track playing. A stream URL goes stale while the
 * music is paused: the server ends an idle transcode session, or the address of the server the
 * queue was built with can no longer be reached (home Wi-Fi left behind). The player then plays
 * what it had buffered and stops on a source error, and pressing play again asks the same dead
 * URL. The car shows "Source error" each time.
 *
 * So a source error first asks the server for the track again ([Action.REFRESH]: a new transcode
 * session, on the address the server answers at now). A transcode that still fails falls back to
 * the original file ([Action.ORIGINAL]). Each track gets that budget once per [windowMs], so a
 * track that keeps failing stops in error instead of looping, and a later pause can recover again.
 */
class SourceRecovery(private val windowMs: Long = WINDOW_MS) {
    enum class Action { REFRESH, ORIGINAL, GIVE_UP }

    private class Attempts(val sinceMs: Long, val tried: MutableSet<Action> = mutableSetOf())

    private val attempts = mutableMapOf<String, Attempts>()

    fun decide(mediaId: String, uri: String, errorCode: Int, nowMs: Long): Action {
        if (mediaId.isBlank() || !isRemote(uri)) return Action.GIVE_UP
        val current = attempts[mediaId]?.takeIf { nowMs - it.sinceMs < windowMs }
            ?: Attempts(nowMs).also { attempts[mediaId] = it }
        val action = when {
            isSourceError(errorCode) && Action.REFRESH !in current.tried -> Action.REFRESH
            MediaItems.isTranscode(uri) && Action.ORIGINAL !in current.tried -> Action.ORIGINAL
            else -> Action.GIVE_UP
        }
        if (action != Action.GIVE_UP) current.tried += action
        return action
    }

    companion object {
        const val WINDOW_MS = 60_000L

        /** Plays [fresh] in place of the track at [index], from [positionMs] where it stopped. */
        fun resume(player: Player, index: Int, fresh: MediaItem, positionMs: Long) {
            player.replaceMediaItem(index, fresh)
            // A new URL replaces the item, which would start it over.
            player.seekTo(index, positionMs)
            player.prepare()
            player.play()
        }

        /** Reading the stream failed (network, HTTP status, a server answer that isn't audio), not decoding it. */
        fun isSourceError(errorCode: Int): Boolean = errorCode in IO_AND_PARSING

        // ERROR_CODE_IO_UNSPECIFIED (2000) up to ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED (3004).
        private val IO_AND_PARSING = PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED

        // A download plays from a file: asking the server again changes nothing.
        private fun isRemote(uri: String): Boolean = uri.startsWith("http://") || uri.startsWith("https://")
    }
}

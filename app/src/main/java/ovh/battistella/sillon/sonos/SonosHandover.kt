package ovh.battistella.sillon.sonos

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import ovh.battistella.sillon.data.repository.SonosAccess
import ovh.battistella.sillon.data.repository.SonosAccessException

/**
 * Moves the music between the phone's player and a Sonos group, both ways, keeping the queue,
 * the track and the position. The playback service owns it and swaps the media session's player
 * through [attach].
 */
@OptIn(UnstableApi::class)
class SonosHandover(
    private val phone: Player,
    private val scope: CoroutineScope,
    private val client: SoapCaller,
    private val openAccess: suspend () -> SonosAccess,
    private val tracks: suspend (keys: List<String>, access: SonosAccess) -> Map<String, SonosTrack>,
    private val refuse: (List<MediaItem>) -> Boolean,
    private val togetherActive: () -> Boolean,
    private val show: (Output) -> Unit,
    private val tell: (OutputNotice) -> Unit,
    /** The Sonos player now behind the media session, or null for the phone's. */
    private val attach: (SonosPlayer?) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    var player: SonosPlayer? = null
        private set
    private var access: SonosAccess? = null

    /** Hands the queue to [group]: the speaker fetches the tracks from the server itself. */
    suspend fun playOn(group: SonosGroup) {
        val current = player
        if (current?.group?.id == group.id) return
        if (togetherActive()) return tell(OutputNotice.TOGETHER_ON_PHONE)
        val (items, index, positionMs) = current?.handBack() ?: phoneQueue()
        if (items.isEmpty()) return
        if (refuse(items)) return tell(OutputNotice.MYSTERY_ON_PHONE)
        val play = current?.wasPlaying ?: phone.playWhenReady
        show(Output.Connecting(group))
        // The phone stops while the queue goes out, so the two never play together.
        val phoneWasPlaying = current == null && phone.playWhenReady
        if (phoneWasPlaying) phone.pause()
        if (current != null) leave(current, pause = true)
        val access = try {
            openAccess()
        } catch (e: Exception) {
            Log.w(TAG, "no way to the server for the speaker: ${e.message}")
            if (current != null) restoreOnPhone(items, index, positionMs, play = false)
            if (phoneWasPlaying) phone.play()
            show(Output.Phone)
            tell(
                when ((e as? SonosAccessException)?.reason) {
                    SonosAccessException.Reason.PLEX_TOKEN -> OutputNotice.PLEX_TOKEN_REFUSED
                    SonosAccessException.Reason.JELLYFIN_TOKEN -> OutputNotice.JELLYFIN_TOKEN_REFUSED
                    null -> OutputNotice.FAILED
                },
            )
            return
        }
        val next = SonosPlayer(
            group = group,
            client = client,
            scope = scope,
            resolve = { sent -> resolve(sent, access) },
            refuse = refuse,
            onRefused = { refused, at, position ->
                backToPhone(resume = false)
                phone.setMediaItems(refused, at, position)
                phone.prepare()
                phone.play()
                tell(OutputNotice.MYSTERY_ON_PHONE)
            },
            onLost = { reason ->
                // Someone else plays on the speaker now: leave it alone.
                backToPhone(resume = false, pause = reason != SonosLoss.TAKEN_OVER)
                tell(
                    when (reason) {
                        SonosLoss.UNREACHABLE -> OutputNotice.UNREACHABLE
                        SonosLoss.COULD_NOT_PLAY -> OutputNotice.COULD_NOT_PLAY
                        SonosLoss.TAKEN_OVER -> OutputNotice.TAKEN_OVER
                    },
                )
            },
            onRefusedAdd = { tell(OutputNotice.MYSTERY_NOT_ADDED) },
            now = now,
        )
        val started = runCatching { next.start(items, index, positionMs, play, phone.shuffleModeEnabled, phone.repeatMode) }
        if (started.isFailure) {
            Log.w(TAG, "Sonos did not take the queue: ${started.exceptionOrNull()?.message}")
            next.release()
            access.close()
            if (current != null) restoreOnPhone(items, index, positionMs, play = false)
            if (phoneWasPlaying) phone.play()
            show(Output.Phone)
            tell(OutputNotice.FAILED)
            return
        }
        player = next
        this.access = access
        attach(next)
        show(Output.OnSonos(group))
    }

    /** Brings the music back to the phone where the speaker stands; plays on when [resume] and it was playing. */
    suspend fun backToPhone(resume: Boolean, pause: Boolean = true) {
        val current = player ?: return show(Output.Phone)
        val (items, index, positionMs) = current.handBack()
        val wasPlaying = current.wasPlaying
        leave(current, pause)
        restoreOnPhone(items, index, positionMs, resume && wasPlaying)
        show(Output.Phone)
    }

    /** The service is going away: stop following the speaker (it plays on) and end its access. */
    suspend fun release() {
        player?.let {
            attach(null)
            it.release()
        }
        player = null
        access?.close()
        access = null
        show(Output.Phone)
    }

    private suspend fun leave(current: SonosPlayer, pause: Boolean) {
        player = null
        attach(null)
        if (pause) current.pauseSpeaker()
        current.release()
        access?.close()
        access = null
    }

    private fun phoneQueue(): Triple<List<MediaItem>, Int, Long> =
        Triple((0 until phone.mediaItemCount).map(phone::getMediaItemAt), phone.currentMediaItemIndex, phone.currentPosition)

    private fun restoreOnPhone(items: List<MediaItem>, index: Int, positionMs: Long, play: Boolean) {
        if (items.isEmpty()) return
        val queue = (0 until phone.mediaItemCount).map { phone.getMediaItemAt(it).mediaId }
        val at = SonosQueue.phoneIndex(queue, items.map { it.mediaId }, index)
        if (at != null) phone.seekTo(at, positionMs) else phone.setMediaItems(items, index, positionMs)
        phone.prepare()
        if (play) phone.play()
    }

    private suspend fun resolve(items: List<MediaItem>, access: SonosAccess): List<SonosTrack?> {
        val found = runCatching { tracks(items.map { it.mediaId }, access) }
            .onFailure { Log.w(TAG, "Sonos tracks failed: ${it.message}") }
            .getOrDefault(emptyMap())
        return items.map { found[it.mediaId] }
    }

    private companion object {
        const val TAG = "SillonSonos"
    }
}

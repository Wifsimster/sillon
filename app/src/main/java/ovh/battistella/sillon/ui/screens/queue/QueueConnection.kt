package ovh.battistella.sillon.ui.screens.queue

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.battistella.sillon.playback.MediaItems
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The queue through the media session, like any other controller: whatever player the session
 * holds (this phone, a listening group, a Sonos speaker) gets the jumps, removals and moves.
 */
@Singleton
class QueueConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _snapshot = MutableStateFlow(QueueSnapshot())
    val snapshot = _snapshot.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<MediaController.() -> Unit>()

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) = release()
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                sync()
            }
        }
    }

    fun connect() {
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val next = MediaController.Builder(context, token).setListener(controllerListener).buildAsync()
        future = next
        next.addListener({
            try {
                val c = next.get()
                controller = c
                c.addListener(playerListener)
                sync()
                pending.toList().forEach { it(c) }
                pending.clear()
            } catch (t: Throwable) {
                controller = null
                future = null
                pending.clear()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        future = null
        _snapshot.value = QueueSnapshot()
    }

    private fun withController(action: MediaController.() -> Unit) {
        val c = controller
        if (c != null) {
            c.action()
        } else {
            pending += action
            connect()
        }
    }

    fun jumpTo(index: Int) = withController {
        if (index !in 0 until mediaItemCount) return@withController
        seekToDefaultPosition(index)
        if (playbackState == Player.STATE_IDLE) prepare()
        play()
    }

    fun remove(index: Int) = withController {
        // The track playing stays: skipping it is the player's job.
        if (index !in 0 until mediaItemCount || index == currentMediaItemIndex) return@withController
        removeMediaItem(index)
    }

    fun move(from: Int, to: Int) = withController {
        if (from !in 0 until mediaItemCount || to !in 0 until mediaItemCount || from == to) return@withController
        moveMediaItem(from, to)
    }

    /** Right after the track playing; with nothing queued, they start playing. */
    fun playNext(tracks: List<PlayableTrack>) = insert(tracks, next = true)

    /** At the end of the queue; with nothing queued, they start playing. */
    fun append(tracks: List<PlayableTrack>) = insert(tracks, next = false)

    private fun insert(tracks: List<PlayableTrack>, next: Boolean) {
        if (tracks.isEmpty()) return
        val items = tracks.map(MediaItems::from)
        withController {
            when {
                mediaItemCount == 0 -> {
                    setMediaItems(items, 0, 0L)
                    prepare()
                    play()
                }
                next -> addMediaItems(currentMediaItemIndex + 1, items)
                else -> addMediaItems(items)
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map(c::getMediaItemAt)
        _snapshot.value = QueueSnapshot(
            items = QueueItem.of(items),
            order = playOrder(c.currentTimeline, c.shuffleModeEnabled),
            currentIndex = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
        )
    }

    private fun playOrder(timeline: Timeline, shuffle: Boolean): List<Int> {
        if (timeline.isEmpty) return emptyList()
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        return order
    }
}

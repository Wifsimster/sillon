package ovh.battistella.sillon.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val isConnected: Boolean = false,
    val currentKey: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
) {
    val hasMedia: Boolean get() = currentKey != null
}

@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null

    private var pending: (MediaController.() -> Unit)? = null

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) = releaseController()
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromController()
    }

    init {
        scope.launch {
            _state.subscriptionCount.collect { count ->
                if (count > 0 && controller?.isPlaying == true) startPositionTicker()
                if (count == 0) stopPositionTicker()
            }
        }
        connect()
    }

    private fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                syncFromController()
                pending?.invoke(c)
                pending = null
            } catch (t: Throwable) {
                controller = null
                controllerFuture = null
                _state.value = _state.value.copy(isConnected = false)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun releaseController() {
        stopPositionTicker()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        controllerFuture = null
        _state.value = _state.value.copy(isConnected = false, isPlaying = false)
    }

    private fun withController(action: MediaController.() -> Unit) {
        val c = controller
        if (c != null) {
            c.action()
        } else {
            pending = action
            connect()
        }
    }

    fun play(tracks: List<PlayableTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val items = tracks.map(MediaItems::from)
        withController {
            shuffleModeEnabled = shuffle
            val start = if (shuffle) items.indices.random() else startIndex.coerceIn(0, items.lastIndex)
            setMediaItems(items, start, 0L)
            prepare()
            play()
        }
    }

    fun replaceUpcoming(tracks: List<PlayableTrack>) {
        withController {
            val items = tracks.map(MediaItems::from).filter { it.mediaId != currentMediaItem?.mediaId }
            if (items.isEmpty()) return@withController
            val next = currentMediaItemIndex + 1
            if (next < mediaItemCount) removeMediaItems(next, mediaItemCount)
            shuffleModeEnabled = false
            addMediaItems(items)
        }
    }

    fun playPause() = withController {
        if (isPlaying) {
            pause()
        } else {
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }

    fun next() = withController { if (hasNextMediaItem()) seekToNextMediaItem() }

    fun previous() = withController { seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { this.seekTo(positionMs) }

    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun startPositionTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val c = controller ?: break
                if (!c.isPlaying || _state.subscriptionCount.value == 0) break
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    durationMs = c.duration.let { if (it == C.TIME_UNSET) 0L else it },
                )
                delay(500)
            }
            tickerJob = null
        }
    }

    private fun stopPositionTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun syncFromController() {
        val c = controller ?: return
        if (c.isPlaying && _state.subscriptionCount.value > 0) startPositionTicker() else stopPositionTicker()
        val item = c.currentMediaItem
        val metadata = item?.mediaMetadata
        _state.value = PlayerUiState(
            isConnected = true,
            currentKey = item?.mediaId?.takeIf { it.isNotBlank() },
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = metadata?.albumTitle?.toString().orEmpty(),
            artworkUri = metadata?.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.let { if (it == C.TIME_UNSET) 0L else it },
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }
}

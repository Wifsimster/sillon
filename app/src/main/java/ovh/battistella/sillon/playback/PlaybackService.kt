package ovh.battistella.sillon.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import ovh.battistella.sillon.BuildConfig
import ovh.battistella.sillon.MainActivity
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SavedQueue
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var libraryTree: MediaLibraryTree
    @Inject lateinit var repository: PlexRepository
    @Inject lateinit var playbackState: PlaybackStateStore
    @Inject lateinit var sides: SideSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaLibrarySession? = null
    private var timelineReporter: TimelineReporter? = null
    private var sidePlayer: SidePlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        if (BuildConfig.DEBUG) player.addAnalyticsListener(EventLogger("SillonPlayer"))
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = fallBackToOriginal(player, error)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyGain(player, mediaItem)
                saveQueue(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startQueueSaver(player) else saveQueue(player)
            }
        })
        timelineReporter = TimelineReporter(player, scope) send@{ report ->
            val server = report.server ?: runCatching { repository.endpoint() }.getOrNull() ?: return@send
            repository.reportPlayback(
                server = server,
                ratingKey = report.ratingKey,
                state = report.state,
                timeMs = report.timeMs,
                durationMs = report.durationMs,
                continuing = report.continuing,
            )
        }.also { it.start() }
        val sidePlayer = SidePlayer(
            exo = player,
            sides = sides,
            describe = { end ->
                getString(R.string.side_end_title, end.ended) to getString(R.string.side_flip_hint, end.next)
            },
            onEndedChanged = { end ->
                mediaSession?.setCustomLayout(if (end != null) listOf(flipButton()) else emptyList())
                scope.launch { runCatching { sides.save() } }
            },
        ).also { sidePlayer = it }
        mediaSession = MediaLibrarySession.Builder(this, sidePlayer, LibraryCallback())
            .setSessionActivity(openAppIntent())
            .build()
        scope.launch {
            runCatching { sides.restore() }
            sides.planChanges.collect {
                sidePlayer.sync()
                runCatching { sides.save() }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun flipButton(): CommandButton = CommandButton.Builder(CommandButton.ICON_SYNC)
        .setDisplayName(getString(R.string.side_flip))
        .setCustomIconResId(R.drawable.ic_side_flip)
        .setSessionCommand(SessionCommand(SideSession.ACTION_FLIP, Bundle.EMPTY))
        .build()

    private fun applyGain(player: Player, item: MediaItem?) {
        val gain = item?.mediaMetadata?.extras
            ?.takeIf { it.containsKey(MediaItems.EXTRA_GAIN_DB) }
            ?.getFloat(MediaItems.EXTRA_GAIN_DB)
        player.volume = MediaItems.volumeFor(gain)
        if (BuildConfig.DEBUG) Log.d(TAG, "gain ${item?.mediaId}: $gain dB -> volume ${player.volume}")
    }

    private var queueSaver: Job? = null

    private fun startQueueSaver(player: Player) {
        if (queueSaver?.isActive == true) return
        queueSaver = scope.launch {
            while (player.isPlaying) {
                delay(QUEUE_SAVE_INTERVAL_MS)
                saveQueue(player)
            }
        }
    }

    private fun saveQueue(player: Player) {
        val queue = snapshot(player) ?: return
        scope.launch(Dispatchers.IO) { runCatching { playbackState.save(queue) } }
    }

    private fun snapshot(player: Player): SavedQueue? {
        if (player.mediaItemCount == 0) return null
        val count = player.mediaItemCount.coerceAtMost(MAX_SAVED_TRACKS)
        val keys = (0 until count).map { player.getMediaItemAt(it).mediaId }.filter { it.isNotBlank() }
        if (keys.isEmpty()) return null
        val current = player.currentMediaItem
        return SavedQueue(
            keys = keys,
            index = player.currentMediaItemIndex.coerceIn(0, keys.lastIndex),
            positionMs = player.currentPosition.coerceAtLeast(0),
            title = current?.mediaMetadata?.title?.toString().orEmpty(),
            artist = current?.mediaMetadata?.artist?.toString().orEmpty(),
        )
    }

    private fun grantArtwork(browser: MediaSession.ControllerInfo, items: List<MediaItem>) {
        val uris = items.mapNotNull { it.mediaMetadata.artworkUri }
        runCatching { ArtworkProvider.grant(this, browser.packageName, uris) }
            .onFailure { Log.w(TAG, "artwork grant to ${browser.packageName} failed", it) }
    }

    private val fellBack = mutableSetOf<String>()

    private fun fallBackToOriginal(player: Player, error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        if (!MediaItems.isTranscode(uri) || !fellBack.add(item.mediaId)) return
        Log.w(TAG, "transcode failed (${error.errorCodeName}), falling back to the original file")
        val index = player.currentMediaItemIndex
        scope.launch {
            val original = runCatching {
                repository.metadata(item.mediaId)?.let { repository.streamUrl(repository.endpoint(), it) }
            }.getOrNull() ?: return@launch
            if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
            player.replaceMediaItem(index, item.buildUpon().setUri(original).setMimeType(null).build())
            player.prepare()
            player.play()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            snapshot(player)?.let { queue -> runBlocking { runCatching { playbackState.save(queue) } } }
        }
        timelineReporter?.release()
        timelineReporter = null
        sidePlayer = null
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(SideSession.ACTION_FLIP, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SideSession.ACTION_FLIP) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            sidePlayer?.flip()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            when {
                !libraryTree.isCallerTrusted(browser) -> {
                    Log.w(TAG, "browse root denied to ${browser.packageName}")
                    LibraryResult.ofError(SessionResult.RESULT_ERROR_PERMISSION_DENIED)
                }
                params?.isRecent == true -> libraryTree.recentRoot()
                    ?.let { LibraryResult.ofItem(it, libraryTree.rootParams()) }
                    ?: LibraryResult.ofError(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                else -> LibraryResult.ofItem(libraryTree.root(), libraryTree.rootParams())
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            if (!libraryTree.isCallerTrusted(browser)) {
                return@future LibraryResult.ofError(SessionResult.RESULT_ERROR_PERMISSION_DENIED)
            }
            runCatching { libraryTree.item(mediaId) }.fold(
                onSuccess = { item ->
                    item?.let { grantArtwork(browser, listOf(it)) }
                    item?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                },
                onFailure = {
                    Log.w(TAG, "onGetItem failed", it)
                    LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
                },
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            if (!libraryTree.isCallerTrusted(browser)) {
                return@future LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), null)
            }
            runCatching { libraryTree.children(parentId, page, pageSize) }.fold(
                onSuccess = {
                    grantArtwork(browser, it)
                    LibraryResult.ofItemList(ImmutableList.copyOf(it), null)
                },
                onFailure = {
                    Log.w(TAG, "onGetChildren failed", it)
                    LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
                },
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future {
            runCatching { libraryTree.resolveEach(mediaItems) }.getOrElse {
                Log.w(TAG, "onAddMediaItems failed", it)
                emptyList()
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            runCatching { libraryTree.resumption() }.getOrElse {
                Log.w(TAG, "onPlaybackResumption failed", it)
                MediaItemsWithStartPosition(emptyList(), 0, 0L)
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            runCatching { libraryTree.resolve(mediaItems, startIndex, startPositionMs) }.getOrElse {
                Log.w(TAG, "onSetMediaItems failed", it)
                MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
            }
        }
    }

    private companion object {
        const val TAG = "SillonAuto"
        const val SESSION_ACTIVITY_REQUEST_CODE = 3_001
        const val QUEUE_SAVE_INTERVAL_MS = 15_000L
        const val MAX_SAVED_TRACKS = 500
    }
}

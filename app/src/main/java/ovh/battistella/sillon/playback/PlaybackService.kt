package ovh.battistella.sillon.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import ovh.battistella.sillon.MainActivity
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.SessionStore
import javax.inject.Inject

/**
 * Foreground media service owning the ExoPlayer and its MediaSession: playback
 * keeps going with the screen off, and the notification, lock screen and
 * Bluetooth/headset buttons all control it.
 *
 * Tracks queued together play gaplessly when the files carry gapless metadata
 * (MP3 LAME/iTunes headers, AAC/M4A); FLAC is sample-exact by nature.
 *
 * Playback is reported to the Plex server by [TimelineReporter].
 *
 * A [MediaLibraryService] so Android Auto (and other media browsers) can
 * navigate the library through [MediaLibraryTree].
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var plexApi: PlexApi
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var libraryTree: MediaLibraryTree
    @Inject lateinit var repository: PlexRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaLibrarySession? = null
    private var timelineReporter: TimelineReporter? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            // Keep the Wi-Fi and CPU awake while streaming with the screen off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        timelineReporter = TimelineReporter(player, scope) send@{ report ->
            // A downloaded track plays from a file: report it to the current server.
            val server = report.server ?: runCatching { repository.endpoint() }.getOrNull() ?: return@send
            plexApi.timeline(
                clientId = sessionStore.clientId(),
                server = server,
                ratingKey = report.ratingKey,
                state = report.state,
                timeMs = report.timeMs,
                durationMs = report.durationMs,
                continuing = report.continuing,
            )
        }.also { it.start() }
        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in the background when music is on; otherwise stop.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Before releasing the player: the final report reads its position.
        timelineReporter?.release()
        timelineReporter = null
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

    /**
     * Bridges the Media3 library callbacks to [MediaLibraryTree]: the caller
     * gate, coroutine-to-future plumbing, and errors mapped to [LibraryResult].
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {

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
                // The "recent" root feeds a resume card; there is no play history to offer yet.
                params?.isRecent == true -> LibraryResult.ofError(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
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
                onSuccess = { LibraryResult.ofItemList(ImmutableList.copyOf(it), null) },
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
        /** `adb logcat -s SillonAuto` for Android Auto diagnostics. */
        const val TAG = "SillonAuto"
        const val SESSION_ACTIVITY_REQUEST_CODE = 3_001
    }
}

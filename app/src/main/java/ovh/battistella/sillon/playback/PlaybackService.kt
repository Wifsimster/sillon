package ovh.battistella.sillon.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ovh.battistella.sillon.MainActivity
import ovh.battistella.sillon.data.plex.PlexApi
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
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var plexApi: PlexApi
    @Inject lateinit var sessionStore: SessionStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
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
        timelineReporter = TimelineReporter(player, scope) { report ->
            plexApi.timeline(
                clientId = sessionStore.clientId(),
                server = report.server,
                ratingKey = report.ratingKey,
                state = report.state,
                timeMs = report.timeMs,
                durationMs = report.durationMs,
                continuing = report.continuing,
            )
        }.also { it.start() }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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

    private inner class SessionCallback : MediaSession.Callback {
        /** Restore stream URIs stripped from items sent by out-of-process controllers. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture<List<MediaItem>>(mediaItems.mapNotNull(MediaItems::restoreUri))
    }

    private companion object {
        const val SESSION_ACTIVITY_REQUEST_CODE = 3_001
    }
}

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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import ovh.battistella.sillon.BuildConfig
import ovh.battistella.sillon.MainActivity
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.SonosAccess
import ovh.battistella.sillon.data.repository.SonosAccessException
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SavedQueue
import ovh.battistella.sillon.playback.cache.ListeningCache
import ovh.battistella.sillon.playback.cache.ListeningCacheKeys
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysterySession
import ovh.battistella.sillon.playback.mystery.MysteryWatcher
import ovh.battistella.sillon.playback.mystery.toSaved
import ovh.battistella.sillon.sonos.Output
import ovh.battistella.sillon.sonos.OutputNotice
import ovh.battistella.sillon.sonos.OutputRequest
import ovh.battistella.sillon.sonos.SonosGroup
import ovh.battistella.sillon.sonos.SonosLoss
import ovh.battistella.sillon.sonos.SonosOutput
import ovh.battistella.sillon.sonos.SonosPlayer
import ovh.battistella.sillon.sonos.SonosQueue
import ovh.battistella.sillon.sonos.SonosTrack
import ovh.battistella.sillon.together.ListenTogether
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var libraryTree: MediaLibraryTree
    @Inject lateinit var repository: PlexRepository
    @Inject lateinit var playbackState: PlaybackStateStore
    @Inject lateinit var mystery: MysterySession
    @Inject lateinit var together: ListenTogether
    @Inject lateinit var neverPlayed: NeverPlayedRadio
    @Inject lateinit var sides: SideSession
    @Inject lateinit var listeningCache: ListeningCache
    @Inject lateinit var sonosOutput: SonosOutput

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaLibrarySession? = null
    private var timelineReporter: TimelineReporter? = null
    private var mysteryWatcher: MysteryWatcher? = null
    private var groupPlayer: ExoGroupPlayer? = null
    private var sidePlayer: SidePlayer? = null
    private var exo: ExoPlayer? = null
    private var sonos: SonosPlayer? = null
    private var sonosReporter: TimelineReporter? = null
    private var sonosAccess: SonosAccess? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val dataSources = listeningCache.dataSourceFactory(DefaultDataSource.Factory(this))
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo = player
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
        player.addListener(NeverPlayedRefill(player, scope, neverPlayed))
        timelineReporter = TimelineReporter(player, scope, ::report).also { it.start() }
        // Sides take pauseAtEndOfMediaItems back only after the group has let go of it.
        groupPlayer = ExoGroupPlayer(player, afterExit = { sidePlayer?.sync() }).also(together::attach)
        mysteryWatcher = MysteryWatcher(player, mystery, scope, together.state.map { it.isActive }).also { it.start() }
        val sidePlayer = SidePlayer(
            base = TogetherPlayer(player, together),
            exo = player,
            grouped = { together.state.value.isActive },
            inGroupMode = { groupPlayer?.inGroupMode == true },
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
        // Joining turns sides off at once, before the group takes the player.
        scope.launch(Dispatchers.Main.immediate) {
            together.state.map { it.isActive }.distinctUntilChanged().collect { sidePlayer.sync() }
        }
        scope.launch {
            sonosOutput.requests.collect { request ->
                when (request) {
                    is OutputRequest.PlayOn -> playOnSonos(request.group)
                    OutputRequest.BackToPhone -> backToPhone(resume = true)
                }
            }
        }
        // Listen together plays on each phone: joining a group brings the music back here.
        scope.launch {
            together.state.map { it.isActive }.distinctUntilChanged().collect { active ->
                if (active && sonos != null) {
                    backToPhone(resume = false)
                    sonosOutput.tell(OutputNotice.TOGETHER_ON_PHONE)
                }
            }
        }
        scope.launch {
            runCatching { sides.restore() }
            sides.planChanges.collect {
                sidePlayer.sync()
                runCatching { sides.save() }
            }
        }
    }

    private suspend fun report(report: TimelineReport) {
        val server = report.server ?: runCatching { repository.endpoint() }.getOrNull() ?: return
        repository.reportPlayback(
            server = server,
            ratingKey = report.ratingKey,
            state = report.state,
            timeMs = report.timeMs,
            durationMs = report.durationMs,
            continuing = report.continuing,
        )
    }

    /**
     * Hands the queue to a Sonos group: the speaker fetches the tracks from the server itself, and
     * the media session (notification, lock screen, player screen, volume keys) drives it.
     */
    @OptIn(UnstableApi::class)
    private suspend fun playOnSonos(group: SonosGroup) {
        val exo = exo ?: return
        val current = sonos
        if (current?.group?.id == group.id) return
        if (together.state.value.isActive) return sonosOutput.tell(OutputNotice.TOGETHER_ON_PHONE)
        val (items, index, positionMs) = current?.handBack()
            ?: Triple((0 until exo.mediaItemCount).map(exo::getMediaItemAt), exo.currentMediaItemIndex, exo.currentPosition)
        if (items.isEmpty()) return
        if (refusedOnSonos(items)) return sonosOutput.tell(OutputNotice.MYSTERY_ON_PHONE)
        val play = current?.wasPlaying ?: exo.playWhenReady
        sonosOutput.set(Output.Connecting(group))
        if (current != null) leaveSonos(current, pause = true)
        val access = try {
            repository.openSonosAccess()
        } catch (e: Exception) {
            Log.w(TAG, "no way to the server for the speaker: ${e.message}")
            if (current != null) restoreOnPhone(items, index, positionMs, play = false)
            sonosOutput.set(Output.Phone)
            sonosOutput.tell(
                when ((e as? SonosAccessException)?.reason) {
                    SonosAccessException.Reason.PLEX_TOKEN -> OutputNotice.PLEX_TOKEN_REFUSED
                    SonosAccessException.Reason.JELLYFIN_TOKEN -> OutputNotice.JELLYFIN_TOKEN_REFUSED
                    null -> OutputNotice.FAILED
                },
            )
            return
        }
        val player = SonosPlayer(
            group = group,
            client = sonosOutput.client,
            scope = scope,
            resolve = { resolveForSonos(it, access) },
            refuse = ::refusedOnSonos,
            onRefused = { refused, at, position ->
                backToPhone(resume = false)
                exo.setMediaItems(refused, at, position)
                exo.prepare()
                exo.play()
                sonosOutput.tell(OutputNotice.MYSTERY_ON_PHONE)
            },
            onLost = { reason ->
                // Someone else plays on the speaker now: leave it alone.
                backToPhone(resume = false, pause = reason != SonosLoss.TAKEN_OVER)
                sonosOutput.tell(
                    when (reason) {
                        SonosLoss.UNREACHABLE -> OutputNotice.UNREACHABLE
                        SonosLoss.COULD_NOT_PLAY -> OutputNotice.COULD_NOT_PLAY
                        SonosLoss.TAKEN_OVER -> OutputNotice.TAKEN_OVER
                    },
                )
            },
        )
        val started = runCatching { player.start(items, index, positionMs, play, exo.shuffleModeEnabled, exo.repeatMode) }
        if (started.isFailure) {
            Log.w(TAG, "Sonos did not take the queue: ${started.exceptionOrNull()?.message}")
            player.release()
            access.close()
            if (current != null) restoreOnPhone(items, index, positionMs, play = false)
            sonosOutput.set(Output.Phone)
            sonosOutput.tell(OutputNotice.FAILED)
            return
        }
        exo.pause()
        // Sides pause the phone's player between faces; the speaker plays the record through.
        sides.setPlan(null)
        sonos = player
        sonosAccess = access
        mediaSession?.player = player
        sonosReporter = TimelineReporter(player, scope, ::report).also { it.start() }
        sonosOutput.set(Output.OnSonos(group))
    }

    /** Brings the music back to the phone where the speaker stands; plays on when [resume] and it was playing. */
    private suspend fun backToPhone(resume: Boolean, pause: Boolean = true) {
        val player = sonos ?: return sonosOutput.set(Output.Phone)
        val (items, index, positionMs) = player.handBack()
        val wasPlaying = player.wasPlaying
        leaveSonos(player, pause)
        restoreOnPhone(items, index, positionMs, resume && wasPlaying)
        sonosOutput.set(Output.Phone)
    }

    @OptIn(UnstableApi::class)
    private suspend fun leaveSonos(player: SonosPlayer, pause: Boolean) {
        sonos = null
        sonosReporter?.release()
        sonosReporter = null
        sidePlayer?.let { mediaSession?.player = it }
        if (pause) player.pauseSpeaker()
        player.release()
        sonosAccess?.close()
        sonosAccess = null
    }

    private fun restoreOnPhone(items: List<MediaItem>, index: Int, positionMs: Long, play: Boolean) {
        val exo = exo ?: return
        if (items.isEmpty()) return
        val phone = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
        val at = SonosQueue.phoneIndex(phone, items.map { it.mediaId }, index)
        if (at != null) exo.seekTo(at, positionMs) else exo.setMediaItems(items, index, positionMs)
        exo.prepare()
        if (play) exo.play()
    }

    private suspend fun resolveForSonos(items: List<MediaItem>, access: SonosAccess): List<SonosTrack?> {
        val tracks = runCatching { repository.sonosTracks(items.map { it.mediaId }, access) }
            .onFailure { Log.w(TAG, "Sonos tracks failed: ${it.message}") }
            .getOrDefault(emptyMap())
        return items.map { tracks[it.mediaId] }
    }

    /** A hidden mystery sleeve stays on the phone: the Sonos app would show the album. */
    private fun refusedOnSonos(items: List<MediaItem>): Boolean {
        if (items.any(MysteryMask::isMasked)) return true
        val hidden = mystery.state.value?.takeIf { !it.revealed } ?: return false
        return items.any { hidden.contains(it.mediaId) }
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
            mystery = mystery.state.value?.takeIf { m -> keys.any(m::contains) }?.toSaved(),
        )
    }

    private fun grantArtwork(browser: MediaSession.ControllerInfo, items: List<MediaItem>) {
        val uris = items.mapNotNull { it.mediaMetadata.artworkUri }
        runCatching { ArtworkProvider.grant(this, browser.packageName, uris) }
            .onFailure { Log.w(TAG, "artwork grant to ${browser.packageName} failed", it) }
    }

    // The mystery sleeve sits in the now-playing metadata, not in a browse result.
    private fun grantSleeve(browser: MediaSession.ControllerInfo) {
        runCatching { ArtworkProvider.grant(this, browser.packageName, listOf(ArtworkProvider.SLEEVE_URI)) }
            .onFailure { Log.w(TAG, "sleeve grant to ${browser.packageName} failed", it) }
    }

    private val fellBack = mutableSetOf<String>()

    @OptIn(UnstableApi::class)
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
            val cacheKey = runCatching { ListeningCacheKeys.track(repository.currentServerId(), item.mediaId) }.getOrNull()
            if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
            player.replaceMediaItem(
                index,
                item.buildUpon().setUri(original).setMimeType(null).setCustomCacheKey(cacheKey).build(),
            )
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

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            snapshot(player)?.let { queue -> runBlocking { runCatching { playbackState.save(queue) } } }
        }
        timelineReporter?.release()
        timelineReporter = null
        mysteryWatcher?.release()
        mysteryWatcher = null
        groupPlayer?.let(together::detach)
        groupPlayer = null
        sidePlayer = null
        // The speaker plays on by itself; Sillon only stops following it.
        sonosReporter?.release()
        sonosReporter = null
        sonos?.release()
        sonos = null
        // The speaker's own token ends with the session (bounded wait: the service is going away).
        sonosAccess?.let { access -> runBlocking { withTimeoutOrNull(ACCESS_CLOSE_MS) { access.close() } } }
        sonosAccess = null
        sonosOutput.set(Output.Phone)
        scope.cancel()
        mediaSession?.release()
        exo?.release()
        exo = null
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
        @OptIn(UnstableApi::class)
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

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SideSession.ACTION_FLIP) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
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
                    .also { grantSleeve(browser) }
                    ?.let { LibraryResult.ofItem(it, libraryTree.rootParams()) }
                    ?: LibraryResult.ofError(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                else -> LibraryResult.ofItem(libraryTree.root(), libraryTree.rootParams())
                    .also { grantSleeve(browser) }
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
        const val ACCESS_CLOSE_MS = 2_000L
    }
}

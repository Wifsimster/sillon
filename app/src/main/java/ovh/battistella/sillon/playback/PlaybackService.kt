package ovh.battistella.sillon.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.ThumbRating
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
import kotlinx.coroutines.flow.combine
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
import ovh.battistella.sillon.data.rating.Rater
import ovh.battistella.sillon.data.rating.Stars
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SavedQueue
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.playback.cache.ListeningCache
import ovh.battistella.sillon.playback.cache.ListeningCacheKeys
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysterySession
import ovh.battistella.sillon.playback.mystery.MysteryWatcher
import ovh.battistella.sillon.playback.mystery.toSaved
import ovh.battistella.sillon.sonos.OutputNotice
import ovh.battistella.sillon.sonos.OutputRequest
import ovh.battistella.sillon.sonos.SonosHandover
import ovh.battistella.sillon.sonos.SonosLocks
import ovh.battistella.sillon.sonos.SonosOutput
import ovh.battistella.sillon.sonos.SonosPlayer
import ovh.battistella.sillon.sonos.SonosRefusal
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
    @Inject lateinit var rater: Rater
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var sonosOutput: SonosOutput

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaLibrarySession? = null
    private var timelineReporter: TimelineReporter? = null
    private var mysteryWatcher: MysteryWatcher? = null
    private var groupPlayer: ExoGroupPlayer? = null
    private var sidePlayer: SidePlayer? = null
    private var skipper: OneStarSkipper? = null
    @Volatile private var skipOneStar = true
    private var exo: ExoPlayer? = null
    private var sonos: SonosPlayer? = null
    private var sonosReporter: TimelineReporter? = null
    private var sonosHandover: SonosHandover? = null
    private val sonosLocks by lazy { SonosLocks(this) }
    private val sonosLockListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sonosLocks.hold(playWhenReady)

        // The heart follows the speaker's track.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshButtons()
    }

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
        sonosHandover = SonosHandover(
            phone = player,
            scope = scope,
            client = sonosOutput.client,
            openAccess = repository::openSonosAccess,
            tracks = repository::sonosTracks,
            refuse = { items -> SonosRefusal.refuses(items, mystery.state.value) },
            togetherActive = { together.state.value.isActive },
            show = sonosOutput::set,
            tell = sonosOutput::tell,
            attach = ::attachSonos,
        )
        // A speaker token left by a Sonos session the app didn't get to end (killed): revoke it.
        scope.launch { runCatching { repository.revokeLeftoverSpeakerToken() } }
        if (BuildConfig.DEBUG) player.addAnalyticsListener(EventLogger("SillonPlayer"))
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = fallBackToOriginal(player, error)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyGain(player, mediaItem)
                saveQueue(player)
                refreshButtons()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startQueueSaver(player) else saveQueue(player)
            }

            // Revealing a surprise sleeve unmasks the track in place: the heart comes back.
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refreshButtons()
        })
        player.addListener(NeverPlayedRefill(player, scope, neverPlayed))
        val oneStar = { item: MediaItem -> rater.state.value.book.isOneStar(item.mediaId, MediaItems.ratingOf(item)) }
        skipper = OneStarSkipper(
            player = player,
            isOneStar = oneStar,
            enabled = { skipOneStar },
            grouped = { together.state.value.isActive },
            // At the end of a side the record waits to be flipped: nothing to skip.
            holding = { sides.state.value.ended != null || (player.pauseAtEndOfMediaItems && !player.playWhenReady) },
        ).also(player::addListener)
        sides.machine.skipped = { i -> skipOneStar && i < player.mediaItemCount && oneStar(player.getMediaItemAt(i)) }
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
            pick = { index, seek -> skipper?.picking(index, seek) ?: seek() },
            describe = { end ->
                getString(R.string.side_end_title, end.ended) to getString(R.string.side_flip_hint, end.next)
            },
            onEndedChanged = {
                refreshButtons()
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
        // A new rating can move the end of a side, and changes the heart in the notification.
        scope.launch {
            combine(rater.state, settings.skipOneStar) { ratings, skip -> ratings to skip }.collect { (_, skip) ->
                skipOneStar = skip
                sidePlayer.sync()
                refreshButtons()
            }
        }
        refreshButtons()
        scope.launch {
            sonosOutput.requests.collect { request ->
                when (request) {
                    is OutputRequest.PlayOn -> sonosHandover?.playOn(request.group)
                    OutputRequest.BackToPhone -> sonosHandover?.backToPhone(resume = true)
                }
            }
        }
        // Listen together plays on each phone: joining a group brings the music back here.
        scope.launch {
            together.state.map { it.isActive }.distinctUntilChanged().collect { active ->
                if (active && sonos != null) {
                    sonosHandover?.backToPhone(resume = false)
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

    /** The Sonos player behind the media session now, or null for the phone's: reports, locks, sides. */
    @OptIn(UnstableApi::class)
    private fun attachSonos(player: SonosPlayer?) {
        sonosReporter?.release()
        sonosReporter = null
        sonos?.removeListener(sonosLockListener)
        sonos = player
        if (player != null) {
            // Sides pause the phone's player between faces; the speaker plays the record through.
            sides.setPlan(null)
            player.addListener(sonosLockListener)
            sonosLocks.hold(player.playWhenReady)
            mediaSession?.player = player
            sonosReporter = TimelineReporter(player, scope, ::report).also { it.start() }
        } else {
            sonosLocks.hold(false)
            sidePlayer?.let { mediaSession?.player = it }
        }
        refreshButtons()
    }

    @OptIn(UnstableApi::class)
    private fun flipButton(): CommandButton = CommandButton.Builder(CommandButton.ICON_SYNC)
        .setDisplayName(getString(R.string.side_flip))
        .setCustomIconResId(R.drawable.ic_side_flip)
        .setSessionCommand(SessionCommand(SideSession.ACTION_FLIP, Bundle.EMPTY))
        .build()

    /**
     * The notification, lock screen and car: flip at the end of a side, else the heart and the
     * thumb down. None on a surprise sleeve not revealed yet: the heart would tell its rating.
     */
    @OptIn(UnstableApi::class)
    private fun refreshButtons() {
        val session = mediaSession ?: return
        val item = session.player.currentMediaItem
        val buttons = when {
            sides.state.value.ended != null -> listOf(flipButton())
            item == null || item.mediaId.isBlank() || MysteryMask.isMasked(item) -> emptyList()
            else -> {
                val loved = Stars.isFiveStars(rater.state.value.book.ratingOf(item.mediaId, MediaItems.ratingOf(item)))
                listOf(loveButton(loved), dislikeButton())
            }
        }
        session.setCustomLayout(buttons)
    }

    @OptIn(UnstableApi::class)
    private fun loveButton(loved: Boolean): CommandButton =
        CommandButton.Builder(if (loved) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (loved) R.string.rating_love_undo else R.string.rating_love))
            .setCustomIconResId(if (loved) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(SessionCommand(PlaybackConnection.ACTION_LOVE, Bundle.EMPTY))
            .build()

    @OptIn(UnstableApi::class)
    private fun dislikeButton(): CommandButton = CommandButton.Builder(CommandButton.ICON_THUMB_DOWN_UNFILLED)
        .setDisplayName(getString(R.string.rating_dislike))
        .setCustomIconResId(R.drawable.ic_thumb_down)
        .setSessionCommand(SessionCommand(PlaybackConnection.ACTION_DISLIKE, Bundle.EMPTY))
        .build()

    /**
     * Rates [key]; 1 star on the track playing moves on at once, except when listening together.
     * On Sonos the speaker holds the queue: it goes to its next track.
     */
    private fun rate(key: String, stars: Int?) {
        rater.rate(key, stars)
        val player = mediaSession?.player ?: return
        if (stars == 1 && key == player.currentMediaItem?.mediaId) {
            OneStarSkip.moveOn(speaker = sonos, skipCurrent = { skipper?.skipCurrent() })
        }
    }

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
        skipper = null
        // The speaker plays on by itself; Sillon only stops following it, and ends the speaker's own
        // token (bounded wait: the service is going away).
        sonosHandover?.let { handover -> runBlocking { withTimeoutOrNull(ACCESS_CLOSE_MS) { handover.release() } } }
        sonosHandover = null
        sonosLocks.hold(false)
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

    // The app itself (its own uid), the system, the notification and Android Auto.
    @OptIn(UnstableApi::class)
    private fun isTrustedRater(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        libraryTree.isCallerTrusted(controller) ||
            session.isMediaNotificationController(controller) ||
            session.isAutoCompanionController(controller) ||
            session.isAutomotiveController(controller)

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(RatingGate.commands(isTrustedRater(session, controller)))
                .build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val current = session.player.currentMediaItem
            val trusted = isTrustedRater(session, controller)
            when (customCommand.customAction) {
                SideSession.ACTION_FLIP -> sidePlayer?.flip()
                PlaybackConnection.ACTION_RATE -> {
                    val key = when (val verdict = RatingGate.check(trusted, args.getString(PlaybackConnection.RATE_KEY), current)) {
                        is RatingGate.Verdict.Rate -> verdict.key
                        is RatingGate.Verdict.Refuse -> return Futures.immediateFuture(SessionResult(verdict.error))
                    }
                    rate(key, args.getInt(PlaybackConnection.RATE_STARS).takeIf { it in 1..Stars.MAX })
                }
                PlaybackConnection.ACTION_LOVE, PlaybackConnection.ACTION_DISLIKE -> {
                    val key = when (val verdict = RatingGate.check(trusted, null, current)) {
                        is RatingGate.Verdict.Rate -> verdict.key
                        is RatingGate.Verdict.Refuse -> return Futures.immediateFuture(SessionResult(verdict.error))
                    }
                    val stars = if (customCommand.customAction == PlaybackConnection.ACTION_DISLIKE) {
                        1
                    } else {
                        val loved = Stars.isFiveStars(rater.state.value.book.ratingOf(key, MediaItems.ratingOf(current)))
                        if (loved) null else Stars.MAX
                    }
                    rate(key, stars)
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Controllers that rate with Media3's own ratings (stars, heart or thumbs): the same rule as
        // Sillon's own commands. Any app can call setRating, and pick the track playing first.
        @OptIn(UnstableApi::class)
        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating,
        ): ListenableFuture<SessionResult> {
            val key = when (val verdict = RatingGate.check(isTrustedRater(session, controller), null, session.player.currentMediaItem)) {
                is RatingGate.Verdict.Rate -> verdict.key
                is RatingGate.Verdict.Refuse -> return Futures.immediateFuture(SessionResult(verdict.error))
            }
            val stars = when (rating) {
                is StarRating -> if (rating.isRated) Math.round(rating.starRating * Stars.MAX / rating.maxStars).coerceIn(1, Stars.MAX) else null
                is HeartRating -> if (rating.isRated && rating.isHeart) Stars.MAX else null
                is ThumbRating -> if (!rating.isRated) null else if (rating.isThumbsUp) Stars.MAX else 1
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            rate(key, stars)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Off the main thread: for a legacy browser (Android Auto), Media3 waits for this root on the
        // main thread, so a root that needs the main thread never comes and the car spins forever.
        @OptIn(UnstableApi::class)
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
            when {
                !libraryTree.isCallerTrusted(browser) -> {
                    Log.w(TAG, "browse root denied to ${browser.packageName}")
                    LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                }
                params?.isRecent == true -> libraryTree.recentRoot()
                    .also { grantSleeve(browser) }
                    ?.let { LibraryResult.ofItem(it, libraryTree.rootParams()) }
                    ?: LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)
                else -> LibraryResult.ofItem(libraryTree.root(), libraryTree.rootParams())
                    .also { grantSleeve(browser) }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            if (!libraryTree.isCallerTrusted(browser)) {
                return@future LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
            }
            runCatching { libraryTree.item(mediaId) }.fold(
                onSuccess = { item ->
                    item?.let { grantArtwork(browser, listOf(it)) }
                    item?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                },
                onFailure = {
                    Log.w(TAG, "onGetItem failed", it)
                    LibraryResult.ofError(SessionError.ERROR_IO)
                },
            )
        }

        @OptIn(UnstableApi::class)
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
                    LibraryResult.ofError(SessionError.ERROR_IO)
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

        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            runCatching { libraryTree.resumption() }.getOrElse {
                Log.w(TAG, "onPlaybackResumption failed", it)
                MediaItemsWithStartPosition(emptyList(), 0, 0L)
            }
        }

        @OptIn(UnstableApi::class)
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

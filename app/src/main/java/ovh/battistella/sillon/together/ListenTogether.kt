package ovh.battistella.sillon.together

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ovh.battistella.sillon.BuildConfig
import ovh.battistella.sillon.data.jellyfin.SyncPlayApi
import ovh.battistella.sillon.data.jellyfin.SyncPlayEvent
import ovh.battistella.sillon.data.jellyfin.SyncPlayGroupInfo
import ovh.battistella.sillon.data.jellyfin.SyncPlayMessages
import ovh.battistella.sillon.data.jellyfin.SyncPlayQueueItem
import ovh.battistella.sillon.data.jellyfin.SyncQueue
import ovh.battistella.sillon.data.jellyfin.sameJellyfinId
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ListenTogether @Inject constructor(
    private val api: SyncPlayApi,
    private val sessionStore: SessionStore,
    private val repository: PlexRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<TogetherState>(TogetherState.Off)
    val state: StateFlow<TogetherState> = _state.asStateFlow()

    val available: Flow<Boolean> = sessionStore.session.map {
        (it as? Session.Ready)?.server?.endpoint?.kind == ServerKind.JELLYFIN
    }

    private val clock = ClockSync()
    private val localClock: () -> Long = SystemClock::elapsedRealtime
    private val players = MutableStateFlow<GroupPlayer?>(null)
    private val player: GroupPlayer? get() = players.value

    private val events = Channel<SessionEvent>(Channel.UNLIMITED)
    private val requests = Channel<GroupRequest>(Channel.UNLIMITED)

    private var server: ServerEndpoint? = null
    private var deviceId = ""
    private var socket: WebSocket? = null
    private var groupId: String? = null
    private var handOver = false

    private var connection: Job? = null
    private var clockJob: Job? = null
    private var keepAliveJob: Job? = null
    private var driftJob: Job? = null
    private var actionJob: Job? = null
    private var reconnect: Job? = null

    private var queue: SyncQueue? = null
    private var loaded: List<SyncPlayQueueItem> = emptyList()
    private var timeline: GroupTimeline? = null
    private var correcting = false
    private var catchingUp = false
    private var awaitingReady = false
    private var reportedBuffering = false
    private var settleUntil = 0L

    init {
        scope.launch { for (event in events) handle(event) }
        scope.launch { for (request in requests) send(request) }
    }

    fun attach(groupPlayer: GroupPlayer) {
        groupPlayer.setListener(playerListener)
        players.value = groupPlayer
    }

    fun detach(groupPlayer: GroupPlayer) {
        if (players.value !== groupPlayer) return
        if (_state.value.isActive) leave()
        groupPlayer.setListener(null)
        players.value = null
    }

    suspend fun groups(): List<SyncPlayGroupInfo> {
        val endpoint = jellyfin() ?: return emptyList()
        return api.groups(endpoint, sessionStore.clientId())
    }

    fun create(name: String) = start(name, null)

    fun join(group: SyncPlayGroupInfo) = start(group.groupName, group.groupId)

    fun leave() {
        events.trySend(SessionEvent.Leave)
    }

    fun dismiss() {
        events.trySend(SessionEvent.Dismiss)
    }

    fun intercept(intent: UserIntent): Boolean {
        val current = _state.value
        if (!current.isActive) return false
        if (current is TogetherState.InGroup) GroupRequests.forIntent(intent, queue)?.let { requests.trySend(it) }
        return true
    }

    private fun start(name: String?, joinId: String?) {
        if (_state.value.isActive) return
        events.trySend(SessionEvent.Connect(name))
        connection = scope.launch {
            val endpoint = jellyfin()
            val groupPlayer = withTimeoutOrNull(PLAYER_WAIT_MS) { players.filterNotNull().first() }
            if (endpoint == null || groupPlayer == null) {
                events.send(SessionEvent.Failed(EndReason.Failed))
                return@launch
            }
            server = endpoint
            deviceId = sessionStore.clientId()
            clock.reset()
            repeat(ClockSync.MIN_SAMPLES) { sampleClock() }
            if (!clock.isSynced) {
                events.send(SessionEvent.Failed(EndReason.ConnectionLost))
                return@launch
            }
            handOver = joinId == null
            val opened = openSocket()
            if (!opened) {
                events.send(SessionEvent.Failed(EndReason.ConnectionLost))
                return@launch
            }
            runCatching {
                if (joinId == null) api.create(endpoint, deviceId, name.orEmpty()) else api.join(endpoint, deviceId, joinId)
            }.onFailure { events.send(SessionEvent.Failed(reasonFor(it))) }
            startClock()
        }
    }

    private suspend fun handle(event: SessionEvent) {
        val before = _state.value
        val step = TogetherMachine.reduce(before, event)
        _state.value = step.state
        if (event is SessionEvent.Server && event.event is SyncPlayEvent.Joined) onJoined(event.event.group)
        step.effects.forEach { apply(it) }
        if (before.isActive && step.state is TogetherState.Ended) tearDown(step.state.reason)
    }

    private fun onJoined(group: SyncPlayGroupInfo) {
        val rejoined = groupId != null && sameJellyfinId(groupId, group.groupId)
        groupId = group.groupId
        if (rejoined) return
        val groupPlayer = player ?: return
        groupPlayer.enterGroupMode()
        driftLoop()
        val mine = groupPlayer.currentQueue()
        if (handOver && mine != null) {
            requests.trySend(GroupRequest.SetQueue(mine.first, mine.second, mine.third))
        } else {
            groupPlayer.pause()
        }
        handOver = false
    }

    private suspend fun apply(effect: Effect) {
        val groupPlayer = player ?: return
        when (effect) {
            is Effect.LoadQueue -> load(groupPlayer, effect.queue)
            is Effect.Run -> run(groupPlayer, effect)
            Effect.PauseLocal -> {
                actionJob?.cancel()
                timeline = null
                groupPlayer.pause()
                groupPlayer.setSpeed(1f)
            }
            Effect.ExitGroupMode -> groupPlayer.exitGroupMode()
        }
    }

    private suspend fun load(groupPlayer: GroupPlayer, next: SyncQueue) {
        actionJob?.cancel()
        val previous = queue
        queue = next
        timeline = null
        correcting = false
        catchingUp = false
        reportedBuffering = false
        groupPlayer.pause()
        groupPlayer.setSpeed(1f)
        val current = next.current ?: return
        val samePlaylist = previous != null && loaded.isNotEmpty() &&
            previous.items.map { it.playlistItemId } == next.items.map { it.playlistItemId }
        val localIndex = loaded.indexOfFirst { it.playlistItemId == current.playlistItemId }
        awaitingReady = true
        if (samePlaylist && localIndex >= 0) {
            groupPlayer.jumpTo(localIndex, next.startPositionMs)
        } else {
            val tracks = runCatching { repository.restoreQueue(next.items.map { it.itemId }) }.getOrDefault(emptyList())
            if (queue !== next) return
            val byId = tracks.associateBy { it.ratingKey.replace("-", "").lowercase() }
            val pairs = next.items.mapNotNull { item -> byId[item.itemId.replace("-", "").lowercase()]?.let { item to it } }
            loaded = pairs.map { it.first }
            val index = loaded.indexOfFirst { it.playlistItemId == current.playlistItemId }
            if (index < 0) {
                awaitingReady = false
                events.send(SessionEvent.Status(MyStatus.CantPlay))
                return
            }
            groupPlayer.load(pairs.map { it.second }, index, next.startPositionMs)
        }
        scope.launch {
            delay(READY_CHECK_MS)
            if (awaitingReady && queue === next && groupPlayer.isReady) reportReady(groupPlayer, isPlaying = false)
        }
    }

    private fun run(groupPlayer: GroupPlayer, effect: Effect.Run) {
        val command = effect.command
        val action = CommandPlanner.plan(
            command,
            queue?.current?.playlistItemId,
            clock.toServer(localClock()),
            groupPlayer.positionMs,
        )
        if (BuildConfig.DEBUG) Log.d(TAG, "command ${command.type} -> $action")
        if (action == PlayerAction.Ignore) return
        timeline = CommandPlanner.timelineAfter(command, groupPlayer.positionMs)
        actionJob?.cancel()
        correcting = false
        catchingUp = false
        groupPlayer.setSpeed(1f)
        when (action) {
            is PlayerAction.PlayAt -> {
                awaitingReady = false
                if (action.delayMs == 0L) {
                    catchUp(groupPlayer)
                } else {
                    if (abs(groupPlayer.positionMs - action.positionMs) > ALIGNED_MS) groupPlayer.seek(action.positionMs)
                    actionJob = scope.launch {
                        delay(action.delayMs)
                        groupPlayer.play()
                        settleUntil = localClock() + SETTLE_MS
                        events.send(SessionEvent.Status(MyStatus.Playing))
                    }
                }
            }
            is PlayerAction.PauseAt -> actionJob = scope.launch {
                delay(action.delayMs)
                groupPlayer.pause()
                groupPlayer.seek(action.positionMs)
                events.send(SessionEvent.Status(MyStatus.Paused))
                events.send(SessionEvent.Drift(null))
            }
            is PlayerAction.SeekThenReady -> {
                groupPlayer.pause()
                groupPlayer.seek(action.positionMs)
                awaitingReady = true
                events.trySend(SessionEvent.Status(MyStatus.Loading))
                scope.launch {
                    delay(READY_CHECK_MS)
                    if (awaitingReady && groupPlayer.isReady) reportReady(groupPlayer, isPlaying = false)
                }
            }
            PlayerAction.Stop -> {
                timeline = null
                groupPlayer.pause()
                events.trySend(SessionEvent.Status(MyStatus.Paused))
            }
            PlayerAction.Ignore -> Unit
        }
    }

    private fun catchUp(groupPlayer: GroupPlayer) {
        actionJob?.cancel()
        actionJob = scope.launch {
            catchingUp = true
            events.send(SessionEvent.Status(MyStatus.CatchingUp))
            var lead = CATCH_UP_LEAD_MS
            repeat(CATCH_UP_TRIES) {
                val line = timeline ?: return@launch
                groupPlayer.pause()
                groupPlayer.setSpeed(1f)
                val startServer = clock.toServer(localClock()) + lead
                groupPlayer.seek(line.positionAt(startServer))
                delay((clock.toLocal(startServer) - localClock()).coerceAtLeast(0))
                if (groupPlayer.isReady) {
                    groupPlayer.play()
                    settleUntil = localClock() + SETTLE_MS
                    catchingUp = false
                    events.send(SessionEvent.Status(MyStatus.Playing))
                    return@launch
                }
                lead *= 2
            }
            catchingUp = false
        }
    }

    private fun driftLoop() {
        driftJob?.cancel()
        driftJob = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(DRIFT_TICK_MS)
                val groupPlayer = player ?: continue
                val line = timeline
                if (line == null || !line.playing || !groupPlayer.isPlaying || catchingUp || localClock() < settleUntil) continue
                val expected = line.positionAt(clock.toServer(localClock()))
                val drift = DriftCorrector.drift(groupPlayer.positionMs, expected)
                when (val correction = DriftCorrector.correct(drift, expected, correcting)) {
                    Correction.None -> if (correcting) {
                        groupPlayer.setSpeed(1f)
                        correcting = false
                    }
                    is Correction.Speed -> {
                        groupPlayer.setSpeed(correction.rate)
                        correcting = true
                    }
                    is Correction.Seek -> catchUp(groupPlayer)
                }
                if (++ticks % DRIFT_REPORT_EVERY == 0) {
                    events.send(SessionEvent.Drift(drift))
                    events.send(SessionEvent.Status(if (correcting) MyStatus.CatchingUp else MyStatus.Playing))
                    Log.i(TAG, "drift=${drift}ms rtt=${clock.roundTripMs}ms offset=${clock.offsetMs}ms speed=${if (correcting) "adjusting" else "1.0"}")
                }
            }
        }
    }

    private val playerListener = object : GroupPlayer.Listener {
        override fun onReadyChanged(ready: Boolean) {
            val groupPlayer = player ?: return
            if (!_state.value.isActive) return
            if (ready) {
                when {
                    awaitingReady -> scope.launch { reportReady(groupPlayer, isPlaying = false) }
                    reportedBuffering -> {
                        reportedBuffering = false
                        scope.launch { reportReady(groupPlayer, isPlaying = true) }
                    }
                }
            } else if (timeline?.playing == true && !catchingUp && !awaitingReady && groupPlayer.isBuffering) {
                reportedBuffering = true
                scope.launch { reportReadiness(groupPlayer, ready = false, isPlaying = true) }
                events.trySend(SessionEvent.Status(MyStatus.Loading))
            }
        }

        override fun onTrackEnded() {
            val current = queue?.current?.playlistItemId ?: return
            if (!_state.value.isActive) return
            timeline = null
            requests.trySend(GroupRequest.NextItem(current))
        }
    }

    private suspend fun reportReady(groupPlayer: GroupPlayer, isPlaying: Boolean) {
        awaitingReady = false
        reportReadiness(groupPlayer, ready = true, isPlaying = isPlaying)
        events.send(SessionEvent.Status(MyStatus.Ready))
    }

    private suspend fun reportReadiness(groupPlayer: GroupPlayer, ready: Boolean, isPlaying: Boolean) {
        val endpoint = server ?: return
        val current = queue?.current?.playlistItemId ?: return
        runCatching {
            api.reportReadiness(
                endpoint, deviceId, ready,
                serverNowMs = clock.toServer(localClock()),
                positionMs = groupPlayer.positionMs,
                isPlaying = isPlaying,
                playlistItemId = current,
            )
        }.onFailure { Log.w(TAG, "ready=$ready report failed", it) }
    }

    private suspend fun send(request: GroupRequest) {
        val endpoint = server ?: return
        if (_state.value !is TogetherState.InGroup) return
        runCatching {
            when (request) {
                GroupRequest.Unpause -> api.unpause(endpoint, deviceId)
                GroupRequest.Pause -> api.pause(endpoint, deviceId)
                is GroupRequest.Seek -> api.seek(endpoint, deviceId, request.positionMs)
                is GroupRequest.NextItem -> api.nextItem(endpoint, deviceId, request.playlistItemId)
                is GroupRequest.PreviousItem -> api.previousItem(endpoint, deviceId, request.playlistItemId)
                is GroupRequest.SetItem -> api.setPlaylistItem(endpoint, deviceId, request.playlistItemId)
                is GroupRequest.SetQueue -> api.setNewQueue(endpoint, deviceId, request.itemIds, request.index, request.positionMs)
                is GroupRequest.Queue -> api.queue(endpoint, deviceId, request.itemIds)
                is GroupRequest.Shuffle -> api.setShuffle(endpoint, deviceId, request.enabled)
                is GroupRequest.Repeat -> api.setRepeat(endpoint, deviceId, request.mode)
            }
        }.onFailure { Log.w(TAG, "request $request failed", it) }
    }

    private suspend fun openSocket(): Boolean {
        val endpoint = server ?: return false
        val opened = CompletableDeferred<Boolean>()
        socket?.cancel()
        socket = api.openSocket(endpoint, deviceId, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== socket) return
                when (val event = SyncPlayMessages.parse(text)) {
                    null -> Unit
                    is SyncPlayEvent.KeepAliveRequested -> scope.launch { keepAlive(webSocket, event.timeoutSeconds) }
                    else -> events.trySend(SessionEvent.Server(event))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.complete(false)
                lost(webSocket)
            }
        })
        val ok = withTimeoutOrNull(SOCKET_OPEN_TIMEOUT_MS) { opened.await() } == true
        if (ok) socket?.let { keepAlive(it, SyncPlayMessages.DEFAULT_KEEP_ALIVE_S) }
        return ok
    }

    private fun keepAlive(webSocket: WebSocket, timeoutSeconds: Int) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && webSocket === socket) {
                webSocket.send(SyncPlayMessages.KEEP_ALIVE)
                delay(timeoutSeconds.coerceAtLeast(2) * 1_000L / 2)
            }
        }
    }

    private fun lost(webSocket: WebSocket) {
        if (webSocket !== socket) return
        socket = null
        if (reconnect?.isActive == true) return
        reconnect = scope.launch {
            val current = _state.value
            if (current !is TogetherState.InGroup) return@launch
            events.send(SessionEvent.ConnectionLost)
            val id = groupId ?: return@launch events.send(SessionEvent.GaveUp)
            val back = withTimeoutOrNull(RECONNECT_WINDOW_MS) {
                while (isActive) {
                    if (openSocket() && runCatching { api.join(server!!, deviceId, id) }.isSuccess) return@withTimeoutOrNull true
                    delay(RECONNECT_INTERVAL_MS)
                }
                false
            } == true
            events.send(if (back) SessionEvent.Reconnected else SessionEvent.GaveUp)
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = scope.launch {
            while (isActive) {
                delay(if (clock.isSynced) ClockSync.SLOW_INTERVAL_MS else ClockSync.FAST_INTERVAL_MS)
                sampleClock()
                val endpoint = server
                if (endpoint != null && _state.value is TogetherState.InGroup) {
                    runCatching { api.ping(endpoint, deviceId, clock.roundTripMs) }
                }
            }
        }
    }

    private suspend fun sampleClock() {
        val endpoint = server ?: return
        runCatching { api.clockSample(endpoint, deviceId, localClock) }
            .getOrNull()
            ?.let(clock::add)
    }

    private fun tearDown(reason: EndReason) {
        val endpoint = server
        val closing = socket
        socket = null
        connection?.cancel()
        listOf(clockJob, keepAliveJob, driftJob, actionJob, reconnect).forEach { it?.cancel() }
        timeline = null
        queue = null
        loaded = emptyList()
        groupId = null
        correcting = false
        catchingUp = false
        awaitingReady = false
        reportedBuffering = false
        scope.launch {
            if (endpoint != null && reason == EndReason.Left) runCatching { api.leave(endpoint, deviceId) }
            closing?.close(NORMAL_CLOSURE, null)
        }
    }

    private fun reasonFor(error: Throwable): EndReason = when ((error as? PlexHttpException)?.code) {
        401, 403 -> EndReason.AccessDenied
        404 -> EndReason.GroupGone
        else -> EndReason.ConnectionLost
    }

    private suspend fun jellyfin(): ServerEndpoint? =
        (sessionStore.session.first() as? Session.Ready)?.server?.endpoint?.takeIf { it.kind == ServerKind.JELLYFIN }

    private companion object {
        const val TAG = "SillonTogether"
        const val PLAYER_WAIT_MS = 5_000L
        const val SOCKET_OPEN_TIMEOUT_MS = 10_000L
        const val RECONNECT_WINDOW_MS = 20_000L
        const val RECONNECT_INTERVAL_MS = 2_000L
        const val READY_CHECK_MS = 300L
        const val ALIGNED_MS = 50L
        const val SETTLE_MS = 1_000L
        const val CATCH_UP_LEAD_MS = 800L
        const val CATCH_UP_TRIES = 3
        const val DRIFT_TICK_MS = 250L
        const val DRIFT_REPORT_EVERY = 4
        const val NORMAL_CLOSURE = 1000
    }
}

package ovh.battistella.sillon.desktop.discord

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import ovh.battistella.sillon.desktop.player.EngineState
import java.io.Closeable
import kotlin.math.abs

enum class DiscordStatus { OFF, WAITING, LIVE }

fun interface PresenceConnection : Closeable {
    fun show(activity: JsonObject?)

    override fun close() {}
}

class DiscordRichPresence(
    private val playback: StateFlow<EngineState>,
    private val enabled: Flow<Boolean>,
    private val covers: Flow<Boolean>,
    private val lookup: suspend (artist: String?, album: String?) -> String?,
    private val labels: PresenceLabels,
    private val connect: () -> PresenceConnection,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: Long = RETRY_MS,
    private val minGapMs: Long = MIN_GAP_MS,
    private val pauseLimitMs: Long = PAUSE_LIMIT_MS,
) {
    private data class Inputs(val enabled: Boolean, val covers: Boolean, val state: EngineState, val tick: Long)

    private val _status = MutableStateFlow(DiscordStatus.OFF)
    val status: StateFlow<DiscordStatus> = _status.asStateFlow()

    private var connection: PresenceConnection? = null
    private var lastAttemptMs: Long? = null
    private var shown: Listening? = null
    private var shownCovers = false
    private var hasShown = false
    private var lastPlayedKey: String? = null
    private var pausedSinceMs: Long? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        job?.cancel()
        job = scope.launch(dispatcher) {
            try {
                combine(enabled, covers, playback, ticks()) { on, art, state, tick -> Inputs(on, art, state, tick) }
                    .conflate()
                    .collect {
                        step(it)
                        delay(minGapMs)
                    }
            } finally {
                disconnect()
                _status.value = DiscordStatus.OFF
            }
        }
    }

    fun close() {
        job?.cancel()
        job = null
    }

    private fun ticks(): Flow<Long> = flow {
        var tick = 0L
        while (true) {
            emit(tick++)
            delay(retryMs)
        }
    }

    private suspend fun step(inputs: Inputs) {
        if (!inputs.enabled) {
            disconnect()
            lastAttemptMs = null
            _status.value = DiscordStatus.OFF
            return
        }
        val target = target(inputs.state)
        val conn = connection ?: tryConnect() ?: return
        if (hasShown && shownCovers == inputs.covers && same(shown, target)) return
        val cover = if (inputs.covers && target != null) runCatching { lookup(target.artist, target.album) }.getOrNull() else null
        val activity = target?.let { Presence.activity(it.copy(coverUrl = cover), labels) }
        try {
            conn.show(activity)
            shown = target
            shownCovers = inputs.covers
            hasShown = true
            _status.value = DiscordStatus.LIVE
        } catch (e: Exception) {
            disconnect()
            lastAttemptMs = clock()
            _status.value = DiscordStatus.WAITING
        }
    }

    private fun target(state: EngineState): Listening? {
        val track = state.current
        val now = clock()
        if (state.playing) {
            lastPlayedKey = track?.ratingKey
            pausedSinceMs = null
        } else if (pausedSinceMs == null) {
            pausedSinceMs = now
        }
        if (track == null) return null
        val paused = pausedSinceMs
        val visible = state.playing || (track.ratingKey == lastPlayedKey && paused != null && now - paused < pauseLimitMs)
        if (!visible) return null
        val duration = state.durationMs.takeIf { it > 0 } ?: track.durationMs ?: 0
        return Listening(
            key = track.ratingKey,
            title = track.title,
            artist = track.artist,
            album = track.album,
            playing = state.playing,
            startedAtMs = if (state.playing) now - state.positionMs else null,
            durationMs = duration,
            coverUrl = null,
        )
    }

    private fun tryConnect(): PresenceConnection? {
        val last = lastAttemptMs
        if (last != null && clock() - last < retryMs) return null
        lastAttemptMs = clock()
        return try {
            connect().also {
                connection = it
                hasShown = false
                _status.value = DiscordStatus.LIVE
            }
        } catch (e: Exception) {
            _status.value = DiscordStatus.WAITING
            null
        }
    }

    private fun disconnect() {
        connection?.let { runCatching { it.close() } }
        connection = null
        shown = null
        hasShown = false
    }

    private fun same(a: Listening?, b: Listening?): Boolean {
        if (a == null || b == null) return a == b
        if (a.key != b.key || a.playing != b.playing || a.durationMs != b.durationMs) return false
        val sa = a.startedAtMs
        val sb = b.startedAtMs
        if (sa == null || sb == null) return sa == sb
        return abs(sa - sb) <= DRIFT_MS
    }

    companion object {
        const val CLIENT_ID = "1463211692656689172"
        const val RETRY_MS = 15_000L
        const val MIN_GAP_MS = 1_000L
        const val PAUSE_LIMIT_MS = 10 * 60_000L
        private const val DRIFT_MS = 2_000L

        fun connectToDiscord(): PresenceConnection {
            val client = DiscordClient(DiscordSockets.open())
            try {
                client.handshake(CLIENT_ID)
            } catch (e: Exception) {
                client.close()
                throw e
            }
            return object : PresenceConnection {
                override fun show(activity: JsonObject?) = client.setActivity(activity)

                override fun close() = client.close()
            }
        }
    }
}

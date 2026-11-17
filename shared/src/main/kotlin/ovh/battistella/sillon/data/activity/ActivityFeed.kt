package ovh.battistella.sillon.data.activity

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository

@Singleton
class ActivityFeed @Inject constructor(
    private val repository: PlexRepository,
) {
    suspend fun load(): ActivityState = guarded {
        if (repository.activityAllowed()) ActivityState.Live(repository.activeListens()) else ActivityState.NotAllowed
    }

    private suspend fun refresh(): ActivityState = guarded { ActivityState.Live(repository.activeListens()) }

    private suspend fun guarded(block: suspend () -> ActivityState): ActivityState = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PlexHttpException) {
        if (e.code == 401 || e.code == 403) ActivityState.NotAllowed else ActivityState.Unreachable
    } catch (e: Exception) {
        ActivityState.Unreachable
    }

    fun watch(
        pollMs: Long = POLL_MS,
        settleMs: Long = SETTLE_MS,
        restMs: Long = REST_MS,
        retryMs: Long = SOCKET_RETRY_MS,
    ): Flow<ActivityState> = channelFlow {
        val nudges = Channel<Unit>(Channel.CONFLATED)
        val socket = launch {
            while (isActive) {
                runCatching { repository.activityEvents().collect { nudges.trySend(Unit) } }
                    .onFailure { if (it is CancellationException) throw it }
                delay(retryMs)
            }
        }
        send(ActivityState.Loading)
        var allowed = false
        while (isActive) {
            val state = if (allowed) refresh() else load()
            allowed = allowed || state is ActivityState.Live
            send(state)
            if (state == ActivityState.NotAllowed) break
            delay(restMs)
            if (withTimeoutOrNull(pollMs) { nudges.receive() } != null) delay(settleMs)
        }
        socket.cancel()
    }.distinctUntilChanged()

    companion object {
        const val POLL_MS = 12_000L
        const val SETTLE_MS = 1_500L
        const val REST_MS = 3_500L
        const val SOCKET_RETRY_MS = 30_000L
    }
}

package ovh.battistella.sillon.data.rating

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.FiveStarsCache
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How a rating send ended, for [RatingBook]. */
enum class SendOutcome { SENT, OFFLINE, REFUSED }

fun sendOutcomeOf(error: Throwable?): SendOutcome = when (error) {
    null -> SendOutcome.SENT
    is PlexHttpException -> if (error.code >= SERVER_ERROR) SendOutcome.OFFLINE else SendOutcome.REFUSED
    is IOException -> SendOutcome.OFFLINE
    else -> SendOutcome.REFUSED
}

private const val SERVER_ERROR = 500
private const val FIRST_RETRY_MS = 30_000L
private const val LAST_RETRY_MS = 15 * 60_000L

/**
 * Rates tracks: the rating shows at once, then leaves for the server. Without a network it
 * waits on the phone and leaves when the phone is back online. A server out of reach while the
 * phone is online (a LAN-only server away from home) is tried again later, less and less often.
 */
@Singleton
class Rater @Inject constructor(
    private val repository: PlexRepository,
    private val store: RatingStore,
    private val fiveStars: FiveStarsCache,
    network: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sending = Mutex()
    private var retry: Job? = null
    private var retryDelayMs: Long? = null

    /** The first wait before trying a server out of reach again; tests shorten it. */
    internal var firstRetryMs = FIRST_RETRY_MS

    val state: StateFlow<ServerRatings> = store.state

    init {
        // Loaded at once: after a restart offline, the player skips the 1-star downloads from the first track.
        scope.launch { runCatching { store.book(repository.currentServerId()) } }
        scope.launch { network.online.filter { it }.collect { runCatching { flush() } } }
    }

    /** Gives [stars] (null clears) to [key]. On Jellyfin, only 1 and 5 stars are taken. */
    fun rate(key: String, stars: Int?) {
        scope.launch {
            runCatching {
                if (repository.isJellyfin.first() && !JellyfinStars.supports(stars)) return@launch
                val server = repository.currentServerId()
                store.update(server) { it.rate(key, stars) }
                fiveStars.mark(server, key, stars == Stars.MAX)
                flush()
            }
        }
    }

    /** Sends what's waiting, oldest first; stops at the first network failure. */
    suspend fun flush() = sending.withLock {
        val server = runCatching { repository.currentServerId() }.getOrNull() ?: return@withLock
        for ((key, rating) in store.book(server).pending) {
            val error = try {
                repository.sendRating(key, rating)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            when (sendOutcomeOf(error)) {
                SendOutcome.SENT -> {
                    store.update(server) { it.sent(key, rating) }
                    synchronized(this) { retryDelayMs = null }
                }
                SendOutcome.REFUSED -> store.update(server) { it.refused(key) }
                SendOutcome.OFFLINE -> {
                    store.update(server) { book -> book.pending.keys.fold(book) { b, k -> b.offline(k) } }
                    retryLater()
                    return@withLock
                }
            }
        }
    }

    // One try at a time, waiting twice as long after each miss, up to LAST_RETRY_MS.
    private fun retryLater() {
        synchronized(this) {
            if (retry?.isActive == true) return
            val wait = retryDelayMs ?: firstRetryMs
            retryDelayMs = (wait * 2).coerceAtMost(LAST_RETRY_MS)
            retry = scope.launch {
                delay(wait)
                synchronized(this@Rater) { retry = null }
                runCatching { flush() }
            }
        }
    }
}

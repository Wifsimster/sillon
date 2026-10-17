package ovh.battistella.sillon.data.plex

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Picks the best way to reach a server among the addresses plex.tv lists.
 *
 * Order of preference: LAN over the internet, and the Plex relay only as a last
 * resort (it is bandwidth-capped). Only HTTPS is tried: Plex hands out
 * `*.plex.direct` certificates for every address, and the token must not travel
 * in clear text.
 */
object ConnectionSelector {

    /** Candidate connections, best first. */
    fun rank(connections: List<PlexConnection>): List<PlexConnection> =
        connections
            .filter { it.uri.startsWith("https://") }
            .distinctBy { it.uri }
            .sortedBy {
                when {
                    it.relay -> 2
                    it.local -> 0
                    else -> 1
                }
            }

    /**
     * Probe every candidate at once and return the best one that answered, so a
     * dead LAN address costs one timeout in total rather than one per address.
     */
    suspend fun select(
        connections: List<PlexConnection>,
        probe: suspend (PlexConnection) -> Boolean,
    ): PlexConnection? = coroutineScope {
        val ranked = rank(connections)
        val results = ranked.map { connection -> async { connection to probe(connection) } }
        // Await in rank order: the first success is the best reachable one. The
        // remaining probes are cancelled when the scope returns.
        var chosen: PlexConnection? = null
        for (result in results) {
            val (connection, ok) = result.await()
            if (ok) {
                chosen = connection
                break
            }
        }
        results.forEach { it.cancel() }
        chosen
    }
}

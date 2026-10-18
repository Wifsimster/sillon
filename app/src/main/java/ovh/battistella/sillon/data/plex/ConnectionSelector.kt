package ovh.battistella.sillon.data.plex

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object ConnectionSelector {
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

    suspend fun select(
        connections: List<PlexConnection>,
        probe: suspend (PlexConnection) -> Boolean,
    ): PlexConnection? = coroutineScope {
        val ranked = rank(connections)
        val results = ranked.map { connection -> async { connection to probe(connection) } }
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

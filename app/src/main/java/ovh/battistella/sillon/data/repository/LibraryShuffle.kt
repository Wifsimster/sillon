package ovh.battistella.sillon.data.repository

import ovh.battistella.sillon.data.local.DownloadEntity

/** "Shuffle": the whole library at random, from the server, or the downloads when offline. */
object LibraryShuffle {
    const val BATCH = 100

    fun plexTracks(size: Int = BATCH): List<Pair<String, String>> = listOf(
        "type" to "10",
        "sort" to "random",
        "X-Plex-Container-Start" to "0",
        "X-Plex-Container-Size" to size.toString(),
    )

    /** The downloaded tracks that can play without a connection, in a new order. */
    fun offline(rows: List<DownloadEntity>, random: kotlin.random.Random = kotlin.random.Random): List<DownloadEntity> =
        rows.filter { it.isPlayableOffline() }.shuffled(random)
}

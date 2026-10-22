package ovh.battistella.sillon.ui.screens.listen

import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.repository.isPlayableOffline

/** The one-tap tiles of the Listen page. */
enum class ListenTile { SHUFFLE, FIVE_STARS, NEVER_PLAYED, NEVER_PLAYED_ALBUMS, MYSTERY }

/** Whether a tile can play, and what it plays from. */
enum class TileState {
    /** Plays from the server (or, for the tiles that know how, falls back to the downloads by itself). */
    READY,

    /** No connection: plays the downloaded music instead. */
    FROM_DOWNLOADS,

    /** No connection, and none of the downloads fits the tile: it needs the server. */
    NEEDS_SERVER,

    /** No connection and nothing downloaded: nothing to play. */
    NOTHING_DOWNLOADED,
}

/** What the phone holds without a connection: the downloaded tracks, and how many of them have 5 stars. */
data class OfflineMusic(val tracks: Int = 0, val fiveStars: Int = 0) {
    companion object {
        /** Reads the disk: call it off the main thread. */
        fun of(rows: List<DownloadEntity>, fiveStarKeys: Set<String>): OfflineMusic {
            val playable = rows.filter { it.isPlayableOffline() }
            return OfflineMusic(playable.size, playable.count { it.ratingKey in fiveStarKeys })
        }
    }
}

object ListenTiles {
    /**
     * Online, every tile plays from the server. Offline with nothing downloaded, none can play.
     * Otherwise Shuffle plays the downloads, 5 stars the downloaded tracks last seen with 5 stars
     * (downloads carry no rating), and Never played and the mystery sleeve fall back to the
     * downloads on their own.
     */
    fun state(tile: ListenTile, online: Boolean, offline: OfflineMusic): TileState = when {
        online -> TileState.READY
        offline.tracks == 0 -> TileState.NOTHING_DOWNLOADED
        tile == ListenTile.SHUFFLE -> TileState.FROM_DOWNLOADS
        tile == ListenTile.FIVE_STARS -> if (offline.fiveStars > 0) TileState.FROM_DOWNLOADS else TileState.NEEDS_SERVER
        else -> TileState.READY
    }

    fun enabled(state: TileState): Boolean = state == TileState.READY || state == TileState.FROM_DOWNLOADS
}

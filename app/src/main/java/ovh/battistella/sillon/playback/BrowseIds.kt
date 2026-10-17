package ovh.battistella.sillon.playback

/**
 * Media ids of the Android Auto browse tree. Folders and tracks carry their
 * Plex keys; a track also carries the album or playlist it was listed in, so
 * tapping it in the car can queue the whole album from that track.
 *
 * Items in the player keep the bare Plex `ratingKey` as their id (what the app
 * and the timeline reports use); `track/…` ids only exist while browsing.
 */
sealed interface BrowseNode {
    data object Root : BrowseNode
    data object Recent : BrowseNode
    data object Artists : BrowseNode
    data object Albums : BrowseNode
    data object Playlists : BrowseNode
    data class Artist(val key: String) : BrowseNode
    data class Album(val key: String) : BrowseNode
    data class Playlist(val key: String) : BrowseNode

    /** A track listed in [parent] (an [Album] or a [Playlist]). */
    data class Track(val parent: BrowseNode, val key: String) : BrowseNode
}

object BrowseIds {
    const val ROOT = "root"
    const val RECENT = "recent"
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val PLAYLISTS = "playlists"

    fun of(node: BrowseNode): String = when (node) {
        BrowseNode.Root -> ROOT
        BrowseNode.Recent -> RECENT
        BrowseNode.Artists -> ARTISTS
        BrowseNode.Albums -> ALBUMS
        BrowseNode.Playlists -> PLAYLISTS
        is BrowseNode.Artist -> "artist/${node.key}"
        is BrowseNode.Album -> "album/${node.key}"
        is BrowseNode.Playlist -> "playlist/${node.key}"
        is BrowseNode.Track -> "track/${of(node.parent)}/${node.key}"
    }

    /** The node behind [id], or null for anything else (such as a bare ratingKey). */
    fun parse(id: String): BrowseNode? {
        when (id) {
            ROOT -> return BrowseNode.Root
            RECENT -> return BrowseNode.Recent
            ARTISTS -> return BrowseNode.Artists
            ALBUMS -> return BrowseNode.Albums
            PLAYLISTS -> return BrowseNode.Playlists
        }
        val parts = id.split('/')
        return when {
            parts.size == 2 && parts[1].isNotEmpty() -> when (parts[0]) {
                "artist" -> BrowseNode.Artist(parts[1])
                "album" -> BrowseNode.Album(parts[1])
                "playlist" -> BrowseNode.Playlist(parts[1])
                else -> null
            }
            parts.size == 4 && parts[0] == "track" && parts[3].isNotEmpty() -> {
                val parent = parse("${parts[1]}/${parts[2]}")
                if (parent is BrowseNode.Album || parent is BrowseNode.Playlist) {
                    BrowseNode.Track(parent, parts[3])
                } else {
                    null
                }
            }
            else -> null
        }
    }
}

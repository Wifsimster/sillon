package ovh.battistella.sillon.playback

import java.util.Base64

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
    /** The saved queue, as the car's "resume" card. */
    data object Resume : BrowseNode
    data object Artists : BrowseNode
    data object Albums : BrowseNode
    data object Playlists : BrowseNode
    /** Every track the user rated 5 stars. */
    data object FiveStars : BrowseNode
    /** The library's radios. */
    data object Radios : BrowseNode
    /** One radio, played by tapping it; [key] is the station key. */
    data class Station(val key: String) : BrowseNode
    data class Artist(val key: String) : BrowseNode
    data class Album(val key: String) : BrowseNode
    data class Playlist(val key: String) : BrowseNode

    /** A track listed in [parent] (an [Album], a [Playlist] or [FiveStars]). */
    data class Track(val parent: BrowseNode, val key: String) : BrowseNode
}

object BrowseIds {

    const val ROOT = "root"
    const val RECENT = "recent"
    const val RESUME = "resume"
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val PLAYLISTS = "playlists"
    const val FIVE_STARS = "five-stars"
    const val RADIOS = "radios"

    fun of(node: BrowseNode): String = when (node) {
        BrowseNode.Root -> ROOT
        BrowseNode.Recent -> RECENT
        BrowseNode.Resume -> RESUME
        BrowseNode.Artists -> ARTISTS
        BrowseNode.Albums -> ALBUMS
        BrowseNode.Playlists -> PLAYLISTS
        BrowseNode.FiveStars -> FIVE_STARS
        BrowseNode.Radios -> RADIOS
        // Station keys are server paths (slashes, queries): encoded to stay one segment.
        is BrowseNode.Station -> "station/" + Base64.getUrlEncoder().withoutPadding().encodeToString(node.key.toByteArray())
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
            RESUME -> return BrowseNode.Resume
            ARTISTS -> return BrowseNode.Artists
            ALBUMS -> return BrowseNode.Albums
            PLAYLISTS -> return BrowseNode.Playlists
            FIVE_STARS -> return BrowseNode.FiveStars
            RADIOS -> return BrowseNode.Radios
        }
        if (id.startsWith("station/")) {
            val key = runCatching { String(Base64.getUrlDecoder().decode(id.removePrefix("station/"))) }.getOrNull()
            return key?.takeIf { it.isNotBlank() }?.let { BrowseNode.Station(it) }
        }
        val parts = id.split('/')
        return when {
            parts.size == 2 && parts[1].isNotEmpty() -> when (parts[0]) {
                "artist" -> BrowseNode.Artist(parts[1])
                "album" -> BrowseNode.Album(parts[1])
                "playlist" -> BrowseNode.Playlist(parts[1])
                else -> null
            }
            parts.size == 3 && parts[0] == "track" && parts[1] == FIVE_STARS && parts[2].isNotEmpty() ->
                BrowseNode.Track(BrowseNode.FiveStars, parts[2])
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

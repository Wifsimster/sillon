package ovh.battistella.sillon.playback.cache

import java.net.URLEncoder

/**
 * Cache keys for what the player streams. A key names the server, the track and the quality,
 * never the URL: a Plex or Jellyfin URL carries the access token, and both the token and the
 * address (local or relay) change while the track stays the same.
 */
object ListeningCacheKeys {
    /** Only the server's original file is kept: see [StreamSource]. */
    const val ORIGINAL = "original"

    private const val PREFIX = "sillon-track/v1/"

    fun track(serverId: String, trackKey: String, quality: String = ORIGINAL): String =
        PREFIX + listOf(serverId, trackKey, quality).joinToString("/") { URLEncoder.encode(it, "UTF-8") }

    fun isTrack(key: String?): Boolean = key != null && key.startsWith(PREFIX)
}

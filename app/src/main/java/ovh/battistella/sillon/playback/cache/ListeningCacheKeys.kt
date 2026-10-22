package ovh.battistella.sillon.playback.cache

import java.net.URLEncoder

/**
 * Cache keys for what the player streams. A key names the server, the track and the quality,
 * never the URL: a Plex or Jellyfin URL carries the access token, and both the token and the
 * address (local or relay) change while the track stays the same.
 *
 * - original file: `sillon-track/v1/<server>/<track>/original`
 * - transcode: `sillon-track/v1/<server>/<track>/aac-<kbps>/<playlist or segment>`
 */
object ListeningCacheKeys {
    const val ORIGINAL = "original"
    private const val TRANSCODE = "aac-"

    private const val PREFIX = "sillon-track/v1/"

    fun track(serverId: String, trackKey: String, quality: String = ORIGINAL): String =
        PREFIX + listOf(serverId, trackKey, quality).joinToString("/") { encode(it) }

    /** The root of a transcode at [kbps]; its playlists and segments sit under it, see [part]. */
    fun transcode(serverId: String, trackKey: String, kbps: Int): String = track(serverId, trackKey, "$TRANSCODE$kbps")

    fun part(transcode: String, file: String): String = transcode + "/" + file.split('/').joinToString("/") { encode(it) }

    fun isTrack(key: String?): Boolean = key != null && key.startsWith(PREFIX)

    fun isOriginal(key: String): Boolean = isTrack(key) && key.endsWith("/$ORIGINAL")

    /** What every transcode key of the track behind [originalKey] starts with. */
    fun transcodesOf(originalKey: String): String = originalKey.removeSuffix(ORIGINAL) + TRANSCODE

    private fun encode(part: String) = URLEncoder.encode(part, "UTF-8")
}

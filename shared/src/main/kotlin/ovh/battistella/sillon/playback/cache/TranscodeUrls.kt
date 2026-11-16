package ovh.battistella.sillon.playback.cache

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads a server transcode (HLS) request: which play session it belongs to, and which file of
 * that transcode it asks for. Plex puts the session in `session=` and in the playlist path
 * (`…/transcode/universal/session/<id>/base/00001.ts`); Jellyfin in `playSessionId=` on every
 * playlist and segment (`…/Audio/<id>/hls1/main/0.ts`).
 */
object TranscodeUrls {
    private val SESSION_PARAMS = listOf("session", "X-Plex-Session-Identifier", "playSessionId", "PlaySessionId")

    /** The play session of [url], among those [known] to the cache. */
    fun sessionIn(url: String, known: (String) -> Boolean): String? {
        val http = url.toHttpUrlOrNull() ?: return null
        SESSION_PARAMS.forEach { param -> http.queryParameter(param)?.takeIf(known)?.let { return it } }
        return http.pathSegments.firstOrNull { it.isNotEmpty() && known(it) }
    }

    /**
     * The file [url] names within its transcode, the same from one play session to the next:
     * no host, no query (where the token travels), no session id.
     */
    fun partOf(url: String, session: String): String? {
        val segments = (url.toHttpUrlOrNull() ?: return null).pathSegments.filter { it.isNotEmpty() && it != session }
        val plex = segments.indexOf("universal")
        val jellyfin = segments.indexOfFirst { it.equals("audio", ignoreCase = true) }
        val start = when {
            plex >= 0 -> plex + 1
            jellyfin >= 0 -> jellyfin + 2
            else -> segments.size - 1
        }
        return segments.drop(start).takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    /** The path of [url], kept to resolve the cached playlists' relative links later; never a host or a token. */
    fun pathOf(url: String): String? = url.toHttpUrlOrNull()?.encodedPath
}

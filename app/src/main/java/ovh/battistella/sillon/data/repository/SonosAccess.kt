package ovh.battistella.sillon.data.repository

import ovh.battistella.sillon.data.plex.ServerEndpoint

/**
 * How one Sonos session reaches the server: the address and the token put in the speaker's URLs.
 * Never the account's own token. [close] it when the session ends.
 */
class SonosAccess(
    val endpoint: ServerEndpoint,
    /** The device the server sees fetching the music, when it isn't the phone (Jellyfin). */
    val deviceId: String? = null,
    private val revoke: suspend () -> Unit = {},
) {
    suspend fun close() {
        runCatching { revoke() }
    }
}

/** The server gives no token fit for a speaker, so nothing is sent to it. */
class SonosAccessException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Plex refused a transient token. */
        PLEX_TOKEN,

        /** Jellyfin gave no speaker token: most often, Quick Connect is off on the server. */
        JELLYFIN_TOKEN,
    }
}

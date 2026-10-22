package ovh.battistella.sillon.data.repository

import ovh.battistella.sillon.data.plex.ServerEndpoint

/**
 * How one Sonos session reaches the server: the address and the token put in the speaker's URLs.
 * Never the account's own token. [close] it when the session ends.
 */
class SonosAccess(val endpoint: ServerEndpoint, private val revoke: suspend () -> Unit = {}) {
    suspend fun close() {
        runCatching { revoke() }
    }
}

/** The server gives no token fit for a speaker, so nothing is sent to it. */
class SonosAccessException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Plex refused a transient token. */
        PLEX_TOKEN,
    }
}

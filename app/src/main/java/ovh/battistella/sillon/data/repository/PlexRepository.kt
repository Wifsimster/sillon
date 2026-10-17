package ovh.battistella.sillon.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.plex.ConnectionSelector
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.PlexPin
import ovh.battistella.sillon.data.plex.PlexResource
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a library call is made before a server is selected. */
class NoServerException : IllegalStateException("No Plex server selected")

/**
 * Everything the UI needs from Plex, with the current account and server
 * filled in from [SessionStore].
 */
@Singleton
class PlexRepository @Inject constructor(
    private val api: PlexApi,
    private val sessionStore: SessionStore,
) {
    val session: Flow<Session> = sessionStore.session

    private val reconnectLock = Mutex()

    // ---------------------------------------------------------------------
    // Sign-in
    // ---------------------------------------------------------------------

    suspend fun createPin(): PlexPin = api.createPin(sessionStore.clientId())

    suspend fun authUrl(pin: PlexPin): String = api.authUrl(sessionStore.clientId(), pin)

    /** Poll [pin]; once approved, store the account token and return true. */
    suspend fun completeSignInIfApproved(pin: PlexPin): Boolean {
        val token = api.checkPin(sessionStore.clientId(), pin).authToken
        if (token.isNullOrBlank()) return false
        sessionStore.signIn(token)
        return true
    }

    suspend fun signOut() = sessionStore.signOut()

    suspend fun changeServer() = sessionStore.clearServer()

    // ---------------------------------------------------------------------
    // Servers
    // ---------------------------------------------------------------------

    /** Plex Media Servers on the account, owned ones first. */
    suspend fun servers(): List<PlexResource> {
        val userToken = userToken()
        return api.resources(sessionStore.clientId(), userToken)
            .filter { it.isServer }
            .sortedWith(compareByDescending<PlexResource> { it.owned }.thenBy { it.name.lowercase() })
    }

    /** Find a working address for [resource] and make it the current server. False if none answers. */
    suspend fun select(resource: PlexResource): Boolean {
        val server = connect(resource) ?: return false
        sessionStore.selectServer(server)
        return true
    }

    private suspend fun connect(resource: PlexResource): SelectedServer? {
        val clientId = sessionStore.clientId()
        // A shared server has its own token; for an owned one it may be absent.
        val token = resource.accessToken ?: userToken()
        val connection = ConnectionSelector.select(resource.connections) {
            api.probe(clientId, it.uri, token)
        } ?: return null
        return SelectedServer(resource.clientIdentifier, resource.name, ServerEndpoint(connection.uri, token))
    }

    // ---------------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------------

    suspend fun musicSections(): List<PlexDirectory> =
        onServer { clientId, server -> api.sections(clientId, server).filter { it.isMusic } }

    suspend fun artists(sectionKey: String): List<PlexMetadata> =
        onServer { clientId, server -> api.artists(clientId, server, sectionKey) }

    suspend fun children(ratingKey: String): List<PlexMetadata> =
        onServer { clientId, server -> api.children(clientId, server, ratingKey) }

    suspend fun metadata(ratingKey: String): PlexMetadata? =
        onServer { clientId, server -> api.metadata(clientId, server, ratingKey) }

    /** The current server's address and token, for building artwork and stream URLs. */
    suspend fun endpoint(): ServerEndpoint = currentServer().endpoint

    fun artworkUrl(server: ServerEndpoint, thumb: String?, sizePx: Int): String? =
        api.artworkUrl(server, thumb, sizePx)

    fun streamUrl(server: ServerEndpoint, track: PlexMetadata): String? =
        track.partKey?.let { api.streamUrl(server, it) }

    /**
     * Run a server call; if the saved address no longer answers (the phone left
     * the home Wi-Fi, the public IP changed), look for a working one and retry
     * once. HTTP errors are real answers from the server and are not retried.
     */
    private suspend fun <T> onServer(block: suspend (String, ServerEndpoint) -> T): T {
        val clientId = sessionStore.clientId()
        val server = currentServer()
        return try {
            block(clientId, server.endpoint)
        } catch (e: IOException) {
            if (e is PlexHttpException) throw e
            val fresh = reconnect(server) ?: throw e
            block(clientId, fresh.endpoint)
        }
    }

    private suspend fun reconnect(stale: SelectedServer): SelectedServer? = reconnectLock.withLock {
        // Another caller may have reconnected while this one waited for the lock.
        val current = currentServer()
        if (current.endpoint != stale.endpoint) return@withLock current
        val resource = servers().firstOrNull { it.clientIdentifier == stale.machineId } ?: return@withLock null
        connect(resource)?.also { sessionStore.selectServer(it) }
    }

    private suspend fun currentServer(): SelectedServer =
        (sessionStore.session.first() as? Session.Ready)?.server ?: throw NoServerException()

    private suspend fun userToken(): String = when (val s = sessionStore.session.first()) {
        is Session.NeedsServer -> s.userToken
        is Session.Ready -> s.userToken
        Session.SignedOut -> throw PlexHttpException(401, "Signed out")
    }
}

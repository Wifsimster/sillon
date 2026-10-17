package ovh.battistella.sillon.data.repository

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.ConnectionSelector
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.plex.PlexHub
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.PlexPin
import ovh.battistella.sillon.data.plex.PlexResource
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.playback.PlayableTrack
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The two PINs of a sign-in attempt; approving either one signs the user in. */
data class SignInPins(val browser: PlexPin, val link: PlexPin)

/** True when the download finished and its file is still there. */
fun DownloadEntity.isPlayableOffline(): Boolean =
    state == DownloadState.DONE && filePath != null && File(filePath).isFile

/** A downloaded track as a player item, playing from its file. */
fun DownloadEntity.toPlayable(): PlayableTrack = PlayableTrack(
    ratingKey = ratingKey,
    streamUrl = Uri.fromFile(File(checkNotNull(filePath))).toString(),
    title = title,
    artist = artist,
    album = album,
    trackNumber = trackNumber,
    durationMs = durationMs,
    artworkUrl = artworkPath?.let { Uri.fromFile(File(it)).toString() },
)

/** Cover size requested for the player and the media notification, in pixels. */
const val PLAYER_ART_PX = 900

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
    private val downloads: DownloadDao,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
) {
    val session: Flow<Session> = sessionStore.session

    private val reconnectLock = Mutex()

    // ---------------------------------------------------------------------
    // Sign-in
    // ---------------------------------------------------------------------

    /** A strong PIN for the browser sign-in and a short one for plex.tv/link. */
    suspend fun createPins(): SignInPins = coroutineScope {
        val clientId = sessionStore.clientId()
        val browser = async { api.createPin(clientId, strong = true) }
        val link = async { api.createPin(clientId, strong = false) }
        SignInPins(browser = browser.await(), link = link.await())
    }

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

    /** The chosen music library, or the server's first one. Null when the server has none. */
    suspend fun currentMusicSection(): PlexDirectory? {
        val sections = musicSections()
        val chosen = sessionStore.musicSectionKey.first()
        return sections.firstOrNull { it.key == chosen } ?: sections.firstOrNull()
    }

    /** Emits whenever the user picks another music library. */
    val musicSectionKey: Flow<String?> = sessionStore.musicSectionKey

    suspend fun selectMusicSection(key: String) = sessionStore.selectMusicSection(key)

    suspend fun albums(sectionKey: String): List<PlexMetadata> =
        onServer { clientId, server -> api.albums(clientId, server, sectionKey) }

    /** Tracks rated 5 stars in the current music library. */
    suspend fun fiveStarTracks(): List<PlexMetadata> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer { clientId, server -> api.fiveStarTracks(clientId, server, section.key) }
    }

    suspend fun playlists(): List<PlexMetadata> =
        onServer { clientId, server -> api.playlists(clientId, server) }

    suspend fun playlistItems(ratingKey: String): List<PlexMetadata> =
        onServer { clientId, server -> api.playlistItems(clientId, server, ratingKey) }

    suspend fun search(sectionKey: String, query: String): List<PlexHub> =
        onServer { clientId, server -> api.search(clientId, server, sectionKey, query) }

    /**
     * Turn tracks into player items. Downloaded tracks play from their file;
     * the others stream from the server. Some listings (search results) may omit
     * the media parts; those tracks are fetched in full first. Tracks with no
     * file are left out.
     */
    suspend fun playable(tracks: List<PlexMetadata>, fallbackArtworkUrl: String? = null): List<PlayableTrack> {
        val server = currentServer()
        val local = downloads.get(server.machineId, tracks.map { it.ratingKey })
            .filter { it.isPlayableOffline() }
            .associateBy { it.ratingKey }
        val complete = coroutineScope {
            tracks.map { track ->
                async {
                    when {
                        track.partKey != null || track.ratingKey in local -> track
                        else -> runCatching { metadata(track.ratingKey) }.getOrNull()
                    }
                }
            }.awaitAll()
        }
        // Original file or transcode, by the quality chosen for the current network.
        val quality = if (network.isMetered()) settings.mobileQuality.first() else settings.wifiQuality.first()
        val clientId = sessionStore.clientId()
        return complete.filterNotNull().mapNotNull { track ->
            local[track.ratingKey]?.let { return@mapNotNull it.toPlayable() }
            if (track.partKey == null) return@mapNotNull null
            val url = quality.kbps?.let { kbps ->
                api.transcodeUrl(server.endpoint, clientId, track.ratingKey, kbps, UUID.randomUUID().toString())
            } ?: streamUrl(server.endpoint, track) ?: return@mapNotNull null
            PlayableTrack(
                ratingKey = track.ratingKey,
                streamUrl = url,
                title = track.title,
                artist = track.trackArtist,
                album = track.parentTitle,
                trackNumber = track.index,
                durationMs = track.duration,
                artworkUrl = artworkUrl(server.endpoint, track.artwork, PLAYER_ART_PX) ?: fallbackArtworkUrl,
            )
        }
    }

    /** Machine identifier of the current server, which downloads are filed under. */
    suspend fun currentServerId(): String = currentServer().machineId

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

    fun streamUrl(server: ServerEndpoint, partKey: String): String = api.streamUrl(server, partKey)

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

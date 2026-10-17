package ovh.battistella.sillon.data.repository

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.jellyfin.JellyfinAuthResult
import ovh.battistella.sillon.data.jellyfin.JellyfinPublicInfo
import ovh.battistella.sillon.data.jellyfin.JellyfinQuickConnect
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
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.plex.TimelineState
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import ovh.battistella.sillon.data.settings.LoudnessMode
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
fun DownloadEntity.toPlayable(loudness: LoudnessMode = LoudnessMode.OFF): PlayableTrack = PlayableTrack(
    ratingKey = ratingKey,
    streamUrl = Uri.fromFile(File(checkNotNull(filePath))).toString(),
    title = title,
    artist = artist,
    album = album,
    trackNumber = trackNumber,
    durationMs = durationMs,
    artworkUrl = artworkPath?.let { Uri.fromFile(File(it)).toString() },
    gainDb = when (loudness) {
        LoudnessMode.OFF -> null
        LoudnessMode.TRACK -> gainDb
        LoudnessMode.ALBUM -> albumGainDb ?: gainDb
    },
)

/** Track and album gains in dB, by track key. */
data class Loudness(val track: Float?, val album: Float?) {
    fun forMode(mode: LoudnessMode): Float? = when (mode) {
        LoudnessMode.OFF -> null
        LoudnessMode.TRACK -> track
        LoudnessMode.ALBUM -> album ?: track
    }
}

/**
 * A radio to play: a Plex station (its path is the key), or Jellyfin's
 * library shuffle. A null [title] means "the library radio", named by the UI.
 */
data class Station(val key: String, val title: String?)

/** Key of the one Jellyfin "station": the whole library, shuffled. */
const val LIBRARY_SHUFFLE_STATION = "jellyfin:library-shuffle"

/** Cover size requested for the player and the media notification, in pixels. */
const val PLAYER_ART_PX = 900

/** Thrown when a library call is made before a server is selected. */
class NoServerException : IllegalStateException("No server selected")

/**
 * Everything the UI needs from the media server, with the current account and
 * server filled in from [SessionStore]. Each call goes to Plex or Jellyfin
 * depending on the server the user signed in to; both answer with the same
 * models (named after Plex, which Sillon supported first).
 */
@Singleton
class PlexRepository @Inject constructor(
    private val api: PlexApi,
    private val sessionStore: SessionStore,
    private val downloads: DownloadDao,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val jellyfin: JellyfinApi,
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

    /** Back to the server list (Plex), or to the sign-in screen (Jellyfin: one server per sign-in). */
    suspend fun changeServer() {
        if (jellyfinServer() != null) sessionStore.signOut() else sessionStore.clearServer()
    }

    // ---------------------------------------------------------------------
    // Jellyfin sign-in
    // ---------------------------------------------------------------------

    /**
     * Find the Jellyfin server behind what the user typed. Without a scheme,
     * HTTPS is tried first, then HTTP (Jellyfin's default on a home network).
     */
    suspend fun findJellyfinServer(input: String): Pair<String, JellyfinPublicInfo> {
        val trimmed = input.trim().trimEnd('/')
        val candidates = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            listOf(trimmed)
        } else {
            listOf("https://$trimmed", "http://$trimmed")
        }
        var lastError: Throwable? = null
        for (url in candidates) {
            val info = runCatching { jellyfin.publicInfo(url) }.onFailure { lastError = it }.getOrNull()
            if (info != null && info.id.isNotBlank()) return url to info
        }
        throw lastError ?: IOException("No Jellyfin server at $input")
    }

    suspend fun signInJellyfin(baseUrl: String, info: JellyfinPublicInfo, username: String, password: String) {
        val auth = jellyfin.authenticate(baseUrl, sessionStore.clientId(), username, password)
        storeJellyfin(baseUrl, info, auth)
    }

    suspend fun startQuickConnect(baseUrl: String): JellyfinQuickConnect =
        jellyfin.quickConnectInitiate(baseUrl, sessionStore.clientId())

    /** Poll a Quick Connect request; once approved, sign in and return true. */
    suspend fun completeQuickConnectIfApproved(baseUrl: String, info: JellyfinPublicInfo, secret: String): Boolean {
        val clientId = sessionStore.clientId()
        if (!jellyfin.quickConnectState(baseUrl, clientId, secret).authenticated) return false
        storeJellyfin(baseUrl, info, jellyfin.quickConnectAuthenticate(baseUrl, clientId, secret))
        return true
    }

    private suspend fun storeJellyfin(baseUrl: String, info: JellyfinPublicInfo, auth: JellyfinAuthResult) {
        sessionStore.signInJellyfin(
            SelectedServer(
                machineId = auth.serverId,
                name = info.serverName.ifBlank { baseUrl },
                endpoint = ServerEndpoint(baseUrl, auth.accessToken, ServerKind.JELLYFIN, auth.user.id),
            ),
        )
    }

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

    suspend fun albums(sectionKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.albums(clientId, server, sectionKey) },
        jellyfin = { deviceId, server -> jellyfin.albums(server, deviceId, sectionKey) },
    )

    /**
     * Tracks rated 5 stars in the current music library. Jellyfin has no star
     * ratings: its favourites stand in for them.
     */
    suspend fun fiveStarTracks(): List<PlexMetadata> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server -> api.fiveStarTracks(clientId, server, section.key) },
            jellyfin = { deviceId, server -> jellyfin.favouriteTracks(server, deviceId, section.key) },
        )
    }

    /** The library's radios. */
    suspend fun stations(): List<Station> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server ->
                api.stations(clientId, server, section.key).map { Station(it.key ?: "", it.title) }.filter { it.key.isNotBlank() }
            },
            jellyfin = { _, _ -> listOf(Station(LIBRARY_SHUFFLE_STATION, null)) },
        )
    }

    /** About a hundred tracks of [station], in the order the server picked. */
    suspend fun stationTracks(station: Station): List<PlexMetadata> {
        val machineId = currentServer().machineId
        return onServer(
            plex = { clientId, server -> api.stationTracks(clientId, server, machineId, station.key) },
            jellyfin = { deviceId, server ->
                val section = currentMusicSection() ?: return@onServer emptyList()
                jellyfin.randomTracks(server, deviceId, section.key)
            },
        )
    }

    /**
     * An artist's radio: Plex's artist station, or Jellyfin's Instant Mix.
     * Plex only has stations for some artists; for the others, the artist's
     * own tracks, shuffled.
     */
    suspend fun artistRadioTracks(artistKey: String): List<PlexMetadata> {
        val machineId = currentServer().machineId
        return onServer(
            plex = { clientId, server ->
                val station = api.artistStation(clientId, server, artistKey)
                if (station != null) {
                    api.stationTracks(clientId, server, machineId, station)
                } else {
                    api.artistTracks(clientId, server, artistKey).shuffled()
                }
            },
            jellyfin = { deviceId, server -> jellyfin.instantMix(server, deviceId, artistKey) },
        )
    }

    suspend fun playlists(): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.playlists(clientId, server) },
        jellyfin = { deviceId, server -> jellyfin.playlists(server, deviceId) },
    )

    suspend fun playlistItems(ratingKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.playlistItems(clientId, server, ratingKey) },
        jellyfin = { deviceId, server -> jellyfin.playlistItems(server, deviceId, ratingKey) },
    )

    suspend fun search(sectionKey: String, query: String): List<PlexHub> = onServer(
        plex = { clientId, server -> api.search(clientId, server, sectionKey, query) },
        jellyfin = { deviceId, server -> jellyfin.search(server, deviceId, sectionKey, query) },
    )

    /** True when the current server is a Jellyfin one (the UI words a few things differently). */
    val isJellyfin: Flow<Boolean> = sessionStore.session.map {
        (it as? Session.Ready)?.server?.endpoint?.kind == ServerKind.JELLYFIN
    }

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
        val mode = settings.loudness.first()
        val loudness = if (mode == LoudnessMode.OFF) {
            emptyMap()
        } else {
            loudness(complete.filterNotNull().filter { it.ratingKey !in local })
        }
        return complete.filterNotNull().mapNotNull { track ->
            local[track.ratingKey]?.let { return@mapNotNull it.toPlayable(mode) }
            if (track.partKey == null) return@mapNotNull null
            val url = quality.kbps?.let { kbps ->
                val session = UUID.randomUUID().toString()
                when (server.endpoint.kind) {
                    ServerKind.PLEX -> api.transcodeUrl(server.endpoint, clientId, track.ratingKey, kbps, session)
                    ServerKind.JELLYFIN -> jellyfin.transcodeUrl(server.endpoint, clientId, track.ratingKey, kbps, session)
                }
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
                gainDb = loudness[track.ratingKey]?.forMode(mode),
            )
        }
    }

    /**
     * Loudness gains of [tracks]. Jellyfin lists them with the tracks; Plex
     * only gives them in full item responses, fetched here in one batched
     * request. Best effort: no gains rather than no music.
     */
    suspend fun loudness(tracks: List<PlexMetadata>): Map<String, Loudness> {
        val known = tracks.filter { it.trackGain != null }
        val missing = tracks.filter { it.trackGain == null }.map { it.ratingKey }
        val fetched = if (missing.isEmpty() || jellyfinServer() != null) {
            emptyList()
        } else {
            runCatching {
                onServer(
                    plex = { clientId, server -> api.metadataBatch(clientId, server, missing) },
                    jellyfin = { _, _ -> emptyList() },
                )
            }.getOrDefault(emptyList())
        }
        return (known + fetched).associate { it.ratingKey to Loudness(it.trackGain, it.albumGain) }
    }

    /** Machine identifier of the current server, which downloads are filed under. */
    suspend fun currentServerId(): String = currentServer().machineId

    suspend fun musicSections(): List<PlexDirectory> = onServer(
        plex = { clientId, server -> api.sections(clientId, server) },
        jellyfin = { deviceId, server -> jellyfin.sections(server, deviceId) },
    ).filter { it.isMusic }

    suspend fun artists(sectionKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.artists(clientId, server, sectionKey) },
        jellyfin = { deviceId, server -> jellyfin.artists(server, deviceId, sectionKey) },
    )

    /** An artist's albums. */
    suspend fun albumsOf(artistKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.children(clientId, server, artistKey) },
        jellyfin = { deviceId, server -> jellyfin.albumsOf(server, deviceId, artistKey) },
    )

    /** An album's tracks. */
    suspend fun tracksOf(albumKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.children(clientId, server, albumKey) },
        jellyfin = { deviceId, server -> jellyfin.tracksOf(server, deviceId, albumKey) },
    )

    suspend fun metadata(ratingKey: String): PlexMetadata? = onServer(
        plex = { clientId, server -> api.metadata(clientId, server, ratingKey) },
        jellyfin = { deviceId, server -> jellyfin.item(server, deviceId, ratingKey) },
    )

    /** Report playback of a track to the server it was played from. */
    suspend fun reportPlayback(
        server: ServerEndpoint,
        ratingKey: String,
        state: TimelineState,
        timeMs: Long,
        durationMs: Long,
        continuing: Boolean,
    ) {
        val clientId = sessionStore.clientId()
        when (server.kind) {
            ServerKind.PLEX -> api.timeline(clientId, server, ratingKey, state, timeMs, durationMs, continuing)
            ServerKind.JELLYFIN -> {
                // Jellyfin wants a "started" event once per track, then progress, then "stopped".
                val event = when {
                    state == TimelineState.Stopped -> "Playing/Stopped".also { jellyfinStarted = null }
                    jellyfinStarted != ratingKey -> "Playing".also { jellyfinStarted = ratingKey }
                    else -> "Playing/Progress"
                }
                jellyfin.reportPlayback(server, clientId, event, ratingKey, timeMs, paused = state == TimelineState.Paused)
            }
        }
    }

    /** The track Jellyfin was last told started playing. */
    @Volatile private var jellyfinStarted: String? = null

    /** The current server's address and token, for building artwork and stream URLs. */
    suspend fun endpoint(): ServerEndpoint = currentServer().endpoint

    fun artworkUrl(server: ServerEndpoint, thumb: String?, sizePx: Int): String? = when (server.kind) {
        ServerKind.PLEX -> api.artworkUrl(server, thumb, sizePx)
        ServerKind.JELLYFIN -> jellyfin.artworkUrl(server, thumb, sizePx)
    }

    fun streamUrl(server: ServerEndpoint, partKey: String): String = when (server.kind) {
        ServerKind.PLEX -> api.streamUrl(server, partKey)
        ServerKind.JELLYFIN -> jellyfin.streamUrl(server, partKey)
    }

    fun streamUrl(server: ServerEndpoint, track: PlexMetadata): String? =
        track.partKey?.let { streamUrl(server, it) }

    /**
     * Run a server call; if the saved address no longer answers (the phone left
     * the home Wi-Fi, the public IP changed), look for a working one and retry
     * once. HTTP errors are real answers from the server and are not retried.
     */
    private suspend fun <T> onServer(
        plex: suspend (String, ServerEndpoint) -> T,
        jellyfin: suspend (String, ServerEndpoint) -> T,
    ): T {
        val clientId = sessionStore.clientId()
        val server = currentServer()
        // A Jellyfin server has the one address the user typed: nothing to reconnect to.
        if (server.endpoint.kind == ServerKind.JELLYFIN) return jellyfin(clientId, server.endpoint)
        val block = plex
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

    private suspend fun jellyfinServer(): ServerEndpoint? =
        (sessionStore.session.first() as? Session.Ready)?.server?.endpoint?.takeIf { it.kind == ServerKind.JELLYFIN }

    private suspend fun currentServer(): SelectedServer =
        (sessionStore.session.first() as? Session.Ready)?.server ?: throw NoServerException()

    private suspend fun userToken(): String = when (val s = sessionStore.session.first()) {
        is Session.NeedsServer -> s.userToken
        is Session.Ready -> s.userToken
        Session.SignedOut -> throw PlexHttpException(401, "Signed out")
    }
}

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
import ovh.battistella.sillon.data.wear.AlbumPlays
import ovh.battistella.sillon.data.wear.jellyfinAlbumPlays
import ovh.battistella.sillon.data.wear.plexAlbumPlays
import ovh.battistella.sillon.playback.PlayableTrack
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SignInPins(val browser: PlexPin, val link: PlexPin)

fun DownloadEntity.isPlayableOffline(): Boolean =
    state == DownloadState.DONE && filePath != null && File(filePath).isFile

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
    albumKey = albumKey,
)

data class Loudness(val track: Float?, val album: Float?) {
    fun forMode(mode: LoudnessMode): Float? = when (mode) {
        LoudnessMode.OFF -> null
        LoudnessMode.TRACK -> track
        LoudnessMode.ALBUM -> album ?: track
    }
}

data class Station(val key: String, val title: String?)

const val LIBRARY_SHUFFLE_STATION = "jellyfin:library-shuffle"

const val PLAYER_ART_PX = 900

class NoServerException : IllegalStateException("No server selected")

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

    suspend fun createPins(): SignInPins = coroutineScope {
        val clientId = sessionStore.clientId()
        val browser = async { api.createPin(clientId, strong = true) }
        val link = async { api.createPin(clientId, strong = false) }
        SignInPins(browser = browser.await(), link = link.await())
    }

    suspend fun authUrl(pin: PlexPin): String = api.authUrl(sessionStore.clientId(), pin)

    suspend fun completeSignInIfApproved(pin: PlexPin): Boolean {
        val token = api.checkPin(sessionStore.clientId(), pin).authToken
        if (token.isNullOrBlank()) return false
        sessionStore.signIn(token)
        return true
    }

    suspend fun signOut() = sessionStore.signOut()

    suspend fun changeServer() {
        if (jellyfinServer() != null) sessionStore.signOut() else sessionStore.clearServer()
    }

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

    suspend fun servers(): List<PlexResource> {
        val userToken = userToken()
        return api.resources(sessionStore.clientId(), userToken)
            .filter { it.isServer }
            .sortedWith(compareByDescending<PlexResource> { it.owned }.thenBy { it.name.lowercase() })
    }

    suspend fun select(resource: PlexResource): Boolean {
        val server = connect(resource) ?: return false
        sessionStore.selectServer(server)
        return true
    }

    private suspend fun connect(resource: PlexResource): SelectedServer? {
        val clientId = sessionStore.clientId()
        val token = resource.accessToken ?: userToken()
        val connection = ConnectionSelector.select(resource.connections) {
            api.probe(clientId, it.uri, token)
        } ?: return null
        return SelectedServer(resource.clientIdentifier, resource.name, ServerEndpoint(connection.uri, token))
    }

    suspend fun currentMusicSection(): PlexDirectory? {
        val sections = musicSections()
        val chosen = sessionStore.musicSectionKey.first()
        return sections.firstOrNull { it.key == chosen } ?: sections.firstOrNull()
    }

    val musicSectionKey: Flow<String?> = sessionStore.musicSectionKey

    suspend fun selectMusicSection(key: String) = sessionStore.selectMusicSection(key)

    suspend fun albums(sectionKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.albums(clientId, server, sectionKey) },
        jellyfin = { deviceId, server -> jellyfin.albums(server, deviceId, sectionKey) },
    )

    suspend fun albumPlays(sectionKey: String, albums: List<PlexMetadata>): Map<String, AlbumPlays> = onServer(
        plex = { _, _ -> plexAlbumPlays(albums) },
        jellyfin = { deviceId, server -> jellyfinAlbumPlays(albums, jellyfin.playedTracks(server, deviceId, sectionKey)) },
    )

    suspend fun fiveStarTracks(): List<PlexMetadata> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server -> api.fiveStarTracks(clientId, server, section.key) },
            jellyfin = { deviceId, server -> jellyfin.favouriteTracks(server, deviceId, section.key) },
        )
    }

    suspend fun restoreQueue(keys: List<String>): List<PlayableTrack> {
        val fetched = runCatching {
            onServer(
                plex = { clientId, server -> api.metadataBatch(clientId, server, keys) },
                jellyfin = { deviceId, server -> jellyfin.itemsByIds(server, deviceId, keys) },
            )
        }.getOrNull()
        if (fetched != null) {
            val byKey = fetched.associateBy { it.ratingKey }
            return playable(keys.mapNotNull { byKey[it] })
        }
        val mode = settings.loudness.first()
        val local = downloads.get(currentServer().machineId, keys).filter { it.isPlayableOffline() }.associateBy { it.ratingKey }
        return keys.mapNotNull { local[it]?.toPlayable(mode) }
    }

    suspend fun stations(): List<Station> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server ->
                val mixes = runCatching { api.mixes(clientId, server, section.key) }.getOrDefault(emptyList())
                (mixes + api.stations(clientId, server, section.key))
                    .map { Station(it.key ?: "", it.title) }
                    .filter { it.key.isNotBlank() }
                    .distinctBy { it.key }
            },
            jellyfin = { _, _ -> listOf(Station(LIBRARY_SHUFFLE_STATION, null)) },
        )
    }

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

    suspend fun trackRadioTracks(trackKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server ->
            val similar = runCatching { api.sonicallySimilar(clientId, server, trackKey) }.getOrDefault(emptyList())
            if (similar.isNotEmpty()) similar else trackRadioFallback(clientId, server, trackKey)
        },
        jellyfin = { deviceId, server -> jellyfin.instantMix(server, deviceId, trackKey) },
    )

    private suspend fun trackRadioFallback(clientId: String, server: ServerEndpoint, trackKey: String): List<PlexMetadata> {
        val track = api.metadata(clientId, server, trackKey) ?: return emptyList()
        val section = currentMusicSection()
        val performer = section?.let { s ->
            performers(track.originalTitle).firstNotNullOfOrNull { name ->
                runCatching { api.artistNamed(clientId, server, s.key, name) }.getOrNull()
            }
        }
        val artistKey = performer?.ratingKey?.takeIf { it.isNotBlank() }
            ?: track.grandparentRatingKey.takeUnless { isCompilationArtist(track.grandparentTitle) }
        if (artistKey != null) return artistRadioTracks(artistKey)
        val libraryRadio = stations().firstOrNull() ?: return emptyList()
        return stationTracks(libraryRadio)
    }

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

    val isJellyfin: Flow<Boolean> = sessionStore.session.map {
        (it as? Session.Ready)?.server?.endpoint?.kind == ServerKind.JELLYFIN
    }

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
                albumKey = track.parentRatingKey,
            )
        }
    }

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

    suspend fun currentServerId(): String = currentServer().machineId

    suspend fun musicSections(): List<PlexDirectory> = onServer(
        plex = { clientId, server -> api.sections(clientId, server) },
        jellyfin = { deviceId, server -> jellyfin.sections(server, deviceId) },
    ).filter { it.isMusic }

    suspend fun artists(sectionKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.artists(clientId, server, sectionKey) },
        jellyfin = { deviceId, server -> jellyfin.artists(server, deviceId, sectionKey) },
    )

    suspend fun albumsOf(artistKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.children(clientId, server, artistKey) },
        jellyfin = { deviceId, server -> jellyfin.albumsOf(server, deviceId, artistKey) },
    )

    suspend fun tracksOf(albumKey: String): List<PlexMetadata> = onServer(
        plex = { clientId, server -> api.children(clientId, server, albumKey) },
        jellyfin = { deviceId, server -> jellyfin.tracksOf(server, deviceId, albumKey) },
    )

    suspend fun metadata(ratingKey: String): PlexMetadata? = onServer(
        plex = { clientId, server -> api.metadata(clientId, server, ratingKey) },
        jellyfin = { deviceId, server -> jellyfin.item(server, deviceId, ratingKey) },
    )

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
                val event = when {
                    state == TimelineState.Stopped -> "Playing/Stopped".also { jellyfinStarted = null }
                    jellyfinStarted != ratingKey -> "Playing".also { jellyfinStarted = ratingKey }
                    else -> "Playing/Progress"
                }
                jellyfin.reportPlayback(server, clientId, event, ratingKey, timeMs, paused = state == TimelineState.Paused)
            }
        }
    }

    @Volatile private var jellyfinStarted: String? = null

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

    private suspend fun <T> onServer(
        plex: suspend (String, ServerEndpoint) -> T,
        jellyfin: suspend (String, ServerEndpoint) -> T,
    ): T {
        val clientId = sessionStore.clientId()
        val server = currentServer()
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

fun performers(credit: String?): List<String> =
    credit.orEmpty()
        .split(Regex("""\s*(?:/|,|;|&| feat\.? | ft\.? | featuring | x )\s*""", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun isCompilationArtist(name: String?): Boolean =
    name != null && name.trim().lowercase() in setOf("various artists", "various", "artistes divers", "compilation", "va")

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
import ovh.battistella.sillon.data.home.HomeAlbum
import ovh.battistella.sillon.data.home.SearchHome
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
import ovh.battistella.sillon.data.settings.FiveStarsCache
import ovh.battistella.sillon.data.settings.LoudnessMode
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.settings.StreamQuality
import ovh.battistella.sillon.data.wear.AlbumPlays
import ovh.battistella.sillon.data.wear.SleeveWearCache
import ovh.battistella.sillon.data.wear.jellyfinAlbumPlays
import ovh.battistella.sillon.data.wear.plexAlbumPlays
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.cache.ListeningCache
import ovh.battistella.sillon.playback.cache.ListeningCacheKeys
import ovh.battistella.sillon.playback.cache.StreamSource
import ovh.battistella.sillon.sonos.Didl
import ovh.battistella.sillon.sonos.SonosFormats
import ovh.battistella.sillon.sonos.SonosStream
import ovh.battistella.sillon.sonos.SonosTrack
import ovh.battistella.sillon.sonos.audioFormat
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

data class LibraryPlays(val neverPlayed: Int, val total: Int)

data class Station(val key: String, val title: String?)

const val LIBRARY_SHUFFLE_STATION = "jellyfin:library-shuffle"

const val PLAYER_ART_PX = 900

private const val SONOS_ART_PX = 600

/** The transcode qualities the listening cache may hold, best first. */
private val TRANSCODE_KBPS = StreamQuality.entries.mapNotNull { it.kbps }.sortedDescending()

class NoServerException : IllegalStateException("No server selected")

@Singleton
class PlexRepository @Inject constructor(
    private val api: PlexApi,
    private val sessionStore: SessionStore,
    private val downloads: DownloadDao,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val jellyfin: JellyfinApi,
    private val sleeveWearCache: SleeveWearCache,
    private val fiveStarsCache: FiveStarsCache,
    private val listeningCache: ListeningCache,
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

    suspend fun signOut() {
        sessionStore.signOut()
        sleeveWearCache.clear()
        fiveStarsCache.clear()
        listeningCache.clear(sweepAgain = true)
    }

    /** Leaving a server leaves its music behind: the listening cache is emptied too. */
    suspend fun changeServer() {
        if (jellyfinServer() != null) {
            signOut()
        } else {
            sessionStore.clearServer()
            fiveStarsCache.clear()
            listeningCache.clear(sweepAgain = true)
        }
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

    /** A random draw over the whole library, made by the server: nothing is downloaded beforehand. */
    suspend fun libraryShuffleTracks(): List<PlexMetadata> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server -> api.sectionItems(clientId, server, section.key, LibraryShuffle.plexTracks()).metadata },
            jellyfin = { deviceId, server -> jellyfin.randomTracks(server, deviceId, section.key) },
        )
    }

    suspend fun neverPlayedTracks(): List<PlexMetadata> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server ->
                api.sectionItems(clientId, server, section.key, NeverPlayedQueries.plexTracks()).metadata
            },
            jellyfin = { deviceId, server ->
                jellyfin.itemsPage(server, deviceId, NeverPlayedQueries.jellyfinTracks(section.key)).first
            },
        )
    }

    suspend fun neverPlayedAlbums(): List<PlexMetadata> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server ->
                api.sectionItems(clientId, server, section.key, NeverPlayedQueries.plexAlbums()).metadata
                    .filter { (it.viewedLeafCount ?: 0) == 0 && it.isNeverPlayed() }
            },
            jellyfin = { deviceId, server ->
                jellyfin.itemsPage(server, deviceId, NeverPlayedQueries.jellyfinAlbums(section.key)).first
            },
        )
    }

    suspend fun libraryPlays(): LibraryPlays? {
        val section = currentMusicSection() ?: return null
        return onServer(
            plex = { clientId, server ->
                val never = api.sectionItems(clientId, server, section.key, NeverPlayedQueries.plexTrackCount(true))
                if (never.metadata.firstOrNull()?.isNeverPlayed() == false) return@onServer null
                val all = api.sectionItems(clientId, server, section.key, NeverPlayedQueries.plexTrackCount(false))
                val neverCount = never.totalSize ?: return@onServer null
                val total = all.totalSize ?: return@onServer null
                LibraryPlays(neverCount, total)
            },
            jellyfin = { deviceId, server ->
                val never = jellyfin.itemsPage(server, deviceId, NeverPlayedQueries.jellyfinTrackCount(section.key, true)).second
                val total = jellyfin.itemsPage(server, deviceId, NeverPlayedQueries.jellyfinTrackCount(section.key, false)).second
                LibraryPlays(never, total)
            },
        )
    }

    suspend fun playCounts(keys: List<String>): Map<String, Int> {
        if (keys.isEmpty()) return emptyMap()
        return onServer(
            plex = { clientId, server -> api.metadataBatch(clientId, server, keys) },
            jellyfin = { deviceId, server -> jellyfin.itemsByIds(server, deviceId, keys) },
        ).associate { it.ratingKey to (it.viewCount ?: 0) }
    }

    /** The albums last played on the server, newest first. */
    suspend fun recentAlbums(): List<HomeAlbum> {
        val section = currentMusicSection() ?: return emptyList()
        return onServer(
            plex = { clientId, server ->
                SearchHome.plexRecent(api.sectionItems(clientId, server, section.key, SearchHome.plexRecentQuery()).metadata)
            },
            jellyfin = { deviceId, server ->
                SearchHome.recentFromTracks(jellyfin.itemsPage(server, deviceId, SearchHome.jellyfinRecentQuery(section.key)).first)
            },
        )
    }

    suspend fun itemsByKeys(keys: List<String>): List<PlexMetadata> {
        if (keys.isEmpty()) return emptyList()
        return onServer(
            plex = { clientId, server -> api.metadataBatch(clientId, server, keys) },
            jellyfin = { deviceId, server -> jellyfin.itemsByIds(server, deviceId, keys) },
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

    suspend fun addAlbumToPlaylist(albumKey: String, playlistTitle: String): Boolean {
        val machineId = currentServer().machineId
        return onServer(
            plex = { clientId, server ->
                val playlist = api.playlists(clientId, server).firstOrNull { it.title == playlistTitle }
                when {
                    playlist == null -> api.createPlaylist(clientId, server, machineId, playlistTitle, albumKey)
                    api.playlistItems(clientId, server, playlist.ratingKey).any { it.parentRatingKey == albumKey } ->
                        return@onServer false
                    else -> api.addToPlaylist(clientId, server, machineId, playlist.ratingKey, albumKey)
                }
                true
            },
            jellyfin = { deviceId, server ->
                val ids = jellyfin.tracksOf(server, deviceId, albumKey).map { it.ratingKey }
                val playlist = jellyfin.playlists(server, deviceId).firstOrNull { it.title == playlistTitle }
                if (playlist == null) {
                    jellyfin.createPlaylist(server, deviceId, playlistTitle, ids)
                    return@onServer true
                }
                val present = jellyfin.playlistItems(server, deviceId, playlist.ratingKey).map { it.ratingKey }.toSet()
                val missing = ids.filter { it !in present }
                if (missing.isEmpty()) return@onServer false
                jellyfin.addToPlaylist(server, deviceId, playlist.ratingKey, missing)
                true
            },
        )
    }

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
            val cacheKey = ListeningCacheKeys.track(server.machineId, track.ratingKey)
            val cached = listeningCache.versions(server.machineId, track.ratingKey, TRANSCODE_KBPS)
            val source = StreamSource.choose(
                downloaded = false,
                originalCached = cached.original,
                cachedTranscodeKbps = cached.transcodeKbps,
                wantedKbps = quality.kbps,
                online = network.isOnline(),
            )
            val url = if (source.transcode) {
                val kbps = checkNotNull(if (source == StreamSource.CACHED_TRANSCODE) cached.transcodeKbps else quality.kbps)
                val session = UUID.randomUUID().toString()
                if (source == StreamSource.CACHED_TRANSCODE) {
                    listeningCache.replayTranscode(session, server.machineId, track.ratingKey, kbps)
                } else {
                    listeningCache.streamTranscode(session, server.machineId, track.ratingKey, kbps)
                }
                when (server.endpoint.kind) {
                    ServerKind.PLEX -> api.transcodeUrl(server.endpoint, clientId, track.ratingKey, kbps, session)
                    ServerKind.JELLYFIN -> jellyfin.transcodeUrl(server.endpoint, clientId, track.ratingKey, kbps, session)
                }
            } else {
                streamUrl(server.endpoint, track) ?: return@mapNotNull null
            }
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
                cacheKey = cacheKey.takeIf { source.original },
            )
        }
    }

    /**
     * Opens a Sonos session's way to the server; the account's or Sillon's own token never goes
     * to a speaker. On Plex, a fresh transient token. On Jellyfin, a token of the speaker's own,
     * revoked when the access is closed. When the server gives none, the session doesn't start.
     */
    suspend fun openSonosAccess(): SonosAccess {
        val server = currentServer()
        val endpoint = server.endpoint
        return when (endpoint.kind) {
            ServerKind.PLEX -> {
                val token = try {
                    api.transientToken(sessionStore.clientId(), endpoint)
                } catch (e: Exception) {
                    throw SonosAccessException(SonosAccessException.Reason.PLEX_TOKEN, e)
                } ?: throw SonosAccessException(SonosAccessException.Reason.PLEX_TOKEN)
                SonosAccess(endpoint.copy(token = token))
            }
            ServerKind.JELLYFIN -> {
                val clientId = sessionStore.clientId()
                // Always the same speaker device: signing it in again replaces a token left behind.
                val speaker = "$clientId-sonos"
                val token = try {
                    jellyfin.speakerToken(endpoint, clientId, speaker)
                } catch (e: Exception) {
                    throw SonosAccessException(SonosAccessException.Reason.JELLYFIN_TOKEN, e)
                }
                SonosAccess(endpoint.copy(token = token), deviceId = speaker) { jellyfin.signOut(endpoint, speaker, token) }
            }
        }
    }

    /**
     * The tracks as a Sonos speaker fetches them, by key: the original file when the speaker
     * plays it, else an MP3 transcode; never a download or the listening cache (the speaker
     * reaches the server itself). The URLs carry [access]'s token only.
     */
    suspend fun sonosTracks(keys: List<String>, access: SonosAccess): Map<String, SonosTrack> {
        if (keys.isEmpty()) return emptyMap()
        val clientId = sessionStore.clientId()
        val tracks = onServer(
            plex = { id, endpoint -> api.metadataBatch(id, endpoint, keys) },
            jellyfin = { id, endpoint -> jellyfin.itemsWithFormat(endpoint, id, keys) },
        )
        val endpoint = access.endpoint
        return tracks.mapNotNull { track ->
            val part = track.partKey ?: return@mapNotNull null
            val stream = SonosFormats.plan(track.audioFormat())
            val (uri, mime) = when (stream) {
                is SonosStream.Direct -> streamUrl(endpoint, part) to stream.mimeType
                SonosStream.Transcode -> sonosTranscodeUrl(endpoint, access.deviceId ?: clientId, track.ratingKey) to SonosStream.Transcode.MIME_TYPE
            }
            val didl = Didl.track(
                key = track.ratingKey,
                uri = uri,
                mimeType = mime,
                title = track.title,
                artist = track.trackArtist,
                album = track.parentTitle,
                artUri = artworkUrl(endpoint, track.artwork, SONOS_ART_PX),
            )
            track.ratingKey to SonosTrack(track.ratingKey, uri, didl)
        }.toMap()
    }

    private fun sonosTranscodeUrl(endpoint: ServerEndpoint, clientId: String, key: String): String {
        val session = UUID.randomUUID().toString()
        val kbps = SonosStream.Transcode.KBPS
        return when (endpoint.kind) {
            ServerKind.PLEX -> api.sonosTranscodeUrl(endpoint, clientId, key, kbps, session)
            ServerKind.JELLYFIN -> jellyfin.sonosTranscodeUrl(endpoint, clientId, key, kbps, session)
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

    /** The lines of a track's lyrics, or an empty list when the server has none. Throws when the server can't be reached. */
    suspend fun lyrics(trackKey: String): List<String> = Lyrics.parse(
        onServer(
            plex = { clientId, server -> api.lyrics(clientId, server, trackKey) },
            jellyfin = { deviceId, server -> jellyfin.lyrics(server, deviceId, trackKey) },
        ),
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

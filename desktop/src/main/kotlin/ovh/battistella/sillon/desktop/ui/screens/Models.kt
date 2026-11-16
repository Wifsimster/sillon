package ovh.battistella.sillon.desktop.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.rating.withoutOneStarDownloads
import ovh.battistella.sillon.data.repository.LibraryShuffle
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.settings.FiveStarsCache
import ovh.battistella.sillon.desktop.AppGraph
import ovh.battistella.sillon.desktop.ui.components.Load
import ovh.battistella.sillon.desktop.ui.components.TileUi
import ovh.battistella.sillon.desktop.ui.components.TrackUi
import ovh.battistella.sillon.desktop.ui.components.toFailed
import ovh.battistella.sillon.playback.PlayableTrack
import kotlin.math.ln

const val TILE_ART_PX = 400
const val HEADER_ART_PX = 900

fun popularityOf(tracks: List<PlexMetadata>): Map<String, Float> {
    val counts = tracks.mapNotNull { t -> t.ratingCount?.takeIf { it > 0 }?.let { t.ratingKey to ln(it.toDouble()) } }
    if (counts.isEmpty()) return emptyMap()
    val low = counts.minOf { it.second }
    val high = counts.maxOf { it.second }
    val range = high - low
    return counts.associate { (key, value) -> key to if (range <= 0.0) 1f else ((value - low) / range).toFloat() }
}

fun PlexMetadata.toTrackUi(number: Int?, artist: String?, popularity: Map<String, Float> = emptyMap()) =
    TrackUi(ratingKey, number, title, artist, duration, popularity[ratingKey], userRating)

data class SectionGridUi(val sectionTitle: String?, val tiles: List<TileUi>?)

class SectionGridModel(
    private val graph: AppGraph,
    private val scope: CoroutineScope,
    private val subtitle: (PlexMetadata) -> String? = { null },
    private val fetch: suspend (String) -> List<PlexMetadata>,
    private val onFetched: (String, List<PlexMetadata>) -> Unit = { _, _ -> },
) {
    private val repository = graph.repository
    private val _state = MutableStateFlow<Load<SectionGridUi>>(Load.Loading)
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
        scope.launch { repository.musicSectionKey.drop(1).collect { load() } }
    }

    fun load() {
        job?.cancel()
        _state.value = Load.Loading
        job = scope.launch {
            _state.value = try {
                val section = repository.currentMusicSection()
                if (section == null) {
                    Load.Ready(SectionGridUi(null, null))
                } else {
                    val server = repository.endpoint()
                    val tiles = withContext(Dispatchers.Default) {
                        fetch(section.key).also { onFetched(section.key, it) }.map {
                            TileUi(it.ratingKey, it.title, subtitle(it), repository.artworkUrl(server, it.thumb, TILE_ART_PX))
                        }
                    }
                    Load.Ready(SectionGridUi(section.title, tiles))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toFailed()
            }
        }
    }
}

class PlaylistsModel(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val repository = graph.repository
    private val _state = MutableStateFlow<Load<List<TileUi>>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        scope.launch {
            _state.value = runCatching {
                val server = repository.endpoint()
                Load.Ready(
                    repository.playlists().map {
                        TileUi(it.ratingKey, it.title, it.leafCount?.toString(), repository.artworkUrl(server, it.artwork, TILE_ART_PX))
                    },
                )
            }.getOrElse { it.toFailed() }
        }
    }
}

data class ArtistUi(val name: String, val artworkUrl: String?, val albums: List<TileUi>)

class ArtistModel(private val graph: AppGraph, private val scope: CoroutineScope, private val key: String) {
    private val repository = graph.repository
    private val _state = MutableStateFlow<Load<ArtistUi>>(Load.Loading)
    val state = _state.asStateFlow()
    private val _startingRadio = MutableStateFlow(false)
    val startingRadio = _startingRadio.asStateFlow()

    init {
        load()
    }

    fun playRadio() {
        if (_startingRadio.value) return
        _startingRadio.value = true
        scope.launch {
            runCatching { repository.playable(repository.artistRadioTracks(key)) }
                .onSuccess { if (it.isNotEmpty()) graph.playback.play(it) }
            _startingRadio.value = false
        }
    }

    fun load() {
        _state.value = Load.Loading
        scope.launch {
            _state.value = runCatching {
                coroutineScope {
                    val server = repository.endpoint()
                    val artist = async { repository.metadata(key) }
                    val albums = repository.albumsOf(key).sortedByDescending { it.year ?: 0 }.map {
                        TileUi(it.ratingKey, it.title, it.year?.toString(), repository.artworkUrl(server, it.thumb, TILE_ART_PX))
                    }
                    val a = artist.await()
                    Load.Ready(ArtistUi(a?.title.orEmpty(), repository.artworkUrl(server, a?.thumb, HEADER_ART_PX), albums))
                }
            }.getOrElse { it.toFailed() }
        }
    }
}

data class DownloadStatus(val total: Int = 0, val done: Int = 0, val active: Int = 0, val failed: Int = 0) {
    val isComplete: Boolean get() = total > 0 && done == total
    val isDownloading: Boolean get() = active > 0
    val isStarted: Boolean get() = done + active + failed > 0

    companion object {
        fun of(keys: List<String>, downloads: List<DownloadEntity>): DownloadStatus {
            val byKey = downloads.associateBy { it.ratingKey }
            val states = keys.mapNotNull { byKey[it]?.state }
            return DownloadStatus(
                total = keys.size,
                done = states.count { it == DownloadState.DONE },
                active = states.count { it == DownloadState.QUEUED || it == DownloadState.DOWNLOADING },
                failed = states.count { it == DownloadState.FAILED },
            )
        }
    }
}

data class CollectionUi(
    val title: String,
    val artist: String?,
    val artistKey: String?,
    val year: Int?,
    val artworkUrl: String?,
    val tracks: List<TrackUi>,
)

class Collection(val ui: CollectionUi, val tracks: List<PlexMetadata>, val fallbackArtworkUrl: String?)

enum class CollectionKind { ALBUM, PLAYLIST, FIVE_STARS }

class CollectionModel(
    private val graph: AppGraph,
    private val scope: CoroutineScope,
    private val kind: CollectionKind,
    private val key: String,
    private val fiveStarsTitle: (jellyfin: Boolean) -> String = { "" },
) {
    private val repository = graph.repository
    private val playback = graph.playback
    private val _state = MutableStateFlow<Load<CollectionUi>>(Load.Loading)
    val state = _state.asStateFlow()
    private val _downloadStatus = MutableStateFlow(DownloadStatus())
    val downloadStatus = _downloadStatus.asStateFlow()
    private val tracks = MutableStateFlow<List<PlexMetadata>>(emptyList())
    private var playable: List<PlayableTrack> = emptyList()

    init {
        load()
        scope.launch {
            val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return@launch
            combine(tracks, graph.downloader.observe(serverId)) { list, rows ->
                DownloadStatus.of(list.map { it.ratingKey }, rows)
            }.collect { _downloadStatus.value = it }
        }
    }

    fun load() {
        _state.value = Load.Loading
        scope.launch {
            _state.value = runCatching {
                val collection = fetch()
                tracks.value = collection.tracks
                playable = repository.playable(collection.tracks, collection.fallbackArtworkUrl)
                Load.Ready(collection.ui)
            }.getOrElse { it.toFailed() }
        }
    }

    private suspend fun fetch(): Collection = coroutineScope {
        val server = repository.endpoint()
        when (kind) {
            CollectionKind.ALBUM -> {
                val album = async { repository.metadata(key) }
                val list = repository.tracksOf(key)
                val a = album.await()
                val art = repository.artworkUrl(server, a?.thumb, HEADER_ART_PX)
                val pop = popularityOf(list)
                Collection(
                    CollectionUi(a?.title.orEmpty(), a?.parentTitle, a?.parentRatingKey, a?.year, art, list.map { it.toTrackUi(it.index, it.originalTitle, pop) }),
                    list,
                    art,
                )
            }
            CollectionKind.PLAYLIST -> {
                val playlist = async { repository.metadata(key) }
                val list = repository.playlistItems(key)
                val p = playlist.await()
                val pop = popularityOf(list)
                Collection(
                    CollectionUi(
                        p?.title.orEmpty(), null, null, null, repository.artworkUrl(server, p?.artwork, HEADER_ART_PX),
                        list.mapIndexed { i, t -> t.toTrackUi(i + 1, t.trackArtist, pop) },
                    ),
                    list,
                    null,
                )
            }
            CollectionKind.FIVE_STARS -> {
                val list = repository.fiveStarTracks()
                runCatching { graph.fiveStars.save(repository.currentServerId(), list.map { it.ratingKey }) }
                val pop = popularityOf(list)
                Collection(
                    CollectionUi(
                        fiveStarsTitle(repository.isJellyfin.first()), null, null, null,
                        list.firstNotNullOfOrNull { repository.artworkUrl(server, it.artwork, HEADER_ART_PX) },
                        list.map { it.toTrackUi(null, listOfNotNull(it.trackArtist, it.parentTitle).joinToString(" · "), pop) },
                    ),
                    list,
                    null,
                )
            }
        }
    }

    private suspend fun fresh(): List<PlayableTrack> =
        runCatching { repository.playable(tracks.value) }.getOrDefault(playable).ifEmpty { playable }

    fun play(trackKey: String?) {
        scope.launch {
            val list = fresh()
            val start = list.indexOfFirst { it.ratingKey == trackKey }.coerceAtLeast(0)
            playback.play(list, start, pickStart = trackKey == null)
        }
    }

    fun shuffle() {
        scope.launch { playback.play(fresh(), shuffle = true) }
    }

    fun playNext(trackKey: String?) {
        scope.launch { playback.playNext(queueable(trackKey)) }
    }

    fun addToQueue(trackKey: String?) {
        scope.launch { playback.addToQueue(queueable(trackKey)) }
    }

    private suspend fun queueable(trackKey: String?): List<PlayableTrack> {
        val list = fresh()
        return if (trackKey == null) list else list.filter { it.ratingKey == trackKey }
    }

    fun download() {
        scope.launch {
            runCatching {
                graph.downloader.download(tracks.value)
                graph.downloader.retryFailed()
            }
        }
    }

    fun removeDownload() {
        scope.launch {
            runCatching { graph.downloader.delete(repository.currentServerId(), tracks.value.map { it.ratingKey }) }
        }
    }
}

enum class ListenTile { SHUFFLE, FIVE_STARS, NEVER_PLAYED }

enum class ListenNotice { SHUFFLE_FROM_DOWNLOADS, FIVE_STARS_FROM_DOWNLOADS, NO_FIVE_STARS, NOTHING, FAILED }

class ListenModel(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val repository = graph.repository
    private val playback = graph.playback

    val online: StateFlow<Boolean> = graph.network.online.stateIn(scope, SharingStarted.Eagerly, graph.network.isOnline())
    val isJellyfin: StateFlow<Boolean> = repository.isJellyfin.stateIn(scope, SharingStarted.Eagerly, false)
    private val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val fiveStarKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations = _stations.asStateFlow()
    private val _starting = MutableStateFlow<String?>(null)
    val starting = _starting.asStateFlow()
    private val _notice = MutableStateFlow<ListenNotice?>(null)
    val notice = _notice.asStateFlow()

    init {
        scope.launch {
            val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return@launch
            launch { graph.downloader.observe(serverId).collect { rows.value = it } }
            launch { graph.fiveStars.observe(serverId).collect { fiveStarKeys.value = it?.keys.orEmpty() } }
            launch { _stations.value = runCatching { repository.stations() }.getOrDefault(emptyList()) }
            val cached = graph.fiveStars.observe(serverId).first()
            if (FiveStarsCache.isStale(cached, System.currentTimeMillis())) {
                runCatching { repository.fiveStarTracks() }.getOrNull()?.let { tracks ->
                    graph.fiveStars.save(serverId, tracks.map { it.ratingKey })
                }
            }
        }
    }

    fun shuffle() = start(ListenTile.SHUFFLE.name) {
        val fromServer = serverOrNull { repository.playable(repository.libraryShuffleTracks()).takeIf { it.isNotEmpty() } }
        if (fromServer != null) {
            playback.play(fromServer)
            return@start null
        }
        if (playDownloads { true }) ListenNotice.SHUFFLE_FROM_DOWNLOADS else ListenNotice.FAILED
    }

    fun fiveStars() = start(ListenTile.FIVE_STARS.name) {
        val fromServer = serverOrNull {
            val tracks = repository.fiveStarTracks()
            runCatching { graph.fiveStars.save(repository.currentServerId(), tracks.map { it.ratingKey }) }
            repository.playable(tracks)
        }
        when {
            fromServer == null -> {
                val keys = fiveStarKeys.value
                if (playDownloads { it.ratingKey in keys }) ListenNotice.FIVE_STARS_FROM_DOWNLOADS else ListenNotice.FAILED
            }
            fromServer.isEmpty() -> ListenNotice.NO_FIVE_STARS
            else -> {
                playback.play(fromServer, shuffle = true)
                null
            }
        }
    }

    fun neverPlayed() = start(ListenTile.NEVER_PLAYED.name) {
        val tracks = repository.playable(repository.neverPlayedTracks())
        if (tracks.isEmpty()) {
            ListenNotice.NOTHING
        } else {
            playback.play(tracks)
            null
        }
    }

    fun station(station: Station) = start(station.key) {
        val tracks = repository.playable(repository.stationTracks(station))
        if (tracks.isEmpty()) {
            ListenNotice.NOTHING
        } else {
            playback.play(tracks)
            null
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private suspend fun playDownloads(keep: (DownloadEntity) -> Boolean): Boolean {
        val mode = graph.settings.loudness.first()
        val ratings = repository.ratingBook()
        val local = withContext(Dispatchers.IO) {
            LibraryShuffle.offline(ratings.withoutOneStarDownloads(rows.value.filter { keep(it) && it.isPlayableOffline() }))
        }.map { it.toPlayable(mode) }
        if (local.isEmpty()) return false
        playback.play(local)
        return true
    }

    private suspend fun <T> serverOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun start(id: String, block: suspend () -> ListenNotice?) {
        if (_starting.value != null) return
        _starting.value = id
        _notice.value = null
        scope.launch {
            _notice.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ListenNotice.FAILED
            }
            _starting.value = null
        }
    }
}

data class SearchResults(
    val query: String,
    val artists: List<TileUi>,
    val albums: List<TileUi>,
    val tracks: List<TrackUi>,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

class SearchModel(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val repository = graph.repository
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _results = MutableStateFlow<Load<SearchResults?>>(Load.Ready(null))
    val results = _results.asStateFlow()
    private val _recent = MutableStateFlow<List<TileUi>>(emptyList())
    val recent = _recent.asStateFlow()
    private var job: Job? = null
    private var found: List<PlexMetadata> = emptyList()

    init {
        scope.launch {
            _recent.value = runCatching {
                val server = repository.endpoint()
                repository.recentAlbums().map { TileUi(it.key, it.title, it.artist, repository.artworkUrl(server, it.thumb, TILE_ART_PX)) }
            }.getOrDefault(emptyList())
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        job?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _results.value = Load.Ready(null)
            return
        }
        job = scope.launch {
            delay(DEBOUNCE_MS)
            _results.value = Load.Loading
            _results.value = try {
                Load.Ready(search(trimmed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toFailed()
            }
        }
    }

    fun retry() = setQuery(_query.value)

    private suspend fun search(query: String): SearchResults {
        val section = repository.currentMusicSection() ?: return SearchResults(query, emptyList(), emptyList(), emptyList())
        val server = repository.endpoint()
        val hubs = repository.search(section.key, query)
        fun items(type: String) = hubs.filter { it.type == type }.flatMap { it.metadata }
        fun tiles(type: String, subtitle: (PlexMetadata) -> String?) = items(type).map {
            TileUi(it.ratingKey, it.title, subtitle(it), repository.artworkUrl(server, it.artwork, TILE_ART_PX))
        }
        found = items("track")
        return SearchResults(
            query = query,
            artists = tiles("artist") { null },
            albums = tiles("album") { it.parentTitle },
            tracks = found.map { it.toTrackUi(null, listOfNotNull(it.trackArtist, it.parentTitle).joinToString(" · ")) },
        )
    }

    private suspend fun playable(trackKey: String): List<PlayableTrack> =
        repository.playable(found.filter { it.ratingKey == trackKey })

    fun play(trackKey: String) {
        scope.launch { runCatching { graph.playback.play(playable(trackKey)) } }
    }

    fun playNext(trackKey: String) {
        scope.launch { runCatching { graph.playback.playNext(playable(trackKey)) } }
    }

    fun addToQueue(trackKey: String) {
        scope.launch { runCatching { graph.playback.addToQueue(playable(trackKey)) } }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}

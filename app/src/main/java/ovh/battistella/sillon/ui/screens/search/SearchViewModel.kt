package ovh.battistella.sillon.ui.screens.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.genres.GenreRepository
import ovh.battistella.sillon.data.genres.GenreView
import ovh.battistella.sillon.data.genres.Genres
import ovh.battistella.sillon.data.home.CachedHome
import ovh.battistella.sillon.data.home.HomeAlbum
import ovh.battistella.sillon.data.home.SearchHome
import ovh.battistella.sillon.data.home.SearchHomeRepository
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.wear.SleeveWearRepository
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.ui.screens.library.TILE_ART_PX
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.screens.library.TrackUi
import ovh.battistella.sillon.ui.screens.library.toTrackUi
import java.io.File
import javax.inject.Inject

data class SearchResults(
    val artists: List<TileUi>,
    val albums: List<TileUi>,
    val tracks: List<TrackUi>,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Failed(val message: String) : SearchState
    data class Ready(val results: SearchResults) : SearchState
}

/** The rows shown while nothing is typed. [loading] is true only while there is nothing to show yet. */
data class SearchHomeUi(
    val recent: List<TileUi> = emptyList(),
    val classics: List<TileUi> = emptyList(),
    val online: Boolean = true,
    val loading: Boolean = false,
    val classicsNeedWear: Boolean = false,
    val genres: List<GenreTileUi> = emptyList(),
    val moreGenres: Boolean = false,
) {
    val isEmpty: Boolean get() = recent.isEmpty() && classics.isEmpty() && genres.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
    private val searchHome: SearchHomeRepository,
    private val genres: GenreRepository,
    private val downloads: DownloadManager,
    private val settings: SettingsStore,
    network: NetworkMonitor,
    sleeveWear: SleeveWearRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val retries = MutableStateFlow(0)

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    val nowPlaying = playback.state

    private var trackResults: List<PlexMetadata> = emptyList()

    val online: StateFlow<Boolean> = network.online.stateIn(viewModelScope, SharingStarted.Eagerly, network.isOnline())

    private val refreshing = MutableStateFlow(false)

    /** Downloaded albums, with the cover saved next to them. */
    private val onPhone: Flow<Map<String, String?>> = repository.session
        .map { (it as? Session.Ready)?.server?.machineId }
        .distinctUntilChanged()
        .flatMapLatest { serverId -> if (serverId == null) flowOf<List<DownloadEntity>>(emptyList()) else downloads.observe(serverId) }
        .map { rows -> downloadedAlbums(rows) }
        .flowOn(Dispatchers.IO)

    val home: StateFlow<SearchHomeUi> = combine(
        combine(searchHome.cached, onPhone, online, refreshing, settings.sleeveWear) { cached, phone, isOnline, busy, wearOn ->
            homeUi(cached, phone, isOnline, busy, wearOn)
        },
        genres.view,
    ) { rows, view -> withGenres(rows, view) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchHomeUi(loading = true))

    init {
        viewModelScope.launch {
            online.filter { it }.collect { refreshHome() }
        }
        viewModelScope.launch {
            // Listening keeps sleeve wear counting; each new count may change the classics.
            sleeveWear.levels.distinctUntilChanged().collect { runCatching { searchHome.refreshClassics() } }
        }
        viewModelScope.launch {
            combine(_query, retries) { q, _ -> q }.collectLatest { raw ->
                val q = raw.trim()
                if (q.length < MIN_QUERY_LENGTH) {
                    _state.value = SearchState.Idle
                    return@collectLatest
                }
                delay(DEBOUNCE_MS)
                _state.value = SearchState.Loading
                _state.value = runCatching { SearchState.Ready(search(q)) }
                    .getOrElse { SearchState.Failed(it.message ?: it.javaClass.simpleName) }
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() {
        retries.value++
    }

    fun playTrack(key: String) {
        viewModelScope.launch {
            val tracks = runCatching { repository.playable(trackResults) }.getOrDefault(emptyList())
            val start = tracks.indexOfFirst { it.ratingKey == key }.coerceAtLeast(0)
            playback.play(tracks, start)
        }
    }

    /** A long press: plays the album from the server, or from the downloads without a connection. */
    fun playAlbum(key: String) {
        viewModelScope.launch {
            val fromServer = if (online.value) {
                runCatching { repository.playable(repository.tracksOf(key)) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val tracks = fromServer.ifEmpty { downloadedTracks(key) }
            if (tracks.isNotEmpty()) playback.play(tracks, 0, pickStart = true)
        }
    }

    private suspend fun refreshHome() {
        refreshing.value = true
        try {
            runCatching { searchHome.refreshRecent() }
            runCatching { searchHome.refreshClassics() }
            runCatching { genres.refresh() }
        } finally {
            refreshing.value = false
        }
    }

    private suspend fun downloadedTracks(albumKey: String): List<PlayableTrack> {
        val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return emptyList()
        val mode = settings.loudness.first()
        return downloads.observe(serverId).first()
            .filter { it.albumKey == albumKey && it.isPlayableOffline() }
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
            .map { it.toPlayable(mode) }
    }

    private suspend fun homeUi(
        cached: CachedHome?,
        phone: Map<String, String?>,
        isOnline: Boolean,
        busy: Boolean,
        wearOn: Boolean,
    ): SearchHomeUi {
        val server = runCatching { repository.endpoint() }.getOrNull()
        val hidden = searchHome.hidden(cached?.sealed.orEmpty())
        val (recent, classics) = SearchHome.rows(cached, hidden)
        fun tiles(albums: List<HomeAlbum>) =
            (if (isOnline) albums else SearchHome.offline(albums, phone.keys)).map { tile(it, server, phone) }
        val recentTiles = tiles(recent)
        val classicTiles = tiles(classics)
        return SearchHomeUi(
            recent = recentTiles,
            classics = classicTiles,
            online = isOnline,
            loading = busy && recentTiles.isEmpty() && classicTiles.isEmpty(),
            classicsNeedWear = !wearOn && classics.isEmpty() && searchHome.classicKeys() == null,
        )
    }

    private fun withGenres(rows: SearchHomeUi, view: GenreView): SearchHomeUi {
        val tiles = view.genres.map { it.toTile() }.withGridColors()
        val shown = rows.copy(genres = tiles.take(Genres.VISIBLE), moreGenres = tiles.size > Genres.VISIBLE)
        // The spinner is for a first visit with nothing to show yet; genres count as something to show.
        return if (tiles.isEmpty()) shown else shown.copy(loading = false)
    }

    private fun tile(album: HomeAlbum, server: ServerEndpoint?, phone: Map<String, String?>): TileUi {
        val local = phone[album.key]?.let { Uri.fromFile(File(it)).toString() }
        val remote = server?.let { repository.artworkUrl(it, album.thumb, TILE_ART_PX) }
        return TileUi(album.key, album.title, album.artist, local ?: remote)
    }

    private suspend fun search(query: String): SearchResults {
        val section = repository.currentMusicSection() ?: return SearchResults(emptyList(), emptyList(), emptyList())
        val server = repository.endpoint()
        val hubs = repository.search(section.key, query)
        fun items(type: String) = hubs.filter { it.type == type }.flatMap { it.metadata }
        fun tiles(type: String, subtitle: (PlexMetadata) -> String?) = items(type).map {
            TileUi(it.ratingKey, it.title, subtitle(it), repository.artworkUrl(server, it.thumb, TILE_ART_PX))
        }
        trackResults = items("track")
        return SearchResults(
            artists = tiles("artist") { null },
            albums = tiles("album") { it.parentTitle },
            tracks = trackResults.map {
                it.toTrackUi(null, listOfNotNull(it.trackArtist, it.parentTitle).joinToString(" · "))
            },
        )
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEBOUNCE_MS = 350L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Albums with at least one track that plays without a connection, with their saved cover if any. */
internal fun downloadedAlbums(rows: List<DownloadEntity>): Map<String, String?> =
    rows.filter { it.albumKey != null && it.isPlayableOffline() }
        .groupBy { it.albumKey!! }
        .mapValues { (_, tracks) -> tracks.firstNotNullOfOrNull { it.artworkPath?.takeIf { p -> File(p).isFile } } }

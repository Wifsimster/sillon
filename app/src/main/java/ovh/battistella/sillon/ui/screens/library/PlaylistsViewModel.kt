package ovh.battistella.sillon.ui.screens.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
import ovh.battistella.sillon.ui.navigation.Routes
import javax.inject.Inject

/** All audio playlists on the server. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    /** On Jellyfin, favourites stand in for 5-star ratings, and the card says so. */
    val isJellyfin = repository.isJellyfin.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** True while the 5-star tracks are being fetched for [shuffleFiveStars]. */
    private val _startingFiveStars = MutableStateFlow(false)
    val startingFiveStars = _startingFiveStars.asStateFlow()

    /** The library's radios, shown under the 5-star card. */
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations = _stations.asStateFlow()

    /** Key of the radio being started, for a spinner on its card. */
    private val _startingStation = MutableStateFlow<String?>(null)
    val startingStation = _startingStation.asStateFlow()

    fun playStation(station: Station) {
        if (_startingStation.value != null) return
        _startingStation.value = station.key
        viewModelScope.launch {
            runCatching { repository.playable(repository.stationTracks(station)) }
                .onSuccess { if (it.isNotEmpty()) playback.play(it) }
            _startingStation.value = null
        }
    }

    /** One tap: every 5-star track of the library, shuffled. */
    fun shuffleFiveStars() {
        if (_startingFiveStars.value) return
        _startingFiveStars.value = true
        viewModelScope.launch {
            runCatching { repository.playable(repository.fiveStarTracks()) }
                .onSuccess { playback.play(it, shuffle = true) }
            _startingFiveStars.value = false
        }
    }

    private val _state = MutableStateFlow<Load<List<TileUi>>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _stations.value = runCatching { repository.stations() }.getOrDefault(emptyList())
        }
        viewModelScope.launch {
            _state.value = runCatching {
                val server = repository.endpoint()
                Load.Ready(
                    repository.playlists().map {
                        TileUi(
                            key = it.ratingKey,
                            title = it.title,
                            subtitle = null,
                            artworkUrl = repository.artworkUrl(server, it.artwork, TILE_ART_PX),
                            trackCount = it.leafCount,
                        )
                    },
                )
            }.getOrElse { it.toFailed() }
        }
    }
}

/** One playlist's tracks, shown like an album. */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlexRepository,
    playback: PlaybackConnection,
    downloads: DownloadManager,
) : TrackCollectionViewModel(repository, playback, downloads) {

    private val key: String = checkNotNull(savedStateHandle[Routes.ARG_KEY])

    init {
        start()
    }

    override suspend fun fetch(): TrackCollection = coroutineScope {
        val server = repository.endpoint()
        val playlist = async { repository.metadata(key) }
        val tracks = repository.playlistItems(key)
        val p = playlist.await()
        TrackCollection(
            ui = AlbumUi(
                title = p?.title.orEmpty(),
                artist = null,
                year = null,
                artworkUrl = repository.artworkUrl(server, p?.artwork, HEADER_ART_PX),
                tracks = popularityOf(tracks).let { pop -> tracks.mapIndexed { i, t -> t.toTrackUi(i + 1, t.trackArtist, pop) } },
            ),
            tracks = tracks,
            fallbackArtworkUrl = null,
        )
    }
}

/** The tracks rated 5 stars, shown and played like a playlist. */
@HiltViewModel
class FiveStarsViewModel @Inject constructor(
    private val repository: PlexRepository,
    playback: PlaybackConnection,
    downloads: DownloadManager,
    @ApplicationContext private val context: Context,
) : TrackCollectionViewModel(repository, playback, downloads) {

    init {
        start()
    }

    override suspend fun fetch(): TrackCollection {
        val tracks = repository.fiveStarTracks()
        val pop = popularityOf(tracks)
        val server = repository.endpoint()
        return TrackCollection(
            ui = AlbumUi(
                title = context.getString(
                    if (repository.isJellyfin.first()) R.string.five_stars_jellyfin else R.string.five_stars,
                ),
                artist = null,
                year = null,
                // The cover of a 5-star track rather than an empty square.
                artworkUrl = tracks.firstNotNullOfOrNull { repository.artworkUrl(server, it.artwork, HEADER_ART_PX) },
                tracks = tracks.map { it.toTrackUi(null, listOfNotNull(it.trackArtist, it.parentTitle).joinToString(" · "), pop) },
            ),
            tracks = tracks,
            fallbackArtworkUrl = null,
        )
    }
}

package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
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
) : ViewModel() {

    private val _state = MutableStateFlow<Load<List<TileUi>>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
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
                tracks = tracks.mapIndexed { i, t -> TrackUi(t.ratingKey, i + 1, t.title, t.trackArtist, t.duration) },
            ),
            tracks = tracks,
            fallbackArtworkUrl = null,
        )
    }
}

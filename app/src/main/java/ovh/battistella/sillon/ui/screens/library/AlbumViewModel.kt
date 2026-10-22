package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.wear.SleeveWearRepository
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.AlbumSides
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.Side
import ovh.battistella.sillon.ui.components.Wear
import ovh.battistella.sillon.ui.navigation.Routes
import javax.inject.Inject

data class AlbumUi(
    val title: String,
    val artist: String?,
    val year: Int?,
    val artworkUrl: String?,
    val tracks: List<TrackUi>,
    val sides: List<Side> = emptyList(),
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlexRepository,
    playback: PlaybackConnection,
    downloads: DownloadManager,
    sleeveWear: SleeveWearRepository,
    private val settings: SettingsStore,
) : TrackCollectionViewModel(repository, playback, downloads) {
    val key: String = checkNotNull(savedStateHandle[Routes.ARG_KEY])

    val wear: StateFlow<Wear?> = sleeveWear.levels
        .map { Wear.of(it, key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val listenBySide = settings.listenBySide.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var sides: List<Side> = emptyList()

    fun setListenBySide(value: Boolean) {
        viewModelScope.launch { settings.setListenBySide(value) }
    }

    override suspend fun sidesForPlayback(): List<Side> =
        if (settings.listenBySide.first()) sides else emptyList()

    init {
        start()
    }

    override suspend fun fetch(): TrackCollection = coroutineScope {
        val server = repository.endpoint()
        val album = async { repository.metadata(key) }
        val tracks = repository.tracksOf(key)
        val a = album.await()
        val albumArt = repository.artworkUrl(server, a?.thumb, HEADER_ART_PX)
        TrackCollection(
            ui = AlbumUi(
                title = a?.title.orEmpty(),
                artist = a?.parentTitle,
                year = a?.year,
                artworkUrl = albumArt,
                tracks = popularityOf(tracks).let { pop -> tracks.map { it.toTrackUi(it.index, it.originalTitle, pop) } },
                sides = AlbumSides.of(tracks).also { sides = it },
            ),
            tracks = tracks,
            fallbackArtworkUrl = albumArt,
        )
    }
}

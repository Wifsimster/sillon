package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.navigation.Routes
import javax.inject.Inject

data class AlbumUi(
    val title: String,
    val artist: String?,
    val year: Int?,
    val artworkUrl: String?,
    val tracks: List<TrackUi>,
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
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
            ),
            tracks = tracks,
            fallbackArtworkUrl = albumArt,
        )
    }
}

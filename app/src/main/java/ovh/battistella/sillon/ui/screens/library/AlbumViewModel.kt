package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
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
    private val playback: PlaybackConnection,
) : ViewModel() {

    private val key: String = checkNotNull(savedStateHandle[Routes.ARG_KEY])

    private val _state = MutableStateFlow<Load<AlbumUi>>(Load.Loading)
    val state = _state.asStateFlow()

    /** The album's tracks resolved for the player, in disc order. */
    private var playable: List<PlayableTrack> = emptyList()

    val nowPlaying = playback.state

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val server = repository.endpoint()
                val album = async { repository.metadata(key) }
                val tracks = repository.children(key)
                val a = album.await()
                val albumArt = repository.artworkUrl(server, a?.thumb, HEADER_ART_PX)
                playable = tracks.mapNotNull { track ->
                    val url = repository.streamUrl(server, track) ?: return@mapNotNull null
                    PlayableTrack(
                        ratingKey = track.ratingKey,
                        streamUrl = url,
                        title = track.title,
                        artist = track.trackArtist,
                        album = track.parentTitle ?: a?.title,
                        trackNumber = track.index,
                        durationMs = track.duration,
                        artworkUrl = repository.artworkUrl(server, track.artwork, HEADER_ART_PX) ?: albumArt,
                    )
                }
                Load.Ready(
                    AlbumUi(
                        title = a?.title.orEmpty(),
                        artist = a?.parentTitle,
                        year = a?.year,
                        artworkUrl = albumArt,
                        tracks = tracks.map {
                            TrackUi(it.ratingKey, it.index, it.title, it.originalTitle, it.duration)
                        },
                    ),
                )
            }.getOrElse { it.toFailed() }
        }
    }

    fun play(trackKey: String? = null) {
        val start = playable.indexOfFirst { it.ratingKey == trackKey }.coerceAtLeast(0)
        playback.play(playable, start)
    }

    fun shuffle() = playback.play(playable, shuffle = true)
}

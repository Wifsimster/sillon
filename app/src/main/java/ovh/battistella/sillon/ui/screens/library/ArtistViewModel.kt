package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.wear.SleeveWearRepository
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
import ovh.battistella.sillon.ui.navigation.Routes
import javax.inject.Inject

data class ArtistUi(val name: String, val artworkUrl: String?, val albums: List<TileUi>, val years: List<Int> = emptyList())

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
    sleeveWear: SleeveWearRepository,
) : ViewModel() {
    val key: String = checkNotNull(savedStateHandle[Routes.ARG_KEY])

    val wear: StateFlow<Map<String, Int>> =
        sleeveWear.levels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _state = MutableStateFlow<Load<ArtistUi>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    private val _startingRadio = MutableStateFlow(false)
    val startingRadio = _startingRadio.asStateFlow()

    fun playRadio() {
        if (_startingRadio.value) return
        _startingRadio.value = true
        viewModelScope.launch {
            runCatching { repository.playable(repository.artistRadioTracks(key)) }
                .onSuccess { if (it.isNotEmpty()) playback.play(it) }
            _startingRadio.value = false
        }
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val server = repository.endpoint()
                val artist = async { repository.metadata(key) }
                val records = repository.albumsOf(key).sortedByDescending { it.year ?: 0 }
                val albums = records
                    .map {
                        TileUi(
                            it.ratingKey,
                            it.title,
                            it.year?.toString(),
                            repository.artworkUrl(server, it.thumb, TILE_ART_PX),
                        )
                    }
                val a = artist.await()
                Load.Ready(
                    ArtistUi(
                        name = a?.title.orEmpty(),
                        artworkUrl = repository.artworkUrl(server, a?.thumb, HEADER_ART_PX),
                        albums = albums,
                        years = records.mapNotNull { it.year },
                    ),
                )
            }.getOrElse { it.toFailed() }
        }
    }
}

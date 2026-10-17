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
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
import ovh.battistella.sillon.ui.navigation.Routes
import javax.inject.Inject

data class ArtistUi(val name: String, val artworkUrl: String?, val albums: List<TileUi>)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlexRepository,
) : ViewModel() {

    private val key: String = checkNotNull(savedStateHandle[Routes.ARG_KEY])

    private val _state = MutableStateFlow<Load<ArtistUi>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val server = repository.endpoint()
                val artist = async { repository.metadata(key) }
                val albums = repository.children(key)
                    .sortedByDescending { it.year ?: 0 }
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
                    ),
                )
            }.getOrElse { it.toFailed() }
        }
    }
}

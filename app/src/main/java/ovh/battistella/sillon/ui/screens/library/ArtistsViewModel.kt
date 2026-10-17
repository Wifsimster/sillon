package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
import javax.inject.Inject

/** Null artists means the server has no music library at all. */
data class ArtistsUi(val sectionTitle: String?, val artists: List<TileUi>?)

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val repository: PlexRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Load<ArtistsUi>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                // TODO: let the user pick when the server has several music libraries.
                val section = repository.musicSections().firstOrNull()
                    ?: return@runCatching Load.Ready(ArtistsUi(null, null))
                val server = repository.endpoint()
                val artists = repository.artists(section.key).map {
                    TileUi(it.ratingKey, it.title, null, repository.artworkUrl(server, it.thumb, TILE_ART_PX))
                }
                Load.Ready(ArtistsUi(section.title, artists))
            }.getOrElse { it.toFailed() }
        }
    }

    fun changeServer() {
        viewModelScope.launch { repository.changeServer() }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}

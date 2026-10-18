package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
import javax.inject.Inject

data class SectionGridUi(val sectionTitle: String?, val tiles: List<TileUi>?)

abstract class SectionGridViewModel(
    private val repository: PlexRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<Load<SectionGridUi>>(Load.Loading)
    val state = _state.asStateFlow()

    private var job: Job? = null

    protected abstract suspend fun fetch(sectionKey: String): List<PlexMetadata>

    protected open fun subtitle(item: PlexMetadata): String? = null

    protected fun start() {
        load()
        viewModelScope.launch { repository.musicSectionKey.drop(1).collect { load() } }
    }

    fun load() {
        job?.cancel()
        _state.value = Load.Loading
        job = viewModelScope.launch {
            _state.value = runCatching {
                val section = repository.currentMusicSection()
                    ?: return@runCatching Load.Ready(SectionGridUi(null, null))
                val server = repository.endpoint()
                val tiles = fetch(section.key).map {
                    TileUi(it.ratingKey, it.title, subtitle(it), repository.artworkUrl(server, it.thumb, TILE_ART_PX))
                }
                Load.Ready(SectionGridUi(section.title, tiles))
            }.getOrElse { it.toFailed() }
        }
    }
}

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val repository: PlexRepository,
) : SectionGridViewModel(repository) {
    init {
        start()
    }

    override suspend fun fetch(sectionKey: String) = repository.artists(sectionKey)
}

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: PlexRepository,
) : SectionGridViewModel(repository) {
    init {
        start()
    }

    override suspend fun fetch(sectionKey: String) = repository.albums(sectionKey)

    override fun subtitle(item: PlexMetadata): String? = item.parentTitle
}

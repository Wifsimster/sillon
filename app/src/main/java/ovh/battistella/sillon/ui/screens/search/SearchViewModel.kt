package ovh.battistella.sillon.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.screens.library.TILE_ART_PX
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.screens.library.TrackUi
import javax.inject.Inject

data class SearchResults(
    val artists: List<TileUi>,
    val albums: List<TileUi>,
    val tracks: List<TrackUi>,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

sealed interface SearchState {
    /** Fewer than [MIN_QUERY_LENGTH] characters typed. */
    data object Idle : SearchState
    data object Loading : SearchState
    data class Failed(val message: String) : SearchState
    data class Ready(val results: SearchResults) : SearchState
}

/** Searches the current music library as the user types. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /** Bumped by [retry] to run the same query again. */
    private val retries = MutableStateFlow(0)

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    val nowPlaying = playback.state

    /** Track results as returned by Plex, kept to build the play queue. */
    private var trackResults: List<PlexMetadata> = emptyList()

    init {
        viewModelScope.launch {
            // collectLatest cancels the pending search when another key is typed.
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

    /** Play the track results, starting with the one tapped. */
    fun playTrack(key: String) {
        viewModelScope.launch {
            val tracks = runCatching { repository.playable(trackResults) }.getOrDefault(emptyList())
            val start = tracks.indexOfFirst { it.ratingKey == key }.coerceAtLeast(0)
            playback.play(tracks, start)
        }
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
                TrackUi(it.ratingKey, null, it.title, listOfNotNull(it.trackArtist, it.parentTitle).joinToString(" · "), it.duration)
            },
        )
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEBOUNCE_MS = 350L
    }
}

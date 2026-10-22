package ovh.battistella.sillon.ui.screens.search

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.genres.Genre
import ovh.battistella.sillon.data.genres.GenreRepository
import ovh.battistella.sillon.data.genres.GenreView
import ovh.battistella.sillon.data.genres.Genres
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.navigation.Routes
import ovh.battistella.sillon.ui.screens.library.TILE_ART_PX
import ovh.battistella.sillon.ui.screens.library.TileUi
import java.io.File
import javax.inject.Inject

/** A genre's page. [found] is false when the genre is gone (renamed on the server, or offline without its albums). */
data class GenrePageUi(
    val label: String = "",
    val artists: List<TileUi> = emptyList(),
    val albums: List<TileUi> = emptyList(),
    val online: Boolean = true,
    val found: Boolean = true,
    val loading: Boolean = true,
)

data class GenreListUi(
    val genres: List<GenreTileUi> = emptyList(),
    val online: Boolean = true,
    val loading: Boolean = true,
)

/** The tiles of a genre page: covers from the phone first, then from the server. */
internal fun genrePage(genre: Genre?, view: GenreView, artwork: (String?) -> String?): GenrePageUi {
    if (genre == null) return GenrePageUi(online = view.online, found = false, loading = false)
    val local = { key: String -> view.onPhone[key]?.let { Uri.fromFile(File(it)).toString() } }
    return GenrePageUi(
        label = genre.label,
        // An artist's page needs the server: offline, the row stays hidden.
        artists = if (view.online) Genres.artists(genre).map { TileUi(it.key, it.name, null, artwork(it.thumb)) } else emptyList(),
        albums = genre.albums.map { TileUi(it.key, it.title, it.artist, local(it.key) ?: artwork(it.thumb)) },
        online = view.online,
        loading = false,
    )
}

@HiltViewModel
class GenreViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val genres: GenreRepository,
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {
    val key: String = checkNotNull(savedState[Routes.ARG_KEY])

    private val _shuffling = MutableStateFlow(false)
    val shuffling = _shuffling.asStateFlow()

    private val _nothingToPlay = MutableStateFlow(false)
    val nothingToPlay = _nothingToPlay.asStateFlow()

    val state: StateFlow<GenrePageUi> = genres.view
        .map { view ->
            val server = runCatching { repository.endpoint() }.getOrNull()
            val genre = view.genres.firstOrNull { it.key == key }
            if (genre == null && !view.known && view.online) GenrePageUi() else genrePage(genre, view) { art(server, it) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GenrePageUi())

    init {
        viewModelScope.launch { runCatching { genres.refresh() } }
    }

    /** Plays the genre in a random order; a surprise not revealed yet is never part of it. */
    fun shuffle() {
        if (_shuffling.value) return
        viewModelScope.launch {
            _shuffling.value = true
            _nothingToPlay.value = false
            try {
                val view = genres.view.first()
                val genre = view.genres.firstOrNull { it.key == key }
                val tracks = genre?.let { runCatching { genres.shuffle(it, view.online) }.getOrDefault(emptyList()) }.orEmpty()
                if (tracks.isEmpty()) _nothingToPlay.value = true else playback.play(tracks, 0, pickStart = true)
            } finally {
                _shuffling.value = false
            }
        }
    }

    private fun art(server: ServerEndpoint?, thumb: String?): String? =
        server?.let { repository.artworkUrl(it, thumb, TILE_ART_PX) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@HiltViewModel
class GenresViewModel @Inject constructor(genres: GenreRepository) : ViewModel() {
    val state: StateFlow<GenreListUi> = genres.view
        .map { GenreListUi(it.genres.map { g -> g.toTile() }.withGridColors(),it.online, loading = it.genres.isEmpty() && !it.known && it.online) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), GenreListUi())
}

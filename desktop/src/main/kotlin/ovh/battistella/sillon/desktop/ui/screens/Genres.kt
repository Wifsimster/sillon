package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.genres.Genre
import ovh.battistella.sillon.data.genres.GenreAlbum
import ovh.battistella.sillon.data.genres.Genres
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.desktop.AppGraph
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.Screen
import ovh.battistella.sillon.desktop.ui.StackEntry
import ovh.battistella.sillon.desktop.ui.components.Load
import ovh.battistella.sillon.desktop.ui.components.LoadContent
import ovh.battistella.sillon.desktop.ui.components.Message
import ovh.battistella.sillon.desktop.ui.components.Tile
import ovh.battistella.sillon.desktop.ui.components.TileGrid
import ovh.battistella.sillon.desktop.ui.components.TileUi
import ovh.battistella.sillon.desktop.ui.components.toFailed

class GenreLibrary(private val repository: PlexRepository) {
    private val lock = Mutex()
    private var owner: String? = null
    private var albums: List<GenreAlbum>? = null

    suspend fun genres(refresh: Boolean = false): List<Genre> = Genres.group(albums(refresh))

    suspend fun albums(refresh: Boolean = false): List<GenreAlbum> = lock.withLock {
        val server = repository.currentServerId() + "|" + repository.currentMusicSection()?.key
        val cached = albums.takeIf { owner == server && !refresh }
        cached ?: repository.genreAlbums().also {
            owner = server
            albums = it
        }
    }

    suspend fun shelfAlbums(): List<ShelfAlbum> {
        val section = repository.currentMusicSection() ?: return emptyList()
        val server = repository.currentServerId() + "|" + section.key
        val known = lock.withLock { years.takeIf { yearsOwner == server } }
        val byKey = known ?: repository.albums(section.key).associate { it.ratingKey to it.year }.also {
            lock.withLock {
                yearsOwner = server
                years = it
            }
        }
        return albums().map { ShelfAlbum(it.key, it.title, it.artist, it.thumb, it.genres, byKey[it.key]) }
    }

    private var yearsOwner: String? = null
    private var years: Map<String, Int?>? = null
}

class GenresModel(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<Load<List<Genre>>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load(refresh = false)
    }

    fun load(refresh: Boolean = true) {
        _state.value = Load.Loading
        scope.launch {
            _state.value = try {
                Load.Ready(graph.genres.genres(refresh))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toFailed()
            }
        }
    }
}

data class GenreUi(val genre: Genre, val albums: List<TileUi>, val artists: List<TileUi>)

class GenreModel(private val graph: AppGraph, private val scope: CoroutineScope, private val key: String) {
    private val repository = graph.repository
    private val _state = MutableStateFlow<Load<GenreUi?>>(Load.Loading)
    val state = _state.asStateFlow()
    private val _starting = MutableStateFlow(false)
    val starting = _starting.asStateFlow()
    private val _nothing = MutableStateFlow(false)
    val nothing = _nothing.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        scope.launch {
            _state.value = try {
                val genre = graph.genres.genres().firstOrNull { it.key == key }
                val server = repository.endpoint()
                Load.Ready(
                    genre?.let { g ->
                        GenreUi(
                            genre = g,
                            albums = g.albums.map { TileUi(it.key, it.title, it.artist, repository.artworkUrl(server, it.thumb, TILE_ART_PX)) },
                            artists = Genres.artists(g).map { TileUi(it.key, it.name, null, repository.artworkUrl(server, it.thumb, TILE_ART_PX)) },
                        )
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toFailed()
            }
        }
    }

    fun shuffle() {
        val genre = (_state.value as? Load.Ready)?.value?.genre ?: return
        if (_starting.value) return
        _starting.value = true
        _nothing.value = false
        scope.launch {
            val tracks = runCatching {
                val drawn = coroutineScope {
                    Genres.shuffleAlbums(genre).map { album -> async { runCatching { repository.tracksOf(album.key) }.getOrDefault(emptyList()) } }
                        .awaitAll().flatten()
                }
                repository.playable(repository.withoutOneStar(drawn).shuffled().take(Genres.SHUFFLE_TRACKS))
            }.getOrDefault(emptyList())
            if (tracks.isEmpty()) _nothing.value = true else graph.playback.play(tracks)
            _starting.value = false
        }
    }
}

@Composable
fun GenresScreen(app: AppState, entry: StackEntry) {
    val strings = LocalStrings.current
    val model = entry.model("genres") { scope -> GenresModel(app.graph, scope) }
    val state by model.state.collectAsState()
    LoadContent(state, { model.load() }) { genres ->
        if (genres.isEmpty()) {
            Message(strings.genresEmpty, action = strings.retry to { model.load() })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(genres, key = { it.key }) { genre ->
                    Box(
                        Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.medium)
                            .background(Color(Genres.colorOf(genre.key)))
                            .clickable { app.open(Screen.Genre(genre.key)) }
                            .padding(16.dp),
                    ) {
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Text(genre.label, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(strings.albumCount(genre.albums.size), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreScreen(app: AppState, entry: StackEntry, key: String) {
    val strings = LocalStrings.current
    val model = entry.model("genre") { scope -> GenreModel(app.graph, scope, key) }
    val state by model.state.collectAsState()
    val starting by model.starting.collectAsState()
    val nothing by model.nothing.collectAsState()
    val wear by app.graph.sleeveWear.levels.collectAsState(emptyMap())
    LoadContent(state, model::load) { ui ->
        if (ui == null) {
            Message(strings.genresEmpty)
            return@LoadContent
        }
        TileGrid(ui.albums, onClick = { app.open(Screen.Album(it.key)) }, wear = wear) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.genre.label, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(strings.albumCount(ui.albums.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = model::shuffle, enabled = !starting) {
                    if (starting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(strings.genreShuffle)
                }
                if (nothing) Text(strings.genreNothing, color = MaterialTheme.colorScheme.error)
                if (ui.artists.isNotEmpty()) {
                    Text(strings.artists, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.artists, key = { it.key }) { artist ->
                            Tile(artist, round = true, modifier = Modifier.width(132.dp)) { app.open(Screen.Artist(artist.key)) }
                        }
                    }
                    Text(strings.albums, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

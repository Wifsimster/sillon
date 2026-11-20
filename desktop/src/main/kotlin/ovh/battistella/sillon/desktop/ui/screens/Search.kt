package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.Screen
import ovh.battistella.sillon.desktop.ui.StackEntry
import ovh.battistella.sillon.desktop.ui.components.LoadContent
import ovh.battistella.sillon.desktop.ui.components.Message
import ovh.battistella.sillon.desktop.ui.components.Tile
import ovh.battistella.sillon.desktop.ui.components.TileUi
import ovh.battistella.sillon.desktop.ui.components.TrackListHeader
import ovh.battistella.sillon.desktop.ui.components.TrackRow
import ovh.battistella.sillon.desktop.ui.components.decodeTrackOrder
import ovh.battistella.sillon.desktop.ui.components.encode
import ovh.battistella.sillon.desktop.ui.components.sortedBy
import ovh.battistella.sillon.desktop.ui.components.Wear

@Composable
fun SearchScreen(app: AppState, entry: StackEntry) {
    val strings = LocalStrings.current
    val model = entry.model("search") { scope -> SearchModel(app.graph, scope) }
    val query by model.query.collectAsState()
    val results by model.results.collectAsState()
    val recent by model.recent.collectAsState()
    val shelves by model.shelves.collectAsState()
    val startingShelf by model.startingShelf.collectAsState()
    val wear by app.graph.sleeveWear.levels.collectAsState(emptyMap())
    val now by app.graph.playback.state.collectAsState()
    val ratings by app.graph.playback.ratings.collectAsState()
    val trackOrder by remember { app.graph.prefs.trackSort(SEARCH_SORT).map(::decodeTrackOrder) }.collectAsState(null)
    val saving = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { app.typing = false } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = model::setQuery,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)
                .focusRequester(focus)
                .onFocusChanged { app.typing = it.isFocused },
            placeholder = { Text(strings.searchHint) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { model.setQuery("") }) { Icon(Icons.Filled.Close, strings.searchClear) }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        LoadContent(results, model::retry) { found ->
            when {
                found == null -> if (recent.isEmpty() && shelves.isEmpty()) {
                    Message(strings.searchStart)
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (recent.isNotEmpty()) {
                            item(key = "recent") { TileRow(strings.searchRecent, recent, round = false, wear = wear) { app.open(Screen.Album(it.key)) } }
                        }
                        items(shelves, key = { it.shelf.id }) { row ->
                            TileRow(
                                title = strings.shelfTitle(row.shelf.kind, row.shelf.label),
                                tiles = row.tiles,
                                round = false,
                                wear = wear,
                                onShuffle = { model.playShelf(row.shelf) },
                                shuffling = startingShelf == row.shelf.id,
                            ) { app.open(Screen.Album(it.key)) }
                        }
                    }
                }
                found.isEmpty -> Message(strings.searchNoResults(found.query))
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (found.artists.isNotEmpty()) {
                        item { TileRow(strings.artists, found.artists, round = true) { app.open(Screen.Artist(it.key)) } }
                    }
                    if (found.albums.isNotEmpty()) {
                        item { TileRow(strings.albums, found.albums, round = false) { app.open(Screen.Album(it.key)) } }
                    }
                    if (found.tracks.isNotEmpty()) {
                        item { Text(strings.tracks, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                        val rated = found.tracks.map { it.copy(stars = ratings.book.starsOf(it.key, it.rating)) }
                        val showPopularity = rated.any { it.popularity != null }
                        val showPlays = rated.any { it.plays != null }
                        item {
                            TrackListHeader(
                                order = trackOrder,
                                onOrder = { next -> saving.launch { app.graph.prefs.setTrackSort(SEARCH_SORT, next?.encode()) } },
                                showPopularity = showPopularity,
                                showPlays = showPlays,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(rated.sortedBy(trackOrder), key = { it.key }) { track ->
                            TrackRow(
                                track = track,
                                playing = now.current?.ratingKey == track.key,
                                skipped = false,
                                onPlay = { model.play(track.key) },
                                onPlayNext = { model.playNext(track.key) },
                                onAddToQueue = { model.addToQueue(track.key) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                showPopularity = showPopularity,
                                showPlays = showPlays,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileRow(
    title: String,
    tiles: List<TileUi>,
    round: Boolean,
    wear: Map<String, Int> = emptyMap(),
    onShuffle: (() -> Unit)? = null,
    shuffling: Boolean = false,
    onClick: (TileUi) -> Unit,
) {
    val strings = LocalStrings.current
    Column {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onShuffle != null) {
                IconButton(onClick = onShuffle, enabled = !shuffling) {
                    if (shuffling) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Shuffle, strings.shuffle)
                    }
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tiles, key = { it.key }) { tile -> Tile(tile, round, Modifier.width(160.dp), wear = Wear.of(wear, tile.key)) { onClick(tile) } }
        }
    }
}

/** Saved sort shared by every search's track results. */
private const val SEARCH_SORT = "search"

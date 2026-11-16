package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.Screen
import ovh.battistella.sillon.desktop.ui.StackEntry
import ovh.battistella.sillon.desktop.ui.components.Cover
import ovh.battistella.sillon.desktop.ui.components.LoadContent
import ovh.battistella.sillon.desktop.ui.components.Message
import ovh.battistella.sillon.desktop.ui.components.TileGrid
import ovh.battistella.sillon.desktop.ui.components.TrackRow
import ovh.battistella.sillon.desktop.ui.components.Wear
import ovh.battistella.sillon.desktop.ui.components.formatDuration

@Composable
fun SectionGridScreen(app: AppState, entry: StackEntry, artists: Boolean) {
    val strings = LocalStrings.current
    val repository = app.graph.repository
    val model = entry.model("grid") { scope ->
        if (artists) {
            SectionGridModel(app.graph, scope, fetch = { repository.artists(it) })
        } else {
            SectionGridModel(
                app.graph,
                scope,
                subtitle = { it.parentTitle },
                fetch = { repository.albums(it) },
                onFetched = { section, albums -> app.graph.sleeveWear.refresh(section, albums) },
            )
        }
    }
    val state by model.state.collectAsState()
    val wear by app.graph.sleeveWear.levels.collectAsState(emptyMap())
    LoadContent(state, model::load) { ui ->
        val tiles = ui.tiles
        when {
            tiles == null -> Message(strings.libraryNoMusic)
            tiles.isEmpty() -> Message(strings.libraryEmpty)
            else -> TileGrid(
                tiles,
                onClick = { app.open(if (artists) Screen.Artist(it.key) else Screen.Album(it.key)) },
                round = artists,
                wear = if (artists) emptyMap() else wear,
            )
        }
    }
}

@Composable
fun PlaylistsScreen(app: AppState, entry: StackEntry) {
    val strings = LocalStrings.current
    val model = entry.model("playlists") { scope -> PlaylistsModel(app.graph, scope) }
    val state by model.state.collectAsState()
    LoadContent(state, model::load) { tiles ->
        if (tiles.isEmpty()) {
            Message(strings.playlistsEmpty)
        } else {
            TileGrid(
                tiles.map { tile -> tile.copy(subtitle = tile.subtitle?.toIntOrNull()?.let(strings::trackCount)) },
                onClick = { app.open(Screen.Playlist(it.key)) },
            )
        }
    }
}

@Composable
fun ArtistScreen(app: AppState, entry: StackEntry, key: String) {
    val strings = LocalStrings.current
    val model = entry.model("artist") { scope -> ArtistModel(app.graph, scope, key) }
    val state by model.state.collectAsState()
    val startingRadio by model.startingRadio.collectAsState()
    val wear by app.graph.sleeveWear.levels.collectAsState(emptyMap())
    LoadContent(state, model::load) { ui ->
        TileGrid(ui.albums, onClick = { app.open(Screen.Album(it.key)) }, wear = wear) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(ui.artworkUrl, Modifier.size(160.dp), shape = CircleShape)
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(ui.name, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(strings.albumCount(ui.albums.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = model::playRadio, enabled = !startingRadio) {
                        if (startingRadio) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Radio, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(strings.artistRadio)
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionScreen(app: AppState, entry: StackEntry, kind: CollectionKind, key: String) {
    val strings = LocalStrings.current
    val model = entry.model("collection") { scope ->
        CollectionModel(app.graph, scope, kind, key) { jellyfin -> if (jellyfin) strings.favourites else strings.fiveStars }
    }
    val state by model.state.collectAsState()
    val downloads by model.downloadStatus.collectAsState()
    val now by app.graph.playback.state.collectAsState()
    val ratings by app.graph.playback.ratings.collectAsState()
    val skipOneStar by app.graph.settings.skipOneStar.collectAsState(true)
    val wear by app.graph.sleeveWear.levels.collectAsState(emptyMap())
    LoadContent(state, model::load) { ui ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                    Cover(ui.artworkUrl, Modifier.size(220.dp), wear = if (kind == CollectionKind.ALBUM) Wear.of(wear, key) else null)
                    Spacer(Modifier.width(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ui.title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (!ui.artist.isNullOrBlank()) {
                            val artistKey = ui.artistKey
                            Text(
                                ui.artist,
                                Modifier.then(if (artistKey != null) Modifier.clickable { app.open(Screen.Artist(artistKey)) } else Modifier),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val total = ui.tracks.sumOf { it.durationMs ?: 0 }
                        Text(
                            listOfNotNull(ui.year?.toString(), strings.trackCount(ui.tracks.size), total.takeIf { it > 0 }?.let(::formatDuration))
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { model.play(null) }, enabled = ui.tracks.isNotEmpty()) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.play)
                            }
                            OutlinedButton(onClick = model::shuffle, enabled = ui.tracks.isNotEmpty()) {
                                Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.shuffle)
                            }
                            DownloadButton(downloads, model::download, model::removeDownload)
                        }
                    }
                }
            }
            if (ui.tracks.isEmpty()) {
                item { Text(strings.libraryEmpty, Modifier.padding(16.dp)) }
            }
            itemsIndexed(ui.tracks, key = { index, track -> "$index-${track.key}" }) { _, track ->
                TrackRow(
                    track = track.copy(stars = ratings.book.starsOf(track.key, track.rating)),
                    playing = now.current?.ratingKey == track.key,
                    skipped = skipOneStar && ratings.book.isOneStar(track.key, track.rating),
                    onPlay = { model.play(track.key) },
                    onPlayNext = { model.playNext(track.key) },
                    onAddToQueue = { model.addToQueue(track.key) },
                )
            }
        }
    }
}

@Composable
private fun DownloadButton(status: DownloadStatus, onDownload: () -> Unit, onRemove: () -> Unit) {
    val strings = LocalStrings.current
    when {
        status.total == 0 -> Unit
        status.isComplete -> OutlinedButton(onClick = onRemove) {
            Icon(Icons.Filled.DownloadDone, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.downloadDone)
        }
        status.isDownloading -> OutlinedButton(onClick = onRemove) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(strings.downloadProgress(status.done, status.total))
        }
        status.failed > 0 -> OutlinedButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.downloadFailed(status.failed))
        }
        status.isStarted -> OutlinedButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.downloadRemove)
        }
        else -> OutlinedButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.download)
        }
    }
}


package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.desktop.AppGraph
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.StackEntry
import ovh.battistella.sillon.desktop.ui.components.Cover
import ovh.battistella.sillon.desktop.ui.components.Message
import ovh.battistella.sillon.desktop.ui.components.formatSize
import ovh.battistella.sillon.playback.StreamUrls
import java.awt.Desktop
import java.io.File

data class DownloadedAlbum(
    val key: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val rows: List<DownloadEntity>,
) {
    val done: Int get() = rows.count { it.state == DownloadState.DONE }
    val failed: Int get() = rows.count { it.state == DownloadState.FAILED }
    val active: Boolean get() = rows.any { it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING }
    val sizeBytes: Long get() = rows.sumOf { it.sizeBytes }
    val progress: Float
        get() = if (rows.isEmpty()) 0f else rows.sumOf { if (it.state == DownloadState.DONE) 100 else it.progress } / (rows.size * 100f)
}

class DownloadsModel(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val _albums = MutableStateFlow<List<DownloadedAlbum>?>(null)
    val albums = _albums.asStateFlow()
    private var serverId: String? = null

    init {
        scope.launch {
            val id = runCatching { graph.repository.currentServerId() }.getOrNull()
            if (id == null) {
                _albums.value = emptyList()
                return@launch
            }
            serverId = id
            graph.downloader.observe(id).collect { rows ->
                _albums.value = rows.groupBy { it.albumKey ?: it.ratingKey }.map { (key, list) ->
                    val sorted = list.sortedWith(compareBy<DownloadEntity> { it.discNumber ?: 0 }.thenBy { it.trackNumber ?: 0 })
                    val first = sorted.first()
                    DownloadedAlbum(
                        key = key,
                        title = first.album ?: first.title,
                        artist = first.albumArtist ?: first.artist,
                        artworkUrl = sorted.firstNotNullOfOrNull { it.artworkPath }?.let { StreamUrls.ofFile(File(it)) },
                        rows = sorted,
                    )
                }
            }
        }
    }

    fun play(album: DownloadedAlbum) {
        scope.launch {
            val mode = graph.settings.loudness.first()
            val tracks = album.rows.filter { it.isPlayableOffline() }.map { it.toPlayable(mode) }
            graph.playback.play(tracks, pickStart = true)
        }
    }

    fun remove(album: DownloadedAlbum) {
        val id = serverId ?: return
        scope.launch { runCatching { graph.downloader.delete(id, album.rows.map { it.ratingKey }) } }
    }

    fun retryFailed() {
        scope.launch { runCatching { graph.downloader.retryFailed() } }
    }

    fun openFolder() {
        val id = serverId ?: return
        runCatching { Desktop.getDesktop().open(graph.downloadFiles.musicDir(id).also { it.mkdirs() }) }
    }
}

@Composable
fun DownloadsScreen(app: AppState, entry: StackEntry) {
    val strings = LocalStrings.current
    val model = entry.model("downloads") { scope -> DownloadsModel(app.graph, scope) }
    val albums by model.albums.collectAsState()
    val list = albums ?: return
    if (list.isEmpty()) {
        Message(strings.downloadsEmpty)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.downloadsSummary(list.sumOf { it.done }, formatSize(list.sumOf { it.sizeBytes })),
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (list.any { it.failed > 0 }) OutlinedButton(onClick = model::retryFailed) { Text(strings.downloadsRetryFailed) }
                OutlinedButton(onClick = model::openFolder) { Text(strings.downloadsOpenFolder) }
            }
        }
        items(list, key = { it.key }) { album ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cover(album.artworkUrl, Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        album.active -> strings.downloadProgress(album.done, album.rows.size)
                        album.failed > 0 -> strings.downloadFailed(album.failed)
                        else -> strings.downloadsSummary(album.done, formatSize(album.sizeBytes))
                    }
                    Text(
                        listOfNotNull(album.artist, status).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (album.active) LinearProgressIndicator(progress = { album.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                IconButton(onClick = { model.play(album) }, enabled = album.done > 0) { Icon(Icons.Filled.PlayArrow, strings.play) }
                IconButton(onClick = { model.remove(album) }) { Icon(Icons.Filled.Delete, strings.downloadRemove) }
            }
        }
    }
}

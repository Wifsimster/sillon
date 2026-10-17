package ovh.battistella.sillon.ui.screens.downloads

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.Message
import ovh.battistella.sillon.ui.screens.library.TrackCollectionScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onAlbum: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_downloads)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_wifi_only)) },
                            leadingIcon = { Checkbox(checked = wifiOnly, onCheckedChange = null) },
                            onClick = { viewModel.setWifiOnly(!wifiOnly) },
                        )
                        if (albums.orEmpty().any { it.status.failed > 0 }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_retry_failed)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.retryFailed()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = albums
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Message(stringResource(R.string.downloads_empty), Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                items(list, key = { it.albumKey }) { album ->
                    val status = album.status
                    val summary = when {
                        status.isDownloading -> stringResource(R.string.download_progress, status.done + 1, status.total)
                        status.failed > 0 -> stringResource(R.string.download_failed, status.failed)
                        else -> pluralStringResource(
                            R.plurals.downloads_summary,
                            status.done,
                            status.done,
                            Formatter.formatShortFileSize(context, album.sizeBytes),
                        )
                    }
                    ListItem(
                        leadingContent = { Artwork(album.artworkUri, Modifier.size(56.dp), cornerRadius = 6.dp) },
                        headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(
                                    listOfNotNull(album.artist, summary).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (status.isDownloading) {
                                    LinearProgressIndicator(
                                        progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onAlbum(album.albumKey) },
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedAlbumScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: DownloadedAlbumViewModel = hiltViewModel(),
) {
    val album by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    TrackCollectionScreen(
        state = album?.let { Load.Ready(it) } ?: Load.Loading,
        nowPlayingKey = nowPlaying.currentKey,
        onBack = onBack,
        onRetry = {},
        onPlay = { key -> if (key == null || viewModel.isReady(key)) viewModel.play(key) },
        onShuffle = viewModel::shuffle,
        contentPadding = contentPadding,
        actions = {
            IconButton(onClick = {
                viewModel.remove()
                onBack()
            }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.download_remove))
            }
        },
    )
}

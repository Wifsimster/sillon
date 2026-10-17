package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.formatDuration

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    TrackCollectionScreen(
        state = state,
        nowPlayingKey = nowPlaying.currentKey,
        onBack = onBack,
        onRetry = viewModel::load,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        contentPadding = contentPadding,
    )
}

/**
 * An album or a playlist: cover, title, Play / Shuffle, then the tracks.
 * [onPlay] with null plays from the top, with a key from that track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCollectionScreen(
    state: Load<AlbumUi>,
    nowPlayingKey: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (String?) -> Unit,
    onShuffle: () -> Unit,
    contentPadding: PaddingValues,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = onRetry, modifier = Modifier.padding(padding)) { album ->
            LazyColumn(
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                item {
                    AlbumHeader(album, onPlay = { onPlay(null) }, onShuffle = onShuffle)
                }
                items(album.tracks, key = { it.key }) { track ->
                    TrackRow(track, playing = nowPlayingKey == track.key, onClick = { onPlay(track.key) })
                }
            }
        }
    }
}

/** One track: number, title, artist when it adds something, duration. */
@Composable
fun TrackRow(track: TrackUi, playing: Boolean, onClick: () -> Unit) {
    val accent = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val artist = track.artist
    ListItem(
        leadingContent = {
            Text(
                track.number?.toString().orEmpty(),
                color = accent,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 24.dp),
            )
        },
        headlineContent = {
            Text(track.title, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (artist != null) {
            { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            track.durationMs?.let { Text(formatDuration(it), style = MaterialTheme.typography.bodySmall) }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AlbumHeader(album: AlbumUi, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Artwork(album.artworkUrl, Modifier.size(240.dp), cornerRadius = 12.dp)
        Text(album.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        val subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.album_tracks, album.tracks.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.album_play))
            }
            OutlinedButton(onClick = onShuffle) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.album_shuffle))
            }
        }
    }
}

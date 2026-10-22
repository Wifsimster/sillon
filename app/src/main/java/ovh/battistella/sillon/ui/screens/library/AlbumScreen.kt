package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.playback.Side
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.PopularityBars
import ovh.battistella.sillon.ui.components.RatingStars
import ovh.battistella.sillon.ui.components.formatDuration

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val listenBySide by viewModel.listenBySide.collectAsStateWithLifecycle()
    TrackCollectionRoute(viewModel, onBack, contentPadding, listenBySide, viewModel::setListenBySide)
}

@Composable
fun TrackCollectionRoute(
    viewModel: TrackCollectionViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    listenBySide: Boolean = false,
    onListenBySideChange: ((Boolean) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val download by viewModel.downloadStatus.collectAsStateWithLifecycle()
    TrackCollectionScreen(
        state = state,
        nowPlayingKey = nowPlaying.currentKey,
        onBack = onBack,
        onRetry = viewModel::load,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        contentPadding = contentPadding,
        listenBySide = listenBySide,
        onListenBySideChange = onListenBySideChange,
        actions = {
            if (state is Load.Ready) {
                DownloadAction(download, onDownload = viewModel::download, onRemove = viewModel::removeDownload)
            }
        },
    )
}

@Composable
fun DownloadAction(status: DownloadStatus, onDownload: () -> Unit, onRemove: () -> Unit) {
    when {
        status.isDownloading -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
                modifier = Modifier.size(24.dp),
            )
        }
        status.isComplete -> IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = stringResource(R.string.download_remove),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        else -> IconButton(onClick = onDownload) {
            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.download))
        }
    }
}

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
    listenBySide: Boolean = false,
    onListenBySideChange: ((Boolean) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = actions,
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
                val sideToggle = onListenBySideChange?.takeIf { album.sides.isNotEmpty() }
                item {
                    AlbumHeader(album, onPlay = { onPlay(null) }, onShuffle = onShuffle) {
                        if (sideToggle != null) ListenBySideChip(listenBySide, sideToggle)
                    }
                }
                val sideStarts = if (listenBySide && sideToggle != null) {
                    album.sides.associateBy { it.keys.first() }
                } else {
                    emptyMap()
                }
                album.tracks.forEach { track ->
                    sideStarts[track.key]?.let { side ->
                        item(key = "side-${side.label}") { SideHeader(side) }
                    }
                    item(key = track.key) {
                        TrackRow(track, playing = nowPlayingKey == track.key, onClick = { onPlay(track.key) })
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(track: TrackUi, playing: Boolean, onClick: () -> Unit) {
    val accent = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val artist = track.artist
    val stars = track.stars
    val popularity = track.popularity
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
        supportingContent = if (artist != null || stars != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stars != null) {
                        RatingStars(stars, Modifier.padding(end = 6.dp))
                    }
                    if (artist != null) Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (popularity != null) PopularityBars(popularity, Modifier.padding(end = 12.dp))
                track.durationMs?.let { Text(formatDuration(it), style = MaterialTheme.typography.bodySmall) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ListenBySideChip(selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(stringResource(R.string.settings_sides)) },
        leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

@Composable
private fun SideHeader(side: Side) {
    Text(
        stringResource(R.string.side_header, side.label, formatDuration(side.durationMs)),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun AlbumHeader(album: AlbumUi, onPlay: () -> Unit, onShuffle: () -> Unit, extra: @Composable () -> Unit = {}) {
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
            pluralStringResource(R.plurals.album_tracks, album.tracks.size, album.tracks.size),
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
        extra()
    }
}

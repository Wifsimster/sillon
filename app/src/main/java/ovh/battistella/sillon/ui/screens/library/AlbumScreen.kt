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
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.alpha
import ovh.battistella.sillon.data.rating.RatingBook
import ovh.battistella.sillon.data.rating.Stars
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
import ovh.battistella.sillon.ui.components.Wear
import ovh.battistella.sillon.ui.components.formatDuration
import ovh.battistella.sillon.ui.share.ShareCardSheet
import ovh.battistella.sillon.ui.share.ShareSource

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val wear by viewModel.wear.collectAsStateWithLifecycle()
    val listenBySide by viewModel.listenBySide.collectAsStateWithLifecycle()
    var shareSource by remember { mutableStateOf<ShareSource?>(null) }
    TrackCollectionRoute(viewModel, onBack, contentPadding, wear, listenBySide, viewModel::setListenBySide) { album ->
        shareSource = album.shareSource(viewModel.key)
    }
    shareSource?.let { ShareCardSheet(it, onDismiss = { shareSource = null }) }
}

fun AlbumUi.shareSource(key: String?) = ShareSource(
    kind = ShareSource.Kind.ALBUM,
    key = key,
    title = title,
    artist = artist,
    year = year?.toString(),
    artworkUri = artworkUrl,
)

@Composable
fun ShareCardAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_card))
    }
}

@Composable
fun TrackCollectionRoute(
    viewModel: TrackCollectionViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    wear: Wear? = null,
    listenBySide: Boolean = false,
    onListenBySideChange: ((Boolean) -> Unit)? = null,
    onShareCard: ((AlbumUi) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val download by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val skipOneStar by viewModel.skipOneStar.collectAsStateWithLifecycle()
    TrackCollectionScreen(
        state = state,
        nowPlayingKey = nowPlaying.currentKey,
        ratings = ratings.book,
        skipOneStar = skipOneStar,
        onBack = onBack,
        onRetry = viewModel::load,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        contentPadding = contentPadding,
        wear = wear,
        listenBySide = listenBySide,
        onListenBySideChange = onListenBySideChange,
        actions = {
            val ready = state as? Load.Ready
            if (ready != null && onShareCard != null) {
                ShareCardAction { onShareCard(ready.value) }
            }
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
    wear: Wear? = null,
    listenBySide: Boolean = false,
    onListenBySideChange: ((Boolean) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    ratings: RatingBook = RatingBook(),
    skipOneStar: Boolean = false,
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
                    AlbumHeader(album, wear, onPlay = { onPlay(null) }, onShuffle = onShuffle) {
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
                        val rating = ratings.ratingOf(track.key, track.stars?.times(2f))
                        val oneStar = Stars.isOneStar(rating)
                        TrackRow(
                            track.copy(stars = rating?.div(2f)),
                            playing = nowPlayingKey == track.key,
                            onClick = { onPlay(track.key) },
                            muted = oneStar,
                            skipped = oneStar && skipOneStar,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(track: TrackUi, playing: Boolean, onClick: () -> Unit, muted: Boolean = false, skipped: Boolean = false) {
    val skippedLabel = stringResource(R.string.track_skipped)
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
        // A 1-star track stays in the list, greyed, and still plays when touched.
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(if (muted && !playing) Modifier.alpha(MUTED_ALPHA) else Modifier)
            .then(if (skipped) Modifier.semantics { stateDescription = skippedLabel } else Modifier),
    )
}

private const val MUTED_ALPHA = 0.45f

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
private fun AlbumHeader(album: AlbumUi, wear: Wear?, onPlay: () -> Unit, onShuffle: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Artwork(album.artworkUrl, Modifier.size(240.dp), cornerRadius = 12.dp, wear = wear)
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

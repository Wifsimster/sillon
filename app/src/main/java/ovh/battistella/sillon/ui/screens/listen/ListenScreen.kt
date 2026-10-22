package ovh.battistella.sillon.ui.screens.listen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.NeverPlayedMode
import ovh.battistella.sillon.data.repository.neverPlayedPercent
import ovh.battistella.sillon.ui.screens.library.LibraryMenu
import ovh.battistella.sillon.ui.screens.library.NeverPlayedNotice
import ovh.battistella.sillon.ui.screens.library.NeverPlayedViewModel
import ovh.battistella.sillon.ui.screens.mystery.MysteryCard
import ovh.battistella.sillon.ui.screens.mystery.MysteryViewModel

/**
 * The first page: music in one tap. Shuffle, 5 stars, never played and the mystery sleeve, as large
 * tiles. Without a connection, what needs the server is greyed out and says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    onFiveStarsList: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ListenViewModel = hiltViewModel(),
    neverPlayed: NeverPlayedViewModel = hiltViewModel(),
    mystery: MysteryViewModel = hiltViewModel(),
) {
    val online by viewModel.online.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val isJellyfin by viewModel.isJellyfin.collectAsStateWithLifecycle()
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val plays by neverPlayed.plays.collectAsStateWithLifecycle()
    val neverStarting by neverPlayed.starting.collectAsStateWithLifecycle()
    val neverNotice by neverPlayed.notice.collectAsStateWithLifecycle()
    val shuffleState = ListenTiles.state(ListenTile.SHUFFLE, online, offline)
    val fiveStarsState = ListenTiles.state(ListenTile.FIVE_STARS, online, offline)
    val neverPlayedState = ListenTiles.state(ListenTile.NEVER_PLAYED, online, offline)
    val mysteryState = ListenTiles.state(ListenTile.MYSTERY, online, offline)
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.listen_tab)) },
                actions = { LibraryMenu() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) {
                item(key = "offline") { OfflineBanner(nothingDownloaded = offline.tracks == 0) }
            }
            item(key = "shuffle") {
                ListenTileCard(
                    title = stringResource(R.string.listen_shuffle),
                    subtitle = stringResource(
                        when (shuffleState) {
                            TileState.FROM_DOWNLOADS -> R.string.listen_shuffle_downloads
                            TileState.NOTHING_DOWNLOADED -> R.string.listen_nothing_downloaded
                            else -> R.string.listen_shuffle_hint
                        },
                    ),
                    icon = Icons.Rounded.Shuffle,
                    action = stringResource(R.string.listen_shuffle_action),
                    onClick = viewModel::shuffle,
                    enabled = ListenTiles.enabled(shuffleState) && starting == null,
                    loading = starting == ListenTile.SHUFFLE,
                    container = colors.primaryContainer,
                    large = true,
                )
            }
            item(key = "pair") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListenTileCard(
                        title = stringResource(if (isJellyfin) R.string.five_stars_jellyfin else R.string.five_stars),
                        subtitle = stringResource(
                            when (fiveStarsState) {
                                TileState.FROM_DOWNLOADS -> R.string.listen_five_stars_downloads
                                TileState.NEEDS_SERVER -> R.string.listen_five_stars_needs_server
                                TileState.NOTHING_DOWNLOADED -> R.string.listen_nothing_downloaded
                                TileState.READY -> if (isJellyfin) R.string.five_stars_jellyfin_hint else R.string.five_stars_hint
                            },
                        ),
                        icon = Icons.Rounded.Star,
                        action = stringResource(R.string.five_stars_shuffle),
                        onClick = viewModel::fiveStars,
                        enabled = ListenTiles.enabled(fiveStarsState) && starting == null,
                        loading = starting == ListenTile.FIVE_STARS,
                        container = colors.secondaryContainer,
                        modifier = Modifier.weight(1f),
                        trailing = {
                            // The full list is a server page: offline, only the tile itself plays.
                            IconButton(onClick = onFiveStarsList, enabled = online) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.List,
                                    contentDescription = stringResource(R.string.listen_five_stars_list),
                                )
                            }
                        },
                    )
                    ListenTileCard(
                        title = stringResource(R.string.never_played),
                        subtitle = if (neverPlayedState == TileState.NOTHING_DOWNLOADED) {
                            stringResource(R.string.listen_nothing_downloaded)
                        } else {
                            plays?.let { neverPlayedPercent(it.neverPlayed, it.total) }
                                ?.let { stringResource(R.string.listen_never_played_share, it) }
                                ?: stringResource(R.string.never_played_hint)
                        },
                        icon = Icons.Rounded.Explore,
                        action = stringResource(R.string.never_played_play),
                        onClick = { neverPlayed.play(NeverPlayedMode.TRACKS) },
                        enabled = ListenTiles.enabled(neverPlayedState) && neverStarting == null,
                        loading = neverStarting == NeverPlayedMode.TRACKS,
                        container = colors.tertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "mystery") {
                Box(Modifier.bleed(8.dp)) { MysteryCard(mystery, enabled = ListenTiles.enabled(mysteryState)) }
            }
            item(key = "never-played-albums") {
                ListenTileCard(
                    title = stringResource(R.string.never_played_albums),
                    subtitle = if (neverPlayedState == TileState.NOTHING_DOWNLOADED) {
                        stringResource(R.string.listen_nothing_downloaded)
                    } else {
                        null
                    },
                    icon = Icons.Rounded.Album,
                    action = stringResource(R.string.never_played_albums_play),
                    onClick = { neverPlayed.play(NeverPlayedMode.ALBUMS) },
                    enabled = ListenTiles.enabled(neverPlayedState) && neverStarting == null,
                    loading = neverStarting == NeverPlayedMode.ALBUMS,
                    container = colors.surfaceContainerHigh,
                    small = true,
                )
            }
            val message = notice?.let { listenNoticeText(it, isJellyfin) } ?: neverNotice?.let { neverPlayedNoticeText(it) }
            if (message != null) {
                item(key = "notice") {
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notice == ListenNotice.FAILED || neverNotice == NeverPlayedNotice.FAILED) {
                            colors.error
                        } else {
                            colors.onSurfaceVariant
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

private fun listenNoticeText(notice: ListenNotice, isJellyfin: Boolean): Int = when (notice) {
    ListenNotice.SHUFFLE_FROM_DOWNLOADS -> R.string.listen_from_downloads
    ListenNotice.FIVE_STARS_FROM_DOWNLOADS -> R.string.listen_five_stars_from_downloads
    ListenNotice.NO_FIVE_STARS -> if (isJellyfin) R.string.listen_no_favourites else R.string.listen_no_five_stars
    ListenNotice.FAILED -> R.string.listen_failed
}

private fun neverPlayedNoticeText(notice: NeverPlayedNotice): Int = when (notice) {
    NeverPlayedNotice.OFFLINE -> R.string.never_played_offline
    NeverPlayedNotice.NO_OFFLINE_HISTORY -> R.string.never_played_no_history
    NeverPlayedNotice.EXHAUSTED -> R.string.never_played_exhausted
    NeverPlayedNotice.FAILED -> R.string.never_played_failed
}

@Composable
private fun OfflineBanner(nothingDownloaded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(if (nothingDownloaded) R.string.listen_offline_nothing else R.string.listen_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * One tile: a single button for TalkBack, read as its title and subtitle, with [action] as what a
 * double tap does. Disabled tiles stay readable and say why in their subtitle.
 */
@Composable
private fun ListenTileCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    action: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    container: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    small: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (small) 16.dp else 24.dp)
    Surface(
        shape = shape,
        color = if (enabled || loading) container else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        val faded = !enabled && !loading
        CompositionLocalProvider(
            LocalContentColor provides LocalContentColor.current.copy(alpha = if (faded) DISABLED_ALPHA else 1f),
        ) {
            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (large) 148.dp else if (small) 0.dp else 168.dp)
                        .clickable(enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onClick)
                        .padding(if (small) 16.dp else 20.dp),
                    verticalArrangement = if (small) Arrangement.Top else Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(if (large) 40.dp else if (small) 24.dp else 32.dp), contentAlignment = Alignment.Center) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 40.dp else if (small) 24.dp else 32.dp))
                        }
                    }
                    if (!small) Spacer(Modifier.height(16.dp))
                    Column(Modifier.padding(top = if (small) 8.dp else 0.dp)) {
                        Text(
                            title,
                            style = when {
                                large -> MaterialTheme.typography.headlineMedium
                                small -> MaterialTheme.typography.titleSmall
                                else -> MaterialTheme.typography.titleLarge
                            },
                        )
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (trailing != null) {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { trailing() }
                }
            }
        }
    }
}

/** Cancels the margin a reused card brings along, so it lines up with the tiles. */
private fun Modifier.bleed(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = (margin * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtMost(constraints.maxWidth + extra),
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    val height = (placeable.height - extra).coerceAtLeast(0)
    layout(constraints.maxWidth, height) { placeable.place(-extra / 2, -extra / 2) }
}

private const val DISABLED_ALPHA = 0.6f

package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import ovh.battistella.sillon.ui.components.Message

@Composable
fun ArtistsScreen(
    onArtist: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ArtistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SectionGridScreen(stringResource(R.string.library_artists), state, viewModel::load, onArtist, round = true, contentPadding)
}

@Composable
fun AlbumsScreen(
    onAlbum: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SectionGridScreen(stringResource(R.string.library_albums), state, viewModel::load, onAlbum, round = false, contentPadding)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionGridScreen(
    title: String,
    state: Load<SectionGridUi>,
    onRetry: () -> Unit,
    onClick: (String) -> Unit,
    round: Boolean,
    contentPadding: PaddingValues,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = { LibraryMenu() },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = onRetry, modifier = Modifier.padding(padding)) { ui ->
            val tiles = ui.tiles
            when {
                tiles == null -> Message(stringResource(R.string.library_no_music), Modifier.padding(padding))
                tiles.isEmpty() -> Message(stringResource(R.string.library_empty), Modifier.padding(padding))
                else -> TileGrid(
                    tiles = tiles,
                    onClick = onClick,
                    round = round,
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
fun TileGrid(
    tiles: List<TileUi>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(tiles, key = { it.key }) { tile ->
            Column(
                modifier = Modifier
                    .clickable { onClick(tile.key) }
                    .padding(8.dp),
                horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Artwork(
                    url = tile.artworkUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    cornerRadius = if (round) 999.dp else 8.dp,
                )
                Text(
                    tile.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (round) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val subtitle = tile.subtitle
                    ?: tile.trackCount?.let { pluralStringResource(R.plurals.album_tracks, it, it) }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

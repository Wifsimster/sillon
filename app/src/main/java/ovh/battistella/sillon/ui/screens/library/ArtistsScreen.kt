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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    onArtist: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ArtistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_artists)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_change_server)) },
                            onClick = { menuOpen = false; viewModel.changeServer() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_sign_out)) },
                            onClick = { menuOpen = false; viewModel.signOut() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { ui ->
            val artists = ui.artists
            when {
                artists == null -> Message(stringResource(R.string.library_no_music), Modifier.padding(padding))
                artists.isEmpty() -> Message(stringResource(R.string.library_empty), Modifier.padding(padding))
                else -> TileGrid(
                    tiles = artists,
                    onClick = onArtist,
                    round = true,
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/** Adaptive grid of covers; [round] for artists, square for albums. */
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
                tile.subtitle?.let {
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

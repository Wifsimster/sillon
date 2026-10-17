package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylist: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_playlists)) },
                actions = { LibraryMenu() },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { playlists ->
            if (playlists.isEmpty()) {
                Message(stringResource(R.string.playlists_empty), Modifier.padding(padding))
            } else {
                TileGrid(
                    tiles = playlists,
                    onClick = onPlaylist,
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    TrackCollectionRoute(viewModel, onBack, contentPadding)
}

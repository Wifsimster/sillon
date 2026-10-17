package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylist: (String) -> Unit,
    onFiveStars: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startingFiveStars by viewModel.startingFiveStars.collectAsStateWithLifecycle()
    val fiveStarsCard: @Composable () -> Unit = {
        FiveStarsCard(starting = startingFiveStars, onOpen = onFiveStars, onShuffle = viewModel::shuffleFiveStars)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_playlists)) },
                actions = { LibraryMenu() },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { playlists ->
            // The 5-star mix is always there, even on a server without playlists.
            TileGrid(
                tiles = playlists,
                onClick = onPlaylist,
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = contentPadding,
                header = fiveStarsCard,
            )
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

/**
 * "My 5 stars": tap the shuffle button to play every 5-star track at once, or
 * the card to see the list.
 */
@Composable
private fun FiveStarsCard(starting: Boolean, onOpen: () -> Unit, onShuffle: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.five_stars), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.five_stars_hint), style = MaterialTheme.typography.bodySmall)
            }
            FilledIconButton(onClick = onShuffle, enabled = !starting, modifier = Modifier.size(48.dp)) {
                if (starting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.five_stars_shuffle))
                }
            }
        }
    }
}

@Composable
fun FiveStarsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: FiveStarsViewModel = hiltViewModel(),
) {
    TrackCollectionRoute(viewModel, onBack, contentPadding)
}

package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.ui.components.LoadContent

/** The radios, then the playlists. The one-tap tiles (5 stars, never played, mystery sleeve) live on Listen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylist: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val startingStation by viewModel.startingStation.collectAsStateWithLifecycle()
    val radios: (@Composable () -> Unit)? = if (stations.isEmpty()) {
        null
    } else {
        { Column { StationsRow(stations, startingStation, onPlay = viewModel::playStation) } }
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
            TileGrid(
                tiles = playlists,
                onClick = onPlaylist,
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = contentPadding,
                header = radios,
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

@Composable
private fun StationsRow(stations: List<Station>, starting: String?, onPlay: (Station) -> Unit) {
    SectionTitle(stringResource(R.string.radios))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        items(stations, key = { it.key }) { station ->
            Card(
                onClick = { onPlay(station) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (starting == station.key) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        station.title ?: stringResource(R.string.radio_library),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun FiveStarsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: FiveStarsViewModel = hiltViewModel(),
) {
    TrackCollectionRoute(viewModel, onBack, contentPadding)
}

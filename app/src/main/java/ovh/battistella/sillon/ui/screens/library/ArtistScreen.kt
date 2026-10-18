package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    onAlbum: (String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startingRadio by viewModel.startingRadio.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? Load.Ready)?.value?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (startingRadio) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::playRadio) {
                            Icon(Icons.Rounded.Radio, stringResource(R.string.artist_radio))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { ui ->
            if (ui.albums.isEmpty()) {
                Message(stringResource(R.string.library_empty), Modifier.padding(padding))
            } else {
                TileGrid(
                    tiles = ui.albums,
                    onClick = onAlbum,
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

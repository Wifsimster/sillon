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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.Message
import ovh.battistella.sillon.ui.components.Wear
import ovh.battistella.sillon.ui.share.CardRules
import ovh.battistella.sillon.ui.share.ShareCardSheet
import ovh.battistella.sillon.ui.share.ShareSource

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
    val wear by viewModel.wear.collectAsStateWithLifecycle()
    var shareSource by remember { mutableStateOf<ShareSource?>(null) }

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
                    (state as? Load.Ready)?.value?.let { artist ->
                        ShareCardAction {
                            shareSource = ShareSource(
                                kind = ShareSource.Kind.ARTIST,
                                key = viewModel.key,
                                title = artist.name,
                                year = CardRules.yearSpan(artist.years),
                                artworkUri = artist.artworkUrl,
                            )
                        }
                    }
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
                    wear = { Wear.of(wear, it) },
                )
            }
        }
    }
    shareSource?.let { ShareCardSheet(it, onDismiss = { shareSource = null }) }
}

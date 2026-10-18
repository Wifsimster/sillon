package ovh.battistella.sillon.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.Message
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.screens.library.TrackRow

@Composable
fun SearchScreen(
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        when (val s = state) {
            SearchState.Idle -> Unit
            SearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SearchState.Failed -> Message(
                stringResource(R.string.error_generic, s.message),
                action = stringResource(R.string.action_retry) to viewModel::retry,
            )
            is SearchState.Ready -> if (s.results.isEmpty) {
                Message(stringResource(R.string.search_no_results, query.trim()))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
                ) {
                    if (s.results.artists.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.library_artists)) }
                        item { TileRow(s.results.artists, round = true, onClick = onArtist) }
                    }
                    if (s.results.albums.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.library_albums)) }
                        item { TileRow(s.results.albums, round = false, onClick = onAlbum) }
                    }
                    if (s.results.tracks.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.library_tracks)) }
                        items(s.results.tracks, key = { "track-${it.key}" }) { track ->
                            TrackRow(
                                track,
                                playing = nowPlaying.currentKey == track.key,
                                onClick = { viewModel.playTrack(track.key) },
                            )
                        }
                    }
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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun TileRow(tiles: List<TileUi>, round: Boolean, onClick: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(tiles, key = { it.key }) { tile ->
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clickable { onClick(tile.key) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(tile.artworkUrl, Modifier.size(112.dp), cornerRadius = if (round) 999.dp else 8.dp)
                Text(
                    tile.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

package ovh.battistella.sillon.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.Message
import ovh.battistella.sillon.ui.screens.library.TileGrid
import ovh.battistella.sillon.ui.screens.library.TileUi

@Composable
fun GenreScreen(
    onAlbum: (String) -> Unit,
    onDownloadedAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shuffling by viewModel.shuffling.collectAsStateWithLifecycle()
    val nothingToPlay by viewModel.nothingToPlay.collectAsStateWithLifecycle()
    GenrePage(
        ui = state,
        shuffling = shuffling,
        nothingToPlay = nothingToPlay,
        // Without a connection the album page can't load: the downloaded copy opens instead.
        onAlbum = if (state.online) onAlbum else onDownloadedAlbum,
        onArtist = onArtist,
        onShuffle = viewModel::shuffle,
        onBack = onBack,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenrePage(
    ui: GenrePageUi,
    shuffling: Boolean,
    nothingToPlay: Boolean,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onShuffle: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !ui.found || ui.albums.isEmpty() -> Message(stringResource(R.string.genre_missing), Modifier.padding(padding))
            else -> TileGrid(
                tiles = ui.albums,
                onClick = onAlbum,
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = contentPadding,
                header = {
                    Column {
                        if (!ui.online) Note(stringResource(R.string.search_home_offline))
                        ShuffleButton(shuffling, onShuffle)
                        if (nothingToPlay) Note(stringResource(R.string.genre_nothing_to_play))
                        if (ui.artists.isNotEmpty()) {
                            Title(stringResource(R.string.genre_artists))
                            ArtistRow(ui.artists, onArtist)
                        }
                        Title(stringResource(R.string.genre_albums))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    onGenre: (String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: GenresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GenreList(state, onGenre, onBack, contentPadding)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreList(ui: GenreListUi, onGenre: (String) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_genres_all)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.genres.isEmpty() -> Message(
                stringResource(if (ui.online) R.string.genre_missing else R.string.genres_offline_none),
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                if (!ui.online) item { Note(stringResource(R.string.search_home_offline)) }
                items(ui.genres.chunked(2), key = { row -> row.first().key }) { row ->
                    GenreGrid(row, onGenre, Modifier.padding(bottom = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun ShuffleButton(shuffling: Boolean, onShuffle: () -> Unit) {
    Button(
        onClick = onShuffle,
        enabled = !shuffling,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        if (shuffling) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(stringResource(R.string.genre_shuffle), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ArtistRow(artists: List<TileUi>, onArtist: (String) -> Unit) {
    val openLabel = stringResource(R.string.genre_open_artist)
    LazyRow {
        items(artists, key = { it.key }) { artist ->
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .clickable(onClickLabel = openLabel) { onArtist(artist.key) }
                    .clearAndSetSemantics {
                        contentDescription = artist.title
                        role = Role.Button
                        onClick(openLabel) { onArtist(artist.key); true }
                    }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(artist.artworkUrl, Modifier.size(88.dp), cornerRadius = 999.dp)
                Text(
                    artist.title,
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

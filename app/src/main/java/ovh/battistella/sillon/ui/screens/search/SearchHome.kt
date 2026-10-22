package ovh.battistella.sillon.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.Message
import ovh.battistella.sillon.ui.screens.library.TileUi

/** The search page before anything is typed: the albums played lately and the most played ones. */
@Composable
fun SearchHome(
    ui: SearchHomeUi,
    onAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    when {
        ui.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            val label = stringResource(R.string.search_home_loading)
            CircularProgressIndicator(Modifier.semantics { contentDescription = label })
        }
        ui.isEmpty && !ui.online -> Message(stringResource(R.string.search_home_offline_empty))
        ui.isEmpty && !ui.classicsNeedWear -> Message(stringResource(R.string.search_home_empty))
        else -> LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            if (!ui.online) item { Note(stringResource(R.string.search_home_offline)) }
            if (ui.recent.isNotEmpty()) {
                item { RowTitle(stringResource(R.string.search_home_recent)) }
                item { AlbumRow(ui.recent, onAlbum, onPlayAlbum) }
            }
            if (ui.classics.isNotEmpty()) {
                item { RowTitle(stringResource(R.string.search_home_classics)) }
                item { AlbumRow(ui.classics, onAlbum, onPlayAlbum) }
            } else if (ui.classicsNeedWear) {
                item { RowTitle(stringResource(R.string.search_home_classics)) }
                item { Note(stringResource(R.string.search_home_classics_need_wear)) }
            }
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(tiles: List<TileUi>, onAlbum: (String) -> Unit, onPlay: (String) -> Unit) {
    val openLabel = stringResource(R.string.search_home_open)
    val playLabel = stringResource(R.string.search_home_play)
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(tiles, key = { it.key }) { tile ->
            val description = tile.subtitle?.let { stringResource(R.string.search_home_album, tile.title, it) } ?: tile.title
            Column(
                modifier = Modifier
                    .width(136.dp)
                    .combinedClickable(
                        onClickLabel = openLabel,
                        onLongClickLabel = playLabel,
                        onLongClick = { onPlay(tile.key) },
                        onClick = { onAlbum(tile.key) },
                    )
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick(openLabel) { onAlbum(tile.key); true }
                        onLongClick(playLabel) { onPlay(tile.key); true }
                    }
                    .padding(4.dp),
            ) {
                Artwork(tile.artworkUrl, Modifier.size(128.dp))
                Text(
                    tile.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp),
                )
                tile.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

package ovh.battistella.sillon.desktop.ui.components

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.i18n.LocalStrings

data class TileUi(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

data class TrackUi(
    val key: String,
    val number: Int?,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
    val popularity: Float? = null,
    val rating: Float? = null,
    val stars: Int? = null,
)

@Composable
fun TileGrid(
    tiles: List<TileUi>,
    onClick: (TileUi) -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    state: LazyGridState = rememberLazyGridState(),
    wear: Map<String, Int> = emptyMap(),
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(tiles, key = { it.key }) { tile -> Tile(tile, round, wear = Wear.of(wear, tile.key)) { onClick(tile) } }
    }
}

@Composable
fun Tile(tile: TileUi, round: Boolean, modifier: Modifier = Modifier, wear: Wear? = null, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Cover(
            url = tile.artworkUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = if (round) CircleShape else RoundedCornerShape(10.dp),
            wear = wear,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tile.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
        )
        if (!tile.subtitle.isNullOrBlank()) {
            Text(
                tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TrackUi,
    playing: Boolean,
    skipped: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    ContextMenuArea(items = {
        listOf(ContextMenuItem(strings.queuePlayNext, onPlayNext), ContextMenuItem(strings.queueAdd, onAddToQueue))
    }) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (playing) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
                .hoverable(interaction)
                .combinedClickable(onClick = {}, onDoubleClick = onPlay)
                .padding(horizontal = 8.dp)
                .height(52.dp)
                .alpha(if (skipped && !playing) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                when {
                    hovered -> IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, strings.play) }
                    playing -> Icon(Icons.Filled.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                    else -> Text(
                        track.number?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = if (skipped) listOfNotNull(track.artist, strings.trackSkipped).joinToString(" · ") else track.artist
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (track.popularity != null) PopularityBar(track.popularity)
            Row(Modifier.width(92.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(track.stars ?: 0) {
                    Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                track.durationMs?.let(::formatDuration).orEmpty(),
                Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            Box {
                IconButton(onClick = { menu = true }, Modifier.alpha(if (hovered || menu) 1f else 0f)) {
                    Icon(Icons.Filled.MoreVert, strings.more)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(strings.queuePlayNext) }, onClick = {
                        menu = false
                        onPlayNext()
                    })
                    DropdownMenuItem(text = { Text(strings.queueAdd) }, onClick = {
                        menu = false
                        onAddToQueue()
                    })
                }
            }
        }
    }
}

@Composable
private fun PopularityBar(value: Float) {
    Box(
        Modifier.width(64.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.04f, 1f)).background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import java.text.Collator
import kotlin.math.roundToInt

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
    val plays: Int? = null,
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

enum class TrackSort(val descendingFirst: Boolean) {
    NUMBER(false),
    TITLE(false),
    POPULARITY(true),
    PLAYS(true),
    STARS(true),
    DURATION(false),
}

data class TrackOrder(val by: TrackSort, val descending: Boolean)

fun TrackOrder.encode(): String = "${by.name}:${if (descending) "desc" else "asc"}"

fun decodeTrackOrder(value: String?): TrackOrder? {
    val (column, direction) = value?.split(':')?.takeIf { it.size == 2 } ?: return null
    val by = TrackSort.entries.firstOrNull { it.name == column } ?: return null
    return when (direction) {
        "desc" -> TrackOrder(by, descending = true)
        "asc" -> TrackOrder(by, descending = false)
        else -> null
    }
}

/** Next state when a column header is clicked: natural direction, then reversed, then back to the original order. */
fun TrackOrder?.next(column: TrackSort): TrackOrder? = when {
    this == null || by != column -> TrackOrder(column, column.descendingFirst)
    descending == column.descendingFirst -> copy(descending = !descending)
    else -> null
}

private val titleCollator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** Sorts a copy of the list; tracks without a value for the column always come last. Ties keep their original order. */
fun List<TrackUi>.sortedBy(order: TrackOrder?): List<TrackUi> {
    if (order == null) return this
    val values: Comparator<TrackUi> = when (order.by) {
        TrackSort.NUMBER -> nullsLastBy { it.number }
        TrackSort.TITLE -> Comparator { a, b -> titleCollator.compare(a.title, b.title) }
        TrackSort.POPULARITY -> nullsLastBy { it.popularity }
        TrackSort.PLAYS -> nullsLastBy { it.plays }
        TrackSort.STARS -> nullsLastBy { it.stars?.takeIf { s -> s > 0 } }
        TrackSort.DURATION -> nullsLastBy { it.durationMs }
    }
    val directed = if (order.descending) {
        Comparator<TrackUi> { a, b ->
            val missing = compareValues(a.isMissing(order.by), b.isMissing(order.by))
            if (missing != 0) missing else values.compare(b, a)
        }
    } else {
        values
    }
    return sortedWith(directed)
}

private fun <T : Comparable<T>> nullsLastBy(selector: (TrackUi) -> T?): Comparator<TrackUi> =
    Comparator { a, b ->
        val x = selector(a)
        val y = selector(b)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            else -> x.compareTo(y)
        }
    }

private fun TrackUi.isMissing(by: TrackSort): Boolean = when (by) {
    TrackSort.NUMBER -> number == null
    TrackSort.TITLE -> false
    TrackSort.POPULARITY -> popularity == null
    TrackSort.PLAYS -> plays == null
    TrackSort.STARS -> (stars ?: 0) <= 0
    TrackSort.DURATION -> durationMs == null
}

private val NumberWidth = 44.dp
private val PopularityWidth = 64.dp
private val PlaysWidth = 64.dp
private val StarsWidth = 92.dp
private val DurationWidth = 56.dp
private val MenuWidth = 48.dp

/** Column headers matching [TrackRow]; clicking one sorts the list by it. */
@Composable
fun TrackListHeader(
    order: TrackOrder?,
    onOrder: (TrackOrder?) -> Unit,
    showPopularity: Boolean,
    showPlays: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("#", TrackSort.NUMBER, order, onOrder, Modifier.width(NumberWidth), Arrangement.Center)
        HeaderCell(strings.columnTitle, TrackSort.TITLE, order, onOrder, Modifier.weight(1f).padding(horizontal = 8.dp), Arrangement.Start)
        if (showPopularity) {
            HeaderCell(strings.columnPopularity, TrackSort.POPULARITY, order, onOrder, Modifier.width(PopularityWidth), Arrangement.Start)
        }
        if (showPlays) {
            HeaderCell(strings.columnPlays, TrackSort.PLAYS, order, onOrder, Modifier.width(PlaysWidth), Arrangement.End)
        }
        HeaderCell(strings.columnRating, TrackSort.STARS, order, onOrder, Modifier.width(StarsWidth).padding(start = 16.dp), Arrangement.Start)
        HeaderCell(strings.columnDuration, TrackSort.DURATION, order, onOrder, Modifier.width(DurationWidth), Arrangement.End)
        Spacer(Modifier.width(MenuWidth))
    }
    HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HeaderCell(
    label: String,
    column: TrackSort,
    order: TrackOrder?,
    onOrder: (TrackOrder?) -> Unit,
    modifier: Modifier,
    arrangement: Arrangement.Horizontal,
) {
    val strings = LocalStrings.current
    val active = order?.by == column
    val hint = when {
        !active -> strings.sortBy(if (column == TrackSort.NUMBER) strings.tracks else label)
        order!!.descending == column.descendingFirst -> strings.sortReverse
        else -> strings.sortReset
    }
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Tooltip(hint, modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { onOrder(order.next(column)) }
                .padding(vertical = 4.dp),
            horizontalArrangement = arrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (active) {
                Icon(
                    if (order!!.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    null,
                    Modifier.padding(start = 2.dp).size(14.dp),
                    tint = color,
                )
            }
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
    showPopularity: Boolean = track.popularity != null,
    showPlays: Boolean = track.plays != null,
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
            Box(Modifier.width(NumberWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                when {
                    hovered -> Tooltip(strings.play) {
                        IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, strings.play) }
                    }
                    playing -> Tooltip(strings.nowPlaying) {
                        Icon(Icons.Filled.Equalizer, strings.nowPlaying, tint = MaterialTheme.colorScheme.primary)
                    }
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
            if (showPopularity) {
                Box(Modifier.width(PopularityWidth)) {
                    val popularity = track.popularity
                    if (popularity != null) {
                        Tooltip(strings.popularity((popularity.coerceIn(0f, 1f) * 100).roundToInt())) {
                            Box(Modifier.padding(vertical = 8.dp)) { PopularityBar(popularity) }
                        }
                    }
                }
            }
            if (showPlays) {
                Box(Modifier.width(PlaysWidth), contentAlignment = Alignment.CenterEnd) {
                    val plays = track.plays
                    if (plays != null) {
                        Tooltip(strings.plays(plays)) {
                            Text(
                                if (plays > 0) plays.toString() else "–",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Box(Modifier.width(StarsWidth).padding(start = 16.dp)) {
                val stars = track.stars ?: 0
                if (stars > 0) {
                    val hint = if (skipped) "${strings.stars(stars)} · ${strings.trackSkipped}" else strings.stars(stars)
                    Tooltip(hint) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            repeat(stars) {
                                Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Text(
                track.durationMs?.let(::formatDuration).orEmpty(),
                Modifier.width(DurationWidth),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            Box {
                val visible = hovered || menu
                Tooltip(if (visible) strings.more else null) {
                    IconButton(onClick = { menu = true }, Modifier.alpha(if (visible) 1f else 0f)) {
                        Icon(Icons.Filled.MoreVert, strings.more)
                    }
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
        Modifier.width(PopularityWidth).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.04f, 1f)).background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

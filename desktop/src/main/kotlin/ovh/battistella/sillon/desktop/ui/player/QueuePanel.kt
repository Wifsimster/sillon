package ovh.battistella.sillon.desktop.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.components.Cover
import ovh.battistella.sillon.playback.PlayableTrack

@Composable
fun QueuePanel(app: AppState, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val playback = app.graph.playback
    val state by playback.state.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(state.index, state.queue.size) {
        if (state.index >= 0) runCatching { list.animateScrollToItem(state.index) }
    }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.queue, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (state.queue.isNotEmpty()) TextButton(onClick = { playback.stop() }) { Text(strings.queueClear) }
            IconButton(onClick = { app.queueOpen = false }) { Icon(Icons.Filled.Close, strings.back) }
        }
        if (state.queue.isEmpty()) {
            Text(strings.playerNothing, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            itemsIndexed(state.queue) { index, track ->
                if (index == state.index) Section(strings.queueNowPlaying)
                if (index == state.index + 1) Section(strings.queueUpNext)
                QueueRow(
                    track = track,
                    current = index == state.index,
                    played = index < state.index,
                    canMoveUp = index > state.index + 1,
                    canMoveDown = index > state.index && index < state.queue.lastIndex,
                    onJump = { playback.jumpTo(index) },
                    onRemove = { playback.removeAt(index) },
                    onMoveUp = { playback.move(index, index - 1) },
                    onMoveDown = { playback.move(index, index + 1) },
                )
            }
            if (state.index == state.queue.lastIndex) {
                item {
                    Section(strings.queueUpNext)
                    Text(strings.queueNothingNext, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    track: PlayableTrack,
    current: Boolean,
    played: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val strings = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (current) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .hoverable(interaction)
            .combinedClickable(onClick = {}, onDoubleClick = onJump)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alpha(if (played) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.artworkUrl, Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hovered) {
            if (canMoveUp) IconButton(onClick = onMoveUp, Modifier.size(30.dp)) { Icon(Icons.Filled.KeyboardArrowUp, strings.queueMoveUp) }
            if (canMoveDown) IconButton(onClick = onMoveDown, Modifier.size(30.dp)) { Icon(Icons.Filled.KeyboardArrowDown, strings.queueMoveDown) }
            IconButton(onClick = onRemove, Modifier.size(30.dp)) { Icon(Icons.Filled.Close, strings.queueRemove, Modifier.size(18.dp)) }
        }
    }
}

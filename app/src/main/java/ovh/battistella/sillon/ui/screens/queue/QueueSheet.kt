package ovh.battistella.sillon.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork

/** The queue playing now: the track on, what comes next, and what already played (folded). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        QueueSheetContent(state, actions = viewModel)
    }
}

@Composable
fun QueueSheetContent(state: QueueUi, actions: QueueActions, modifier: Modifier = Modifier) {
    var showPlayed by rememberSaveable { mutableStateOf(false) }
    val upNext by rememberUpdatedState(state.upNext)
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableFloatStateOf(0f) }

    fun endDrag() {
        val from = upNext.indexOfFirst { it.item.id == dragged }
        if (from >= 0) {
            val to = (from + QueueModel.dragSteps(dragOffset, rowPx)).coerceIn(0, upNext.lastIndex)
            QueueModel.move(upNext, from, to)?.let { (item, target) -> actions.move(item, target) }
        }
        dragged = null
        dragOffset = 0f
    }

    val noticeText = notice(state)
    LazyColumn(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "title") {
            Text(
                stringResource(R.string.queue_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        noticeText?.let { text ->
            item(key = "notice") { Notice(text) }
        }
        state.current?.let { current ->
            item(key = "current") { CurrentRow(current) }
        }
        item(key = "next-header") { SectionHeader(stringResource(R.string.queue_up_next)) }
        if (state.upNext.isEmpty() && !state.radioContinues) {
            item(key = "next-empty") {
                Text(
                    stringResource(R.string.queue_nothing_next),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        itemsIndexed(state.upNext, key = { _, row -> row.item.id }) { position, row ->
            val from = state.upNext.indexOfFirst { it.item.id == dragged }
            val target = if (from >= 0) (from + QueueModel.dragSteps(dragOffset, rowPx)).coerceIn(0, state.upNext.lastIndex) else -1
            val shift = when {
                from < 0 -> 0f
                position == from -> dragOffset
                position in (from + 1)..target -> -rowPx
                position in target until from -> rowPx
                else -> 0f
            }
            Column(
                Modifier
                    .zIndex(if (position == from) 1f else 0f)
                    .graphicsLayer { translationY = shift }
                    .onSizeChanged { rowPx = it.height.toFloat() },
            ) {
                UpNextRow(
                    row = row,
                    editable = state.editable,
                    onJump = { actions.jump(row.item.index) },
                    onRemove = { actions.remove(row.item.index) },
                    onMoveUp = QueueModel.move(state.upNext, position, position - 1)
                        ?.takeIf { state.canReorder }
                        ?.let { (item, to) -> { actions.move(item, to) } },
                    onMoveDown = QueueModel.move(state.upNext, position, position + 1)
                        ?.takeIf { state.canReorder }
                        ?.let { (item, to) -> { actions.move(item, to) } },
                    dragHandle = if (state.canReorder) {
                        Modifier.pointerInput(row.item.id) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragged = row.item.id
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                },
                                onDragEnd = ::endDrag,
                                onDragCancel = {
                                    dragged = null
                                    dragOffset = 0f
                                },
                            )
                        }
                    } else {
                        null
                    },
                )
                row.sideBreak?.let { SideBreakRow(it) }
            }
        }
        if (state.radioContinues) {
            item(key = "radio") { RadioContinues() }
        }
        if (state.played.isNotEmpty()) {
            item(key = "played-header") {
                PlayedHeader(state.played.size, expanded = showPlayed, onToggle = { showPlayed = !showPlayed })
            }
            if (showPlayed) {
                itemsIndexed(state.played, key = { _, row -> "played-${row.item.id}" }) { _, row ->
                    PlayedRow(row, enabled = state.editable, onJump = { actions.jump(row.item.index) })
                }
            }
        }
    }
}

@Composable
private fun notice(state: QueueUi): String? = when {
    state.lock == QueueLock.TOGETHER -> stringResource(R.string.queue_together)
    state.lock == QueueLock.SPEAKER_SHUFFLE -> stringResource(R.string.queue_speaker_shuffle, state.speaker.orEmpty())
    state.speaker != null -> stringResource(R.string.queue_on_speaker, state.speaker)
    state.sidesActive -> stringResource(R.string.queue_sides_note)
    !state.canReorder && state.upNext.size > 1 -> stringResource(R.string.queue_shuffle_no_reorder)
    else -> null
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun titleOf(item: QueueItem): String =
    if (item.hidden) {
        item.trackNumber?.let { stringResource(R.string.queue_hidden_track, it) } ?: stringResource(R.string.mystery_title)
    } else {
        item.title
    }

@Composable
private fun CurrentRow(row: QueueRow) {
    val nowPlaying = stringResource(R.string.queue_now_playing)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        ListItem(
            overlineContent = { Text(nowPlaying) },
            headlineContent = { Text(titleOf(row.item), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = row.item.artist?.let { artist -> @Composable { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingContent = { Artwork(row.item.artworkUri, Modifier.size(48.dp), cornerRadius = 8.dp) },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
                overlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
        row.sideBreak?.let { SideBreakRow(it) }
    }
}

@Composable
private fun UpNextRow(
    row: QueueRow,
    editable: Boolean,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    dragHandle: Modifier?,
) {
    val removeLabel = stringResource(R.string.queue_remove)
    val upLabel = stringResource(R.string.queue_move_up)
    val downLabel = stringResource(R.string.queue_move_down)
    val jumpLabel = stringResource(R.string.queue_jump)
    val readOnly = stringResource(R.string.queue_read_only)
    val remove by rememberUpdatedState(onRemove)
    Removable(enabled = editable, onRemove = { remove() }) {
        ListItem(
            headlineContent = { Text(titleOf(row.item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = row.item.artist?.let { artist -> @Composable { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingContent = { Artwork(row.item.artworkUri, Modifier.size(40.dp), cornerRadius = 6.dp) },
            trailingContent = dragHandle?.let { handle ->
                @Composable {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.queue_drag),
                        modifier = handle.size(48.dp).padding(12.dp),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = if (editable) {
                Modifier
                    .clickable(onClickLabel = jumpLabel, onClick = onJump)
                    .semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(removeLabel) { onRemove(); true },
                            onMoveUp?.let { CustomAccessibilityAction(upLabel) { it(); true } },
                            onMoveDown?.let { CustomAccessibilityAction(downLabel) { it(); true } },
                        )
                    }
            } else {
                Modifier.semantics(mergeDescendants = true) { stateDescription = readOnly }
            },
        )
    }
}

@Composable
private fun Removable(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
private fun SideBreakRow(side: SideBreak) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.queue_side_end, side.ended, side.next),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RadioContinues() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.queue_radio_continues),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.queue_expanded else R.string.queue_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = state
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.queue_played, count, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun PlayedRow(row: QueueRow, enabled: Boolean, onJump: () -> Unit) {
    ListItem(
        headlineContent = { Text(titleOf(row.item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = row.item.artist?.let { artist -> @Composable { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Artwork(row.item.artworkUri, Modifier.size(40.dp), cornerRadius = 6.dp) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = if (enabled) {
            Modifier.clickable(onClickLabel = stringResource(R.string.queue_jump), onClick = onJump)
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        },
    )
}

package ovh.battistella.sillon.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.sort.TrackOrder
import ovh.battistella.sillon.data.sort.TrackSort
import ovh.battistella.sillon.data.sort.toggle

/** Shows [label] when [content] is long-pressed (or hovered with a mouse), the Material way to name an icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * Top-bar button that sorts a track list. Picking a column sorts by it in its natural direction
 * (most stars, plays or popularity first; titles A to Z); picking it again reverses it.
 */
@Composable
fun TrackSortAction(
    order: TrackOrder?,
    onOrder: (TrackOrder?) -> Unit,
    showPopularity: Boolean,
    showPlays: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    val sortLabel = stringResource(R.string.sort)
    Box {
        IconTooltip(sortLabel) {
            IconButton(onClick = { open = true }) {
                Icon(
                    Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = sortLabel,
                    tint = if (order != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_original)) },
                trailingIcon = { if (order == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = {
                    open = false
                    onOrder(null)
                },
            )
            columns(showPopularity, showPlays).forEach { (column, label) ->
                val active = order?.by == column
                val direction = when {
                    !active -> null
                    order!!.descending -> stringResource(R.string.sort_descending)
                    else -> stringResource(R.string.sort_ascending)
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(label),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        if (active) {
                            Icon(
                                if (order!!.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onOrder(order.toggle(column))
                    },
                    modifier = if (direction != null) Modifier.semantics { stateDescription = direction } else Modifier,
                )
            }
        }
    }
}

private fun columns(showPopularity: Boolean, showPlays: Boolean): List<Pair<TrackSort, Int>> = buildList {
    add(TrackSort.TITLE to R.string.sort_title)
    if (showPopularity) add(TrackSort.POPULARITY to R.string.sort_popularity)
    if (showPlays) add(TrackSort.PLAYS to R.string.sort_plays)
    add(TrackSort.STARS to R.string.sort_rating)
    add(TrackSort.DURATION to R.string.sort_duration)
}

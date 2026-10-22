package ovh.battistella.sillon.ui.screens.queue

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.playback.PlayableTrack

/** Queues tracks and says so: the toast is read by TalkBack too. */
@Composable
fun rememberQueueTracks(viewModel: QueueEditViewModel = hiltViewModel()): (QueuePlace, suspend () -> List<PlayableTrack>) -> Unit {
    val context = LocalContext.current
    val next = stringResource(R.string.queue_added_next)
    val end = stringResource(R.string.queue_added)
    return remember(viewModel, next, end) {
        { place: QueuePlace, tracks: suspend () -> List<PlayableTrack> ->
            viewModel.add(place, tracks) {
                Toast.makeText(context, if (place == QueuePlace.NEXT) next else end, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** The album's (or playlist's) overflow menu in the top bar. */
@Composable
fun QueueMenuAction(onQueue: (QueuePlace) -> Unit, viewModel: QueueEditViewModel = hiltViewModel()) {
    val canPlayNext by viewModel.canPlayNext.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        QueueMenu(open, canPlayNext, onDismiss = { open = false }, onQueue = onQueue)
    }
}

/** A track's menu, opened by a long press on its row. */
@Composable
fun TrackQueueMenu(
    onQueue: (QueuePlace) -> Unit,
    viewModel: QueueEditViewModel = hiltViewModel(),
    row: @Composable (openMenu: () -> Unit) -> Unit,
) {
    val canPlayNext by viewModel.canPlayNext.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        row { open = true }
        QueueMenu(open, canPlayNext, onDismiss = { open = false }, onQueue = onQueue)
    }
}

@Composable
private fun QueueMenu(open: Boolean, canPlayNext: Boolean, onDismiss: () -> Unit, onQueue: (QueuePlace) -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (canPlayNext) DropdownMenuItem(
            text = { Text(stringResource(R.string.queue_play_next)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null) },
            onClick = {
                onDismiss()
                onQueue(QueuePlace.NEXT)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.queue_add)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
            onClick = {
                onDismiss()
                onQueue(QueuePlace.END)
            },
        )
    }
}

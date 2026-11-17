package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.sillon.data.activity.ActiveListen
import ovh.battistella.sillon.data.activity.ActivityState
import ovh.battistella.sillon.data.activity.ListenState
import ovh.battistella.sillon.desktop.AppGraph
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.StackEntry
import ovh.battistella.sillon.desktop.ui.components.Cover
import ovh.battistella.sillon.desktop.ui.components.Message

class ActivityModel(graph: AppGraph, scope: CoroutineScope) {
    val state = graph.activity.watch().stateIn(scope, SharingStarted.Eagerly, ActivityState.Loading)
}

@Composable
fun ActivityScreen(app: AppState, entry: StackEntry) {
    val model = entry.model("activity") { scope -> ActivityModel(app.graph, scope) }
    val state by model.state.collectAsState()
    ActivityContent(state)
}

@Composable
fun ActivityContent(state: ActivityState) {
    val strings = LocalStrings.current
    when (state) {
        ActivityState.Loading -> Unit
        ActivityState.NotAllowed -> Message(strings.activityNotAllowed)
        ActivityState.Unreachable -> Message(strings.activityOffline)
        is ActivityState.Live -> if (state.listens.isEmpty()) {
            Message(strings.activityEmpty)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.listens, key = { it.id }) { ListenRow(it) }
            }
        }
    }
}

@Composable
private fun ListenRow(listen: ActiveListen) {
    val strings = LocalStrings.current
    val user = listen.user.ifBlank { strings.activityUnknownUser }
    val who = if (listen.mine) strings.activityYou(user) else user
    Row(Modifier.fillMaxWidth().widthIn(max = 760.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(listen.artwork, Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(who, listen.device).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(listen.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(listen.subtitle, strings.activityTranscoding.takeIf { listen.transcoding }).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (listen.durationMs > 0) {
                LinearProgressIndicator(progress = { listen.progress }, Modifier.fillMaxWidth().padding(top = 6.dp), drawStopIndicator = {})
            }
        }
        if (listen.state == ListenState.PAUSED) {
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Filled.Pause, strings.activityPaused, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.plex.PlexResource
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.components.Load
import ovh.battistella.sillon.desktop.ui.components.LoadContent
import ovh.battistella.sillon.desktop.ui.components.toFailed

private data class ServersUi(val servers: List<PlexResource>, val connecting: String? = null, val unreachable: String? = null)

private class ServersModel(private val repository: PlexRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<Load<ServersUi>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        scope.launch {
            _state.value = runCatching { Load.Ready(ServersUi(repository.servers())) }.getOrElse { it.toFailed() }
        }
    }

    fun select(server: PlexResource) {
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (current.connecting != null) return
        _state.value = Load.Ready(current.copy(connecting = server.name, unreachable = null))
        scope.launch {
            val ok = runCatching { repository.select(server) }.getOrDefault(false)
            if (!ok) _state.value = Load.Ready(current.copy(connecting = null, unreachable = server.name))
        }
    }

    fun signOut() {
        scope.launch { repository.signOut() }
    }
}

@Composable
fun ServersScreen(app: AppState) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val model = remember { ServersModel(app.graph.repository, scope) }
    val state by model.state.collectAsState()
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(strings.serversTitle, style = MaterialTheme.typography.headlineMedium)
                Box(Modifier.weight(1f)) {
                    LoadContent(state, model::load) { ui ->
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ui.servers.isEmpty()) Text(strings.serversEmpty, style = MaterialTheme.typography.bodyLarge)
                            ui.servers.forEach { server ->
                                ListItem(
                                    headlineContent = { Text(server.name) },
                                    supportingContent = { Text(if (server.owned) strings.serversOwned else strings.serversShared) },
                                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { model.select(server) },
                                )
                            }
                            ui.connecting?.let { Text(strings.serversConnecting(it), style = MaterialTheme.typography.bodyMedium) }
                            ui.unreachable?.let {
                                Text(strings.serversUnreachable(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                TextButton(onClick = model::signOut) { Text(strings.signOut) }
            }
        }
    }
}

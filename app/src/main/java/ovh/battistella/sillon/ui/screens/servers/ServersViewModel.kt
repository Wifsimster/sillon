package ovh.battistella.sillon.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.plex.PlexResource
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed
import javax.inject.Inject

data class ServersUi(
    val servers: List<PlexResource>,
    /** Name of the server being probed, if any. */
    val connecting: String? = null,
    /** Name of the last server that didn't answer on any address. */
    val unreachable: String? = null,
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val repository: PlexRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Load<ServersUi>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching { Load.Ready(ServersUi(repository.servers())) }
                .getOrElse { it.toFailed() }
        }
    }

    /** Probe [server]; on success the session moves the app to the library on its own. */
    fun select(server: PlexResource) {
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (current.connecting != null) return
        _state.value = Load.Ready(current.copy(connecting = server.name, unreachable = null))
        viewModelScope.launch {
            val ok = runCatching { repository.select(server) }.getOrDefault(false)
            if (!ok) {
                _state.value = Load.Ready(current.copy(connecting = null, unreachable = server.name))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}

package ovh.battistella.sillon.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.jellyfin.JellyfinPublicInfo
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository
import javax.inject.Inject

data class JellyfinSignInState(
    val server: Pair<String, JellyfinPublicInfo>? = null,
    val quickConnectCode: String? = null,
    val busy: Boolean = false,
    val error: JellyfinSignInError? = null,
)

enum class JellyfinSignInError { SERVER_NOT_FOUND, WRONG_CREDENTIALS, QUICK_CONNECT_UNAVAILABLE, OTHER }

@HiltViewModel
class JellyfinSignInViewModel @Inject constructor(
    private val repository: PlexRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(JellyfinSignInState())
    val state = _state.asStateFlow()

    private var quickConnect: Job? = null

    fun findServer(address: String) {
        if (address.isBlank()) return
        busyWhile {
            val found = runCatching { repository.findJellyfinServer(address) }.getOrNull()
            if (found == null) {
                _state.update { it.copy(error = JellyfinSignInError.SERVER_NOT_FOUND) }
            } else {
                _state.update { it.copy(server = found, error = null) }
            }
        }
    }

    fun signIn(username: String, password: String) {
        if (username.isBlank()) return
        val (url, info) = _state.value.server ?: return
        busyWhile {
            runCatching { repository.signInJellyfin(url, info, username.trim(), password) }.onFailure { e ->
                if (e is CancellationException) throw e
                val error = if (e is PlexHttpException && e.code in 400..401) {
                    JellyfinSignInError.WRONG_CREDENTIALS
                } else {
                    JellyfinSignInError.OTHER
                }
                _state.update { it.copy(error = error) }
            }
        }
    }

    fun startQuickConnect() {
        val (url, info) = _state.value.server ?: return
        quickConnect?.cancel()
        quickConnect = viewModelScope.launch {
            val request = runCatching { repository.startQuickConnect(url) }.getOrNull()
            if (request == null) {
                _state.update { it.copy(error = JellyfinSignInError.QUICK_CONNECT_UNAVAILABLE) }
                return@launch
            }
            _state.update { it.copy(quickConnectCode = request.code, error = null) }
            repeat(QUICK_CONNECT_POLLS) {
                delay(POLL_MS)
                val done = runCatching { repository.completeQuickConnectIfApproved(url, info, request.secret) }
                    .getOrDefault(false)
                if (done) return@launch
            }
            _state.update { it.copy(quickConnectCode = null) }
        }
    }

    fun changeServer() {
        quickConnect?.cancel()
        _state.value = JellyfinSignInState()
    }

    private fun busyWhile(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val QUICK_CONNECT_POLLS = 100
    }
}

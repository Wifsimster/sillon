package ovh.battistella.sillon.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import javax.inject.Inject

sealed interface SignInState {
    data object Loading : SignInState
    /** Waiting for the user to approve [code], in the browser or at plex.tv/link. */
    data class Waiting(val code: String, val authUrl: String) : SignInState
    data object Expired : SignInState
    data class Failed(val message: String) : SignInState
}

/**
 * Plex PIN sign-in: create a PIN, let the user approve it, poll until plex.tv
 * hands over the token. Once stored, the session flow moves the app on by itself.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val repository: PlexRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Loading)
    val state = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        _state.value = SignInState.Loading
        job = viewModelScope.launch {
            try {
                val pins = repository.createPins()
                _state.value = SignInState.Waiting(pins.link.code, repository.authUrl(pins.browser))
                val deadline = System.currentTimeMillis() + MAX_WAIT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(POLL_INTERVAL_MS)
                    // A dropped poll (flaky network) isn't fatal: try again next tick.
                    for (pin in listOf(pins.browser, pins.link)) {
                        val approved = runCatching { repository.completeSignInIfApproved(pin) }.getOrDefault(false)
                        if (approved) return@launch
                    }
                }
                _state.value = SignInState.Expired
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = SignInState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        /** Plex PINs live 15 minutes or more; stop polling well before draining the battery. */
        const val MAX_WAIT_MS = 10 * 60_000L
    }
}

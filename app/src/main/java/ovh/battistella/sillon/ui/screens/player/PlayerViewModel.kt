package ovh.battistella.sillon.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.mystery.MysteryLauncher
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysterySession
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackConnection,
    private val repository: PlexRepository,
    private val mysterySession: MysterySession,
    private val mysteryLauncher: MysteryLauncher,
) : ViewModel() {
    val state = playback.state

    val mystery = mysterySession.state
    val mysteryStarting = mysteryLauncher.starting
    val keeping = mysteryLauncher.keeping
    val keep = mysteryLauncher.keep

    private val _startingRadio = MutableStateFlow(false)
    val startingRadio = _startingRadio.asStateFlow()

    fun radioFromCurrent() {
        val key = state.value.currentKey?.takeIf { it != MysteryMask.HIDDEN_KEY } ?: return
        if (_startingRadio.value) return
        _startingRadio.value = true
        viewModelScope.launch {
            runCatching { repository.playable(repository.trackRadioTracks(key)) }
                .onSuccess { playback.replaceUpcoming(it) }
            _startingRadio.value = false
        }
    }

    fun reveal() = mysterySession.reveal()
    fun revealSeen() = mysterySession.markRevealSeen()
    fun keepSurprise() = mysteryLauncher.keep()
    fun anotherSurprise() = mysteryLauncher.start()

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
}

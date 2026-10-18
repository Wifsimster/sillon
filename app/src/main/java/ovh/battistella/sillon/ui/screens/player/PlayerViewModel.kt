package ovh.battistella.sillon.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import ovh.battistella.sillon.playback.PlaybackConnection
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackConnection,
    private val repository: PlexRepository,
) : ViewModel() {
    val state = playback.state

    private val _startingRadio = MutableStateFlow(false)
    val startingRadio = _startingRadio.asStateFlow()

    fun radioFromCurrent() {
        val key = state.value.currentKey ?: return
        if (_startingRadio.value) return
        _startingRadio.value = true
        viewModelScope.launch {
            runCatching { repository.playable(repository.trackRadioTracks(key)) }
                .onSuccess { playback.replaceUpcoming(it) }
            _startingRadio.value = false
        }
    }

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
}

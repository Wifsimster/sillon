package ovh.battistella.sillon.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.wear.SleeveWearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.components.Wear
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackConnection,
    private val repository: PlexRepository,
    sleeveWear: SleeveWearRepository,
) : ViewModel() {
    val state = playback.state

    val wear: StateFlow<Wear?> = combine(sleeveWear.levels, state.map { it.albumKey }.distinctUntilChanged()) { levels, album ->
        Wear.of(levels, album, intensity = PLAYER_WEAR_INTENSITY)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private companion object {
        const val PLAYER_WEAR_INTENSITY = 0.6f
    }
}

package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.repository.NeverPlayedMode
import ovh.battistella.sillon.data.settings.CachedLibraryPlays
import ovh.battistella.sillon.playback.NeverPlayedRadio
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.RadioStart
import javax.inject.Inject

enum class NeverPlayedNotice { OFFLINE, NO_OFFLINE_HISTORY, EXHAUSTED, FAILED }

@HiltViewModel
class NeverPlayedViewModel @Inject constructor(
    private val radio: NeverPlayedRadio,
    private val playback: PlaybackConnection,
) : ViewModel() {
    private val _plays = MutableStateFlow<CachedLibraryPlays?>(null)
    val plays = _plays.asStateFlow()

    private val _starting = MutableStateFlow<NeverPlayedMode?>(null)
    val starting = _starting.asStateFlow()

    private val _notice = MutableStateFlow<NeverPlayedNotice?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { radio.cachedPlays() }.getOrNull()?.let { _plays.value = it }
            runCatching { radio.refreshPlays() }.getOrNull()?.let { _plays.value = it }
        }
    }

    fun play(mode: NeverPlayedMode) {
        if (_starting.value != null) return
        _starting.value = mode
        _notice.value = null
        viewModelScope.launch {
            _notice.value = runCatching { radio.start(mode) }.fold(
                onSuccess = { start ->
                    when (start) {
                        is RadioStart.Ready -> {
                            playback.play(start.tracks)
                            if (start.offline) NeverPlayedNotice.OFFLINE else null
                        }
                        RadioStart.Exhausted -> NeverPlayedNotice.EXHAUSTED
                        RadioStart.NoOfflineHistory -> NeverPlayedNotice.NO_OFFLINE_HISTORY
                    }
                },
                onFailure = { NeverPlayedNotice.FAILED },
            )
            _starting.value = null
        }
    }
}

package ovh.battistella.sillon.ui.screens.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ovh.battistella.sillon.playback.PlaybackConnection
import javax.inject.Inject

/** Shared by the mini-player and the full player: state in, transport commands out. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackConnection,
) : ViewModel() {
    val state = playback.state

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
}

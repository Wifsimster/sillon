package ovh.battistella.sillon.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysterySession
import ovh.battistella.sillon.sonos.SonosGroup
import ovh.battistella.sillon.sonos.SonosOutput
import ovh.battistella.sillon.together.ListenTogether
import javax.inject.Inject

/** Why the Sonos speakers can't be picked right now. */
enum class OutputBlock { NOTHING_PLAYING, TOGETHER, MYSTERY }

object OutputRules {
    fun block(hasMedia: Boolean, togetherActive: Boolean, hiddenMystery: Boolean): OutputBlock? = when {
        togetherActive -> OutputBlock.TOGETHER
        hiddenMystery -> OutputBlock.MYSTERY
        !hasMedia -> OutputBlock.NOTHING_PLAYING
        else -> null
    }
}

@HiltViewModel
class OutputViewModel @Inject constructor(
    private val sonos: SonosOutput,
    playback: PlaybackConnection,
    together: ListenTogether,
    mystery: MysterySession,
) : ViewModel() {
    val output = sonos.output
    val groups = sonos.groups
    val scanning = sonos.scanning
    val notice = sonos.notice

    val block: StateFlow<OutputBlock?> = combine(playback.state, together.state, mystery.state) { player, group, sleeve ->
        val key = player.currentKey.orEmpty()
        val hidden = sleeve != null && !sleeve.revealed && (key == MysteryMask.HIDDEN_KEY || sleeve.contains(key))
        OutputRules.block(player.hasMedia, group.isActive, hidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun scan() = sonos.scan()

    fun playOn(group: SonosGroup) = sonos.playOn(group)

    fun backToPhone() = sonos.backToPhone()

    fun noticeSeen() = sonos.noticeSeen()
}

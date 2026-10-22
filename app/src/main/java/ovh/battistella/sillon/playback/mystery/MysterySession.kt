package ovh.battistella.sillon.playback.mystery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.battistella.sillon.playback.PlayableTrack
import javax.inject.Inject
import javax.inject.Singleton

data class MysteryTrack(val track: PlayableTrack, val side: Int, val number: Int)

data class Mystery(
    val albumKey: String,
    val tracks: List<MysteryTrack>,
    val offline: Boolean = false,
    val revealed: Boolean = false,
    val revealSeen: Boolean = false,
    val left: Set<String> = emptySet(),
) {
    fun contains(key: String): Boolean = tracks.any { it.track.ratingKey == key }

    fun real(key: String): PlayableTrack? = tracks.firstOrNull { it.track.ratingKey == key }?.track
}

object RevealRule {
    const val TRACKS_BEFORE_REVEAL = 3

    fun shouldReveal(tracksLeft: Int, albumEnded: Boolean): Boolean =
        albumEnded || tracksLeft >= TRACKS_BEFORE_REVEAL
}

@Singleton
class MysterySession @Inject constructor() {
    private val _state = MutableStateFlow<Mystery?>(null)
    val state: StateFlow<Mystery?> = _state.asStateFlow()

    fun start(mystery: Mystery) {
        _state.value = mystery
    }

    fun trackLeft(key: String) = _state.update { m ->
        if (m == null || m.revealed || !m.contains(key)) return@update m
        val left = m.left + key
        m.copy(left = left, revealed = RevealRule.shouldReveal(left.size, albumEnded = false))
    }

    fun albumEnded() = _state.update { m -> if (m == null || m.revealed) m else m.copy(revealed = true) }

    fun reveal() = _state.update { it?.copy(revealed = true) }

    fun markRevealSeen() = _state.update { m -> if (m?.revealed == true) m.copy(revealSeen = true) else m }

    fun clear() {
        _state.value = null
    }
}

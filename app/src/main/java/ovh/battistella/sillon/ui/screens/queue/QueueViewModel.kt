package ovh.battistella.sillon.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.playback.NeverPlayedRadio
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.SideSession
import ovh.battistella.sillon.sonos.Output
import ovh.battistella.sillon.sonos.SonosOutput
import ovh.battistella.sillon.together.ListenTogether
import javax.inject.Inject

interface QueueActions {
    fun jump(index: Int)
    fun remove(index: Int)
    fun move(from: Int, to: Int)
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queue: QueueConnection,
    together: ListenTogether,
    sonos: SonosOutput,
    private val sides: SideSession,
    private val radio: NeverPlayedRadio,
) : ViewModel(), QueueActions {
    val state: StateFlow<QueueUi> = combine(queue.snapshot, together.state, sonos.output, sides.state) { snapshot, group, output, side ->
        val current = snapshot.items.getOrNull(snapshot.currentIndex)
        val currentKey = current?.id?.substringBeforeLast('#')
        QueueModel.build(
            snapshot,
            QueueContext(
                together = group.isActive,
                speaker = (output as? Output.OnSonos)?.group?.name,
                sides = sides.machine.plan.takeIf { side.active },
                radioContinues = radio.isActive && currentKey != null && radio.owns(currentKey),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUi())

    init {
        queue.connect()
    }

    override fun jump(index: Int) = queue.jumpTo(index)

    override fun remove(index: Int) = queue.remove(index)

    override fun move(from: Int, to: Int) = queue.move(from, to)
}

enum class QueuePlace { NEXT, END }

/** "Play next" and "Add to queue" from an album, a playlist or one of their tracks. */
@HiltViewModel
class QueueEditViewModel @Inject constructor(
    private val queue: QueueConnection,
) : ViewModel() {
    fun add(place: QueuePlace, tracks: suspend () -> List<PlayableTrack>) {
        viewModelScope.launch {
            val chosen = runCatching { tracks() }.getOrDefault(emptyList())
            when (place) {
                QueuePlace.NEXT -> queue.playNext(chosen)
                QueuePlace.END -> queue.append(chosen)
            }
        }
    }
}

package ovh.battistella.sillon.playback

import kotlinx.serialization.Serializable

@Serializable
data class SideEnd(val ended: String, val next: String, val nextIndex: Int)

data class SideState(
    val active: Boolean = false,
    val current: String? = null,
    val ended: SideEnd? = null,
)

class SideMachine {
    var plan: SidePlan? = null
        private set
    var ended: SideEnd? = null
        private set

    private var queue: List<String> = emptyList()
    private var index: Int = -1
    private var shuffle: Boolean = false

    private val activePlan: SidePlan? get() = plan?.takeIf { !shuffle && it.matches(queue) }

    val state: SideState
        get() {
            val active = activePlan ?: return SideState()
            return SideState(active = true, current = active.sideAt(index)?.let(active.labels::getOrNull), ended = ended)
        }

    val pauseAtEnd: Boolean
        get() {
            val active = activePlan ?: return false
            if (ended != null || !active.isLastOfSide(index)) return false
            val side = active.sideAt(index) ?: return false
            return !active.isLastSide(side)
        }

    fun setPlan(plan: SidePlan?) {
        this.plan = plan
        ended = null
    }

    fun restore(plan: SidePlan, ended: SideEnd?) {
        this.plan = plan
        this.ended = ended
    }

    fun onQueue(queue: List<String>, index: Int, shuffle: Boolean) {
        this.queue = queue
        this.index = index
        this.shuffle = shuffle
        val end = ended ?: return
        if (activePlan != null && index != end.nextIndex && index != end.nextIndex - 1) ended = null
    }

    fun onPausedAtEndOfItem(): SideEnd? {
        val active = activePlan ?: return null
        val side = active.sideAt(index) ?: return null
        val end = when {
            active.isLastOfSide(index) && !active.isLastSide(side) -> side
            side > 0 && active.firstIndexOf(side) == index -> side - 1
            else -> return null
        }
        return SideEnd(active.labels[end], active.labels[end + 1], active.firstIndexOf(end + 1)).also { ended = it }
    }

    fun flip(): Int? {
        val end = ended ?: return null
        ended = null
        return end.nextIndex.takeIf { activePlan != null }
    }
}

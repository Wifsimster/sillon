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

    /**
     * True while listening together: the group's queue and pauses belong to
     * the Jellyfin SyncPlay group, so sides are off and any ended side is dropped.
     */
    var grouped: Boolean = false
        set(value) {
            field = value
            if (value) ended = null
        }

    /**
     * The tracks the player will skip (rated 1 star): a side ends after its last track that
     * will really play, so skipping never runs past the end of a side.
     */
    var skipped: (Int) -> Boolean = { false }

    private val activePlan: SidePlan? get() = plan?.takeIf { !grouped && !shuffle && it.matches(queue) }

    val state: SideState
        get() {
            val active = activePlan ?: return SideState()
            return SideState(active = true, current = active.sideAt(index)?.let(active.labels::getOrNull), ended = ended)
        }

    val pauseAtEnd: Boolean
        get() {
            val active = activePlan ?: return false
            if (ended != null || !endsSide(active, index)) return false
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
        val plan = activePlan ?: return
        val stillThere = index == end.nextIndex ||
            (index in 0 until end.nextIndex && plan.sideAt(index) == plan.sideAt(end.nextIndex - 1) && endsSide(plan, index))
        if (!stillThere) ended = null
    }

    private fun endsSide(plan: SidePlan, index: Int): Boolean {
        val side = plan.sideAt(index) ?: return false
        var i = index + 1
        while (plan.sideAt(i) == side) {
            if (!skipped(i)) return false
            i++
        }
        return true
    }

    fun onPausedAtEndOfItem(): SideEnd? {
        val active = activePlan ?: return null
        val side = active.sideAt(index) ?: return null
        val end = when {
            endsSide(active, index) && !active.isLastSide(side) -> side
            side > 0 && active.firstIndexOf(side) == index -> side - 1
            else -> return null
        }
        return SideEnd(active.labels[end], active.labels[end + 1], active.firstIndexOf(end + 1)).also { ended = it }
    }

    fun onSeek() {
        ended = null
    }

    fun flip(): Int? {
        val end = ended ?: return null
        ended = null
        return end.nextIndex.takeIf { activePlan != null }
    }
}

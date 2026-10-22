package ovh.battistella.sillon.ui.screens.queue

import androidx.media3.common.MediaItem
import ovh.battistella.sillon.playback.SidePlan
import ovh.battistella.sillon.playback.mystery.MysteryMask
import kotlin.math.roundToInt

/**
 * One track of Media3's queue, as the sheet shows it. A track of a mystery sleeve not yet
 * revealed keeps only its number: [title], [artist] and [artworkUri] stay empty.
 */
data class QueueItem(
    /** Stable while the queue changes around it: the LazyColumn key and the swipe state. */
    val id: String,
    /** Where it sits in Media3's list: what jump, remove and move act on. */
    val index: Int,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val hidden: Boolean = false,
    val trackNumber: Int? = null,
) {
    companion object {
        fun of(items: List<MediaItem>): List<QueueItem> {
            val seen = mutableMapOf<String, Int>()
            return items.mapIndexed { index, item ->
                val occurrence = (seen[item.mediaId] ?: 0) + 1
                seen[item.mediaId] = occurrence
                of(index, item, "${item.mediaId}#$occurrence")
            }
        }

        fun of(index: Int, item: MediaItem, id: String): QueueItem {
            val metadata = item.mediaMetadata
            if (MysteryMask.isMasked(item)) {
                return QueueItem(id, index, title = "", artist = null, artworkUri = null, hidden = true, trackNumber = metadata.trackNumber)
            }
            return QueueItem(
                id = id,
                index = index,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() },
                artworkUri = metadata.artworkUri?.toString(),
                trackNumber = metadata.trackNumber,
            )
        }
    }
}

/** The queue as Media3 holds it: the items, their play order (shuffle included) and where playback is. */
data class QueueSnapshot(
    val items: List<QueueItem> = emptyList(),
    val order: List<Int> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
)

/** Where the queue plays and what else is going on: decides what the sheet lets the listener change. */
data class QueueContext(
    val together: Boolean = false,
    /** The Sonos group playing, or null on the phone. */
    val speaker: String? = null,
    /** The record's sides, only while listening by side is active for this queue. */
    val sides: SidePlan? = null,
    /** The never-played radio is playing and will add tracks as it goes. */
    val radioContinues: Boolean = false,
)

enum class QueueLock {
    NONE,

    /** The group's queue belongs to the Jellyfin SyncPlay session. */
    TOGETHER,

    /** The speaker shuffles in an order Sillon doesn't know. */
    SPEAKER_SHUFFLE,
}

/** The end of a side falls right after this track: the turntable stops there. */
data class SideBreak(val ended: String, val next: String)

data class QueueRow(val item: QueueItem, val sideBreak: SideBreak? = null)

data class QueueUi(
    val current: QueueRow? = null,
    val upNext: List<QueueRow> = emptyList(),
    val played: List<QueueRow> = emptyList(),
    val lock: QueueLock = QueueLock.NONE,
    /** Moving tracks needs the list order: off while shuffling. */
    val canReorder: Boolean = false,
    val radioContinues: Boolean = false,
    val sidesActive: Boolean = false,
    val speaker: String? = null,
) {
    val editable: Boolean get() = lock == QueueLock.NONE
}

object QueueModel {
    fun build(snapshot: QueueSnapshot, context: QueueContext): QueueUi {
        val lock = when {
            context.together -> QueueLock.TOGETHER
            context.speaker != null && snapshot.shuffle -> QueueLock.SPEAKER_SHUFFLE
            else -> QueueLock.NONE
        }
        val items = snapshot.items
        val order = snapshot.order.filter { it in items.indices }
        val position = order.indexOf(snapshot.currentIndex)
        if (position < 0) return QueueUi(lock = lock, speaker = context.speaker)
        fun row(index: Int) = QueueRow(items[index], context.sides?.let { sideBreak(it, index) })
        return QueueUi(
            current = row(snapshot.currentIndex),
            upNext = order.drop(position + 1).map(::row),
            played = order.take(position).map(::row),
            lock = lock,
            canReorder = lock == QueueLock.NONE && !snapshot.shuffle,
            radioContinues = context.radioContinues,
            sidesActive = context.sides != null,
            speaker = context.speaker,
        )
    }

    private fun sideBreak(plan: SidePlan, index: Int): SideBreak? {
        val side = plan.sideAt(index) ?: return null
        if (!plan.isLastOfSide(index) || plan.isLastSide(side)) return null
        return SideBreak(plan.labels[side], plan.labels.getOrNull(side + 1) ?: return null)
    }

    /**
     * The Media3 move for the up-next row at [from] going to the row at [to]: (item index, new index),
     * or null when nothing moves. Only meaningful when [QueueUi.canReorder] (list order = play order).
     */
    fun move(upNext: List<QueueRow>, from: Int, to: Int): Pair<Int, Int>? {
        if (from !in upNext.indices || to !in upNext.indices || from == to) return null
        return upNext[from].item.index to upNext[to].item.index
    }

    /** How many rows a drag of [offsetPx] crosses, for rows [rowPx] tall. */
    fun dragSteps(offsetPx: Float, rowPx: Float): Int =
        if (rowPx <= 0f) 0 else (offsetPx / rowPx).roundToInt()
}

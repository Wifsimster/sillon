package ovh.battistella.sillon.data.sort

import java.text.Collator

/** A column a track list can be sorted by; [descendingFirst] is the direction a first tap or click picks. */
enum class TrackSort(val descendingFirst: Boolean) {
    NUMBER(false),
    TITLE(false),
    POPULARITY(true),
    PLAYS(true),
    STARS(true),
    DURATION(false),
}

data class TrackOrder(val by: TrackSort, val descending: Boolean) {
    /** How the order is written to the settings; read back with [decodeTrackOrder]. */
    fun encode(): String = "${by.name}:${if (descending) "desc" else "asc"}"
}

fun decodeTrackOrder(value: String?): TrackOrder? {
    val (column, direction) = value?.split(':')?.takeIf { it.size == 2 } ?: return null
    val by = TrackSort.entries.firstOrNull { it.name == column } ?: return null
    return when (direction) {
        "desc" -> TrackOrder(by, descending = true)
        "asc" -> TrackOrder(by, descending = false)
        else -> null
    }
}

/** Next state when a column is picked: natural direction, then reversed, then back to the original order. */
fun TrackOrder?.next(column: TrackSort): TrackOrder? = when {
    this == null || by != column -> TrackOrder(column, column.descendingFirst)
    descending == column.descendingFirst -> copy(descending = !descending)
    else -> null
}

/** Picking a column from a menu: natural direction, and each further pick reverses it (no "off" state). */
fun TrackOrder?.toggle(column: TrackSort): TrackOrder =
    if (this == null || by != column) TrackOrder(column, column.descendingFirst) else copy(descending = !descending)

/** What a track list exposes so it can be sorted the same way on every platform. */
interface SortableTrack {
    val number: Int?
    val title: String
    val popularity: Float?
    val plays: Int?
    val durationMs: Long?

    /** Rating in stars; null or 0 means unrated. */
    val sortStars: Float?
}

private val titleCollator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** Sorts a copy of the list; tracks without a value for the column always come last. Ties keep their original order. */
fun <T : SortableTrack> List<T>.sortedBy(order: TrackOrder?): List<T> {
    if (order == null) return this
    val by = order.by
    return sortedWith { a, b ->
        val missing = compareValues(a.isMissing(by), b.isMissing(by))
        when {
            missing != 0 -> missing
            a.isMissing(by) -> 0
            order.descending -> compareBy(by, b, a)
            else -> compareBy(by, a, b)
        }
    }
}

private fun compareBy(by: TrackSort, a: SortableTrack, b: SortableTrack): Int = when (by) {
    TrackSort.NUMBER -> compareValues(a.number, b.number)
    TrackSort.TITLE -> titleCollator.compare(a.title, b.title)
    TrackSort.POPULARITY -> compareValues(a.popularity, b.popularity)
    TrackSort.PLAYS -> compareValues(a.plays, b.plays)
    TrackSort.STARS -> compareValues(a.sortStars, b.sortStars)
    TrackSort.DURATION -> compareValues(a.durationMs, b.durationMs)
}

private fun SortableTrack.isMissing(by: TrackSort): Boolean = when (by) {
    TrackSort.NUMBER -> number == null
    TrackSort.TITLE -> false
    TrackSort.POPULARITY -> popularity == null
    TrackSort.PLAYS -> plays == null
    TrackSort.STARS -> (sortStars ?: 0f) <= 0f
    TrackSort.DURATION -> durationMs == null
}

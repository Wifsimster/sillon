package ovh.battistella.sillon.playback

import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexMetadata
import kotlin.math.abs

data class SideTrack(
    val key: String,
    val durationMs: Long?,
    val disc: Int? = null,
    val title: String = "",
)

data class Side(val label: String, val keys: List<String>, val durationMs: Long)

object AlbumSides {
    const val MIN_ALBUM_MS = 12 * 60_000L
    const val MIN_TRACKS = 4

    fun split(tracks: List<SideTrack>): List<Side> {
        if (tracks.size < MIN_TRACKS || tracks.sumOf { it.durationMs ?: 0L } < MIN_ALBUM_MS) return emptyList()
        val groups = byMarkers(tracks) ?: byDiscs(tracks) ?: halves(tracks).map { null to it }
        if (groups.size < 2) return emptyList()
        return groups.mapIndexed { i, (marker, group) ->
            Side(marker ?: label(i), group.map { it.key }, group.sumOf { it.durationMs ?: 0L })
        }
    }

    fun of(tracks: List<PlexMetadata>): List<Side> =
        split(tracks.map { SideTrack(it.ratingKey, it.duration, it.parentIndex, it.title) })

    fun label(index: Int): String = if (index < 26) ('A' + index).toString() else (index + 1).toString()

    private fun byMarkers(tracks: List<SideTrack>): List<Pair<String?, List<SideTrack>>>? {
        val markers = tracks.map { sideMarker(it.title) ?: return null }
        if (markers.zipWithNext().any { (a, b) -> b < a }) return null
        val groups = tracks.indices.groupBy { markers[it] }
        if (groups.size < 2) return null
        return groups.map { (marker, indices) -> marker.toString() to indices.map { tracks[it] } }
    }

    private fun byDiscs(tracks: List<SideTrack>): List<Pair<String?, List<SideTrack>>>? {
        val discs = tracks.map { it.disc ?: return null }
        if (discs.distinct().size < 2 || discs.zipWithNext().any { (a, b) -> b < a }) return null
        return tracks.groupBy { it.disc }.values.flatMap { disc ->
            if (disc.size >= 2 && disc.sumOf { it.durationMs ?: 0L } >= MIN_ALBUM_MS) halves(disc) else listOf(disc)
        }.map { null to it }
    }

    internal fun halves(tracks: List<SideTrack>): List<List<SideTrack>> {
        if (tracks.size < 2) return listOf(tracks)
        val total = tracks.sumOf { it.durationMs ?: 0L }
        var elapsed = 0L
        var cut = 1
        var best = Long.MAX_VALUE
        for (i in 1 until tracks.size) {
            elapsed += tracks[i - 1].durationMs ?: 0L
            val distance = abs(2 * elapsed - total)
            if (distance < best) {
                best = distance
                cut = i
            }
        }
        return listOf(tracks.subList(0, cut), tracks.subList(cut, tracks.size))
    }

    private val SIDE_WORD = Regex("""(?i)\b(?:side|face)\s+([a-z])\b""")
    private val VINYL_POSITION = Regex("""^\s*([A-Z])\d{1,2}(?:[\s.:)\-]|$)""")

    internal fun sideMarker(title: String): Char? {
        VINYL_POSITION.find(title)?.let { return it.groupValues[1].single() }
        return SIDE_WORD.find(title)?.groupValues?.get(1)?.single()?.uppercaseChar()
    }
}

@Serializable
data class SidePlan(val keys: List<String>, val sides: List<Int>, val labels: List<String>) {
    fun sideAt(index: Int): Int? = sides.getOrNull(index)

    fun isLastSide(side: Int): Boolean = side >= labels.lastIndex

    fun isLastOfSide(index: Int): Boolean {
        val side = sideAt(index) ?: return false
        return sideAt(index + 1) != side
    }

    fun firstIndexOf(side: Int): Int = sides.indexOf(side)

    fun matches(queue: List<String>): Boolean = queue == keys

    companion object {
        fun of(sides: List<Side>, queue: List<String>): SidePlan? {
            if (sides.size < 2) return null
            val sideOfKey = sides.flatMapIndexed { i, side -> side.keys.map { it to i } }.toMap()
            val indices = queue.map { sideOfKey[it] ?: return null }
            if (indices.zipWithNext().any { (a, b) -> b < a }) return null
            val used = indices.distinct()
            if (used.size < 2) return null
            return SidePlan(queue, indices.map(used::indexOf), used.map { sides[it].label })
        }
    }
}

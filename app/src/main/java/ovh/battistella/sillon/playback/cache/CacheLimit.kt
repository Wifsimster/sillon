package ovh.battistella.sillon.playback.cache

/** The sizes offered for the listening cache, and the guard against filling the phone. */
object CacheLimit {
    const val GB = 1_000_000_000L

    val CHOICES_GB = listOf(1, 2, 5, 10)
    const val DEFAULT_GB = 2

    /** Free space the cache always leaves to the phone. */
    const val RESERVE_BYTES = 1 * GB

    fun bytes(gb: Int): Long = gb * GB

    fun sanitize(gb: Int?): Int = gb?.takeIf { it in CHOICES_GB } ?: DEFAULT_GB

    /** The most the cache can hold: what it holds now plus the free space, less the reserve. */
    fun room(usedBytes: Long, freeBytes: Long): Long = (usedBytes + freeBytes - RESERVE_BYTES).coerceAtLeast(0)

    /** The limit actually applied: the chosen size, cut down when the phone runs short of space. */
    fun effective(chosenGb: Int, usedBytes: Long, freeBytes: Long): Long =
        minOf(bytes(chosenGb), room(usedBytes, freeBytes))

    fun fits(gb: Int, usedBytes: Long, freeBytes: Long): Boolean = bytes(gb) <= room(usedBytes, freeBytes)
}

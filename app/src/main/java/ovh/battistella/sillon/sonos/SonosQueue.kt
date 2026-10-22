package ovh.battistella.sillon.sonos

/** Which part of Sillon's queue goes to the speaker, and where playback lands in it. */
object SonosQueue {
    /** Enough for any album or radio; a whole-library shuffle is cut around the current track. */
    const val MAX_TRACKS = 200

    /** The indices sent: up to [max] tracks, a quarter of them before [index]. */
    fun window(size: Int, index: Int, max: Int = MAX_TRACKS): IntRange {
        if (size <= 0) return IntRange.EMPTY
        if (size <= max) return 0 until size
        val start = (index - max / 4).coerceIn(0, size - max)
        return start until start + max
    }

    /**
     * Where [index] (in the sent list) lands once the tracks the speaker can't play are left out:
     * [kept] holds the sent indices that stayed, in order. The next kept track when [index] went.
     */
    fun realign(kept: List<Int>, index: Int): Int {
        if (kept.isEmpty()) return 0
        val at = kept.indexOfFirst { it >= index }
        return if (at >= 0) at else kept.lastIndex
    }

    /**
     * Where the phone picks up in its own queue ([phone], keys) when the speaker played [sent]
     * and stands at [index] of it: null when [sent] isn't a part of [phone] in order (the queue
     * changed while on Sonos), and the phone then takes the speaker's queue instead.
     */
    fun phoneIndex(phone: List<String>, sent: List<String>, index: Int): Int? {
        if (index !in sent.indices) return null
        var p = 0
        var found: Int? = null
        sent.forEachIndexed { i, key ->
            while (p < phone.size && phone[p] != key) p++
            if (p == phone.size) return null
            if (i == index) found = p
            p++
        }
        return found
    }
}

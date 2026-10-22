package ovh.battistella.sillon.ui.share

import androidx.compose.runtime.Immutable

enum class CardFormat(val width: Int, val height: Int) {
    SQUARE(1080, 1080),
    STORY(1080, 1920),
}

enum class CardTemplate { VINYL, LABEL, POLAROID }

/** What a card is made from: text only. The cover arrives later as pixels, never as a URL. */
@Immutable
data class CardText(
    val title: String,
    val artist: String? = null,
    val detail: String? = null,
    val stars: Float? = null,
    val note: String? = null,
    val lyrics: List<String> = emptyList(),
) {
    val lines: List<String> get() = listOfNotNull(title, artist, detail, note) + lyrics
}

/** Where the share action was started from; the card sheet resolves the rest. */
@Immutable
data class ShareSource(
    val kind: Kind,
    val key: String?,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val artworkUri: String? = null,
) {
    enum class Kind { TRACK, ALBUM, ARTIST }
}

object CardRules {
    const val NOTE_MAX = 140
    const val LYRICS_MAX = 2

    /** Keeps the personal note on one paragraph and at most [NOTE_MAX] characters, never splitting an emoji. */
    fun note(raw: String?): String? {
        val text = raw.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        return limit(text).trimEnd()
    }

    /** Cuts what the user types at [NOTE_MAX] code points, so the counter and the card agree. */
    fun limit(text: String): String {
        if (text.codePointCount(0, text.length) <= NOTE_MAX) return text
        var end = text.offsetByCodePoints(0, NOTE_MAX)
        // Keep a joined emoji (ZWJ sequence, variation selector, skin tone, keycap) whole or drop it.
        while (end > 0) {
            end = when {
                end < text.length && extendsPrevious(text.codePointAt(end)) -> text.offsetByCodePoints(end, -1)
                text.codePointBefore(end) == ZWJ -> text.offsetByCodePoints(end, -1)
                else -> break
            }
        }
        return text.substring(0, end)
    }

    private fun extendsPrevious(codePoint: Int): Boolean =
        codePoint == ZWJ || codePoint == COMBINING_KEYCAP || codePoint in VARIATION_SELECTORS || codePoint in SKIN_TONES

    /** Toggles one lyric line, keeping at most [LYRICS_MAX] in the order they appear in the song. */
    fun toggleLyric(chosen: List<Int>, index: Int): List<Int> = when {
        index in chosen -> chosen - index
        chosen.size >= LYRICS_MAX -> chosen
        else -> (chosen + index).sorted()
    }

    fun stars(value: Float?): Float? = value?.takeIf { it > 0f }?.coerceAtMost(5f)?.let { Math.round(it * 2) / 2f }

    /** "1971 – 1994" for an artist, from the years of their albums. */
    fun yearSpan(years: List<Int>): String? {
        val known = years.filter { it > 0 }
        if (known.isEmpty()) return null
        val first = known.min()
        val last = known.max()
        return if (first == last) first.toString() else "$first – $last"
    }

    private const val ZWJ = 0x200D
    private const val COMBINING_KEYCAP = 0x20E3
    private val VARIATION_SELECTORS = 0xFE00..0xFE0F
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
}

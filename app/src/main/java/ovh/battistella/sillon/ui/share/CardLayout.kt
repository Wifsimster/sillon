package ovh.battistella.sillon.ui.share

import kotlin.math.max
import kotlin.math.min

enum class CardFont { DISPLAY, MEDIUM, REGULAR }

/** Measures a run of text in pixels; the renderer uses Android's Paint, tests a fixed-width font. */
fun interface TextMeasure {
    fun width(text: String, font: CardFont, sizePx: Float): Float
}

data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun overlaps(other: Box): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun inside(width: Int, height: Int): Boolean =
        left >= 0f && top >= 0f && right <= width + EPSILON && bottom <= height + EPSILON

    private companion object {
        const val EPSILON = 0.5f
    }
}

enum class TextRole { TITLE, ARTIST, DETAIL, NOTE, LYRICS }

enum class Align { START, CENTER }

data class TextBlock(
    val role: TextRole,
    val lines: List<String>,
    val font: CardFont,
    val size: Float,
    val lineHeight: Float,
    val box: Box,
    val align: Align,
    /** Lyric lines are indented after an amber rule. */
    val indent: Float = 0f,
)

data class CardLayout(
    val format: CardFormat,
    val template: CardTemplate,
    val cover: Box,
    /** The record behind the cover (Vinyl) or around it (Label). */
    val disc: Box?,
    /** The white frame of the Polaroid. */
    val frame: Box?,
    val texts: List<TextBlock>,
    val stars: Box?,
    val signature: Box,
    /** 0.5 to 5 stars, drawn in [stars]. */
    val rating: Float? = null,
) {
    val width: Int get() = format.width
    val height: Int get() = format.height
    val text: List<String> get() = texts.flatMap { it.lines }
}

/**
 * Places everything on the card. Pure geometry: the same code draws the card and runs in the unit tests.
 *
 * The text is laid out first; the cover takes the room that is left, between a large maximum and a
 * floor. When the text would push the cover under that floor, the note, then the lyrics, then the
 * title give up lines until it fits.
 */
object CardLayoutEngine {
    fun layout(format: CardFormat, template: CardTemplate, text: CardText, measure: TextMeasure): CardLayout {
        val spec = Spec.of(format)
        var limits = Limits(title = spec.titleLines, note = spec.noteLines, lyric = 2, artist = 2)
        while (true) {
            val attempt = attempt(format, template, text, measure, spec, limits)
            if (attempt != null) return attempt.copy(rating = text.stars)
            limits = limits.tighter()
                ?: return attempt(format, template, text, measure, spec, limits, force = true)!!.copy(rating = text.stars)
        }
    }

    private data class Limits(val title: Int, val note: Int, val lyric: Int, val artist: Int) {
        fun tighter(): Limits? = when {
            note > 1 -> copy(note = note - 1)
            lyric > 1 -> copy(lyric = 1)
            title > 1 -> copy(title = title - 1)
            artist > 1 -> copy(artist = 1)
            else -> null
        }
    }

    private class Spec(
        val margin: Float,
        val titleMax: Float,
        val titleMin: Float,
        val titleLines: Int,
        val artist: Float,
        val detail: Float,
        val note: Float,
        val noteLines: Int,
        val lyric: Float,
        val starHeight: Float,
        val signatureHeight: Float,
        val coverFloor: Float,
    ) {
        companion object {
            fun of(format: CardFormat) = when (format) {
                CardFormat.SQUARE -> Spec(
                    margin = 64f, titleMax = 64f, titleMin = 40f, titleLines = 2, artist = 36f, detail = 30f,
                    note = 32f, noteLines = 3, lyric = 30f, starHeight = 32f, signatureHeight = 40f, coverFloor = 300f,
                )
                CardFormat.STORY -> Spec(
                    margin = 80f, titleMax = 84f, titleMin = 52f, titleLines = 3, artist = 46f, detail = 38f,
                    note = 42f, noteLines = 5, lyric = 38f, starHeight = 44f, signatureHeight = 52f, coverFloor = 520f,
                )
            }
        }
    }

    private fun attempt(
        format: CardFormat,
        template: CardTemplate,
        text: CardText,
        measure: TextMeasure,
        spec: Spec,
        limits: Limits,
        force: Boolean = false,
    ): CardLayout? {
        val w = format.width.toFloat()
        val h = format.height.toFloat()
        val m = spec.margin
        val signature = Box(0f, h - m * 0.6f - spec.signatureHeight, w, spec.signatureHeight)
        val top = m
        val bottom = signature.top - m * 0.4f
        val room = bottom - top

        return when (template) {
            CardTemplate.VINYL -> {
                // The cover sits on the left; the record slides out on its right by 34 % of the cover.
                val textWidth = w - 2 * m
                val stack = stack(text, textWidth, measure, spec, limits, Align.START)
                val gap = m * 0.75f
                val maxCover = (w - 2 * m) / VINYL_SPAN
                val cover = min(maxCover, room - gap - stack.height)
                if (cover < spec.coverFloor && !force) return null
                val c = max(cover, spec.coverFloor)
                val groupTop = top + max(0f, (room - (c + gap + stack.height)) / 2f) * centring(format)
                val coverBox = Box(m, groupTop, c, c)
                val discSize = c * DISC_RATIO
                val discCenterX = m + c * VINYL_SPAN - discSize / 2f
                val disc = Box(discCenterX - discSize / 2f, groupTop + (c - discSize) / 2f, discSize, discSize)
                val placed = stack.place(m, coverBox.bottom + gap)
                CardLayout(format, template, coverBox, disc, null, placed.blocks, placed.stars, signature)
            }
            CardTemplate.LABEL -> {
                // The cover is the round label of a record, ringed in amber, the grooves around it.
                val textWidth = w - 2 * m
                val stack = stack(text, textWidth, measure, spec, limits, Align.CENTER)
                val gap = m * 0.6f
                val maxDisc = w - m
                val disc = min(maxDisc, room - gap - stack.height)
                if (disc * LABEL_COVER < spec.coverFloor && !force) return null
                val d = max(disc, spec.coverFloor / LABEL_COVER)
                val groupTop = top + max(0f, (room - (d + gap + stack.height)) / 2f) * centring(format)
                val discBox = Box((w - d) / 2f, groupTop, d, d)
                val c = d * LABEL_COVER
                val coverBox = Box((w - c) / 2f, groupTop + (d - c) / 2f, c, c)
                val placed = stack.place(m, discBox.bottom + gap)
                CardLayout(format, template, coverBox, discBox, null, placed.blocks, placed.stars, signature)
            }
            CardTemplate.POLAROID -> {
                // A print on cream: thin border on three sides, the caption (title, artist, year, stars) in the
                // wide bottom border. The note and the lyrics are written on the cream under the print.
                val tail = stack(text, w - 2 * m, measure, spec, limits, Align.CENTER, Part.WORDS)
                val tailGap = if (tail.items.isEmpty()) 0f else m * 0.5f
                var c = (w - 2 * m) / (1 + 2 * POLAROID_BORDER)
                var caption = stack(text, c, measure, spec, limits, Align.START, Part.HEAD)
                fun height() = c * (1 + POLAROID_BORDER * 3.8f) + caption.height + tailGap + tail.height
                while (height() > room && c > spec.coverFloor) {
                    c = max(spec.coverFloor, c - STEP)
                    caption = stack(text, c, measure, spec, limits, Align.START, Part.HEAD)
                }
                if (height() > room && !force) return null
                val border = c * POLAROID_BORDER
                val frameWidth = c + 2 * border
                val frameHeight = c * (1 + POLAROID_BORDER * 3.8f) + caption.height
                val frameTop = top + max(0f, (room - height()) / 2f)
                val frame = Box((w - frameWidth) / 2f, frameTop, frameWidth, frameHeight)
                val coverBox = Box(frame.left + border, frame.top + border, c, c)
                val head = caption.place(coverBox.left, coverBox.bottom + border * 1.2f)
                val words = tail.place(m, frame.bottom + tailGap)
                CardLayout(format, template, coverBox, null, frame, head.blocks + words.blocks, head.stars, signature)
            }
        }
    }

    /** Which lines of text a stack holds: the music's (title to stars), the user's (note, lyrics), or both. */
    private enum class Part { ALL, HEAD, WORDS }

    /** Square cards keep the cover at the top; stories centre the whole group in the frame. */
    private fun centring(format: CardFormat): Float = if (format == CardFormat.STORY) 1f else 0f

    private class Item(val block: TextBlock?, val starHeight: Float, val gapBefore: Float) {
        val height: Float get() = block?.box?.height ?: starHeight
    }

    private class Placed(val blocks: List<TextBlock>, val stars: Box?)

    private class Stack(val items: List<Item>, val width: Float, val align: Align) {
        val height: Float get() = items.sumOf { (it.gapBefore + it.height).toDouble() }.toFloat()

        fun place(left: Float, top: Float): Placed {
            var y = top
            val blocks = mutableListOf<TextBlock>()
            var stars: Box? = null
            items.forEach { item ->
                y += item.gapBefore
                val block = item.block
                if (block != null) {
                    blocks += block.copy(box = Box(left, y, width, block.box.height))
                } else {
                    val starsWidth = item.starHeight * STAR_ROW
                    val x = if (align == Align.CENTER) left + (width - starsWidth) / 2f else left
                    stars = Box(x, y, starsWidth, item.starHeight)
                }
                y += item.height
            }
            return Placed(blocks, stars)
        }
    }

    private fun stack(
        text: CardText,
        width: Float,
        measure: TextMeasure,
        spec: Spec,
        limits: Limits,
        align: Align,
        part: Part = Part.ALL,
    ): Stack {
        val items = mutableListOf<Item>()
        fun add(block: TextBlock?, gap: Float) {
            if (block != null && block.lines.isNotEmpty()) items += Item(block, 0f, if (items.isEmpty()) 0f else gap)
        }

        if (part != Part.WORDS) {
            add(title(text.title, width, measure, spec, limits.title, align), 0f)
            text.artist?.takeIf { it.isNotBlank() }?.let {
                add(block(TextRole.ARTIST, it, CardFont.MEDIUM, spec.artist, width, limits.artist, measure, align), spec.artist * 0.3f)
            }
            text.detail?.takeIf { it.isNotBlank() }?.let {
                add(block(TextRole.DETAIL, it, CardFont.REGULAR, spec.detail, width, 1, measure, align), spec.detail * 0.2f)
            }
            if (text.stars != null) items += Item(null, spec.starHeight, if (items.isEmpty()) 0f else spec.starHeight * 0.6f)
        }
        if (part == Part.HEAD) return Stack(items, width, align)
        text.note?.takeIf { it.isNotBlank() }?.let {
            add(block(TextRole.NOTE, it, CardFont.REGULAR, spec.note, width, limits.note, measure, align), spec.note * 0.8f)
        }
        if (text.lyrics.isNotEmpty()) {
            // Left-aligned lyrics hang from an amber rule; centred ones stand on their own.
            val indent = if (align == Align.START) spec.lyric * 0.7f else 0f
            val lines = text.lyrics.take(CardRules.LYRICS_MAX).flatMap {
                wrap(it, width - indent, CardFont.REGULAR, spec.lyric, limits.lyric, measure)
            }
            val lineHeight = spec.lyric * LINE
            add(
                TextBlock(TextRole.LYRICS, lines, CardFont.REGULAR, spec.lyric, lineHeight, Box(0f, 0f, width, lines.size * lineHeight), align, indent),
                spec.lyric * 0.8f,
            )
        }
        return Stack(items, width, align)
    }

    private fun title(title: String, width: Float, measure: TextMeasure, spec: Spec, maxLines: Int, align: Align): TextBlock {
        var size = spec.titleMax
        while (size > spec.titleMin) {
            val lines = wrap(title, width, CardFont.DISPLAY, size, Int.MAX_VALUE, measure)
            if (lines.size <= maxLines) return block(TextRole.TITLE, lines, CardFont.DISPLAY, size, width, align, TITLE_LINE)
            size -= TITLE_STEP
        }
        val lines = wrap(title, width, CardFont.DISPLAY, spec.titleMin, maxLines, measure)
        return block(TextRole.TITLE, lines, CardFont.DISPLAY, spec.titleMin, width, align, TITLE_LINE)
    }

    private fun block(
        role: TextRole,
        text: String,
        font: CardFont,
        size: Float,
        width: Float,
        maxLines: Int,
        measure: TextMeasure,
        align: Align,
    ) = block(role, wrap(text, width, font, size, maxLines, measure), font, size, width, align, LINE)

    private fun block(role: TextRole, lines: List<String>, font: CardFont, size: Float, width: Float, align: Align, line: Float): TextBlock {
        val lineHeight = size * line
        return TextBlock(role, lines, font, size, lineHeight, Box(0f, 0f, width, lines.size * lineHeight), align)
    }

    /** Greedy word wrap; a word wider than the line is broken; the last kept line ends with "…" when text is cut. */
    fun wrap(text: String, width: Float, font: CardFont, size: Float, maxLines: Int, measure: TextMeasure): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        fun fits(s: String) = measure.width(s, font, size) <= width
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (fits(candidate)) {
                current = candidate
                continue
            }
            if (current.isNotEmpty()) lines += current
            current = word
            while (!fits(current)) {
                var cut = current.length - 1
                while (cut > 1 && !fits(current.substring(0, cut))) cut--
                if (Character.isHighSurrogate(current[cut - 1])) cut--
                lines += current.substring(0, cut)
                current = current.substring(cut)
            }
        }
        if (current.isNotEmpty()) lines += current
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        kept[kept.lastIndex] = ellipsize(kept.last(), width, font, size, measure)
        return kept
    }

    private fun ellipsize(line: String, width: Float, font: CardFont, size: Float, measure: TextMeasure): String {
        var s = line
        while (s.isNotEmpty() && measure.width("$s$ELLIPSIS", font, size) > width) {
            s = s.dropLast(if (s.length >= 2 && Character.isLowSurrogate(s.last())) 2 else 1)
        }
        return s.trimEnd() + ELLIPSIS
    }

    const val ELLIPSIS = "…"
    const val STAR_ROW = 5.6f
    private const val LINE = 1.25f
    private const val TITLE_LINE = 1.12f
    private const val TITLE_STEP = 4f
    private const val STEP = 8f
    const val VINYL_SPAN = 1.34f
    const val DISC_RATIO = 0.96f
    const val LABEL_COVER = 0.74f
    const val POLAROID_BORDER = 0.05f
}

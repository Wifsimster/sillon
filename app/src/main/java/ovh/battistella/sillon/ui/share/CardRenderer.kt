package ovh.battistella.sillon.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.spiralPoints
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** The card's colours: the brand's, washed with the cover's average colour like the player. */
data class CardPalette(val tint: Int) {
    companion object {
        const val VINYL = 0xFF15120F.toInt()
        const val GROOVE = 0xFF2A241E.toInt()
        const val SLEEVE = 0xFFF6EFE4.toInt()
        const val LABEL = 0xFFE8A33D.toInt()
        const val LABEL_ON_CREAM = 0xFF8A5100.toInt()
        const val PRINT = 0xFFFFFCF6.toInt()

        val Default = CardPalette(GROOVE)

        /** Average colour of the cover, as the player computes it, on a few dozen pixels. */
        fun of(cover: Bitmap?): CardPalette {
            if (cover == null) return Default
            val small = cover.scale(SAMPLE, SAMPLE)
            val pixels = IntArray(SAMPLE * SAMPLE)
            small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
            if (small !== cover) small.recycle()
            var r = 0L
            var g = 0L
            var b = 0L
            pixels.forEach { p ->
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            val n = pixels.size
            return CardPalette(Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()))
        }

        private const val SAMPLE = 24
    }

    /** The tint, darkened enough that cream text stays readable on it. */
    val deep: Int
        get() {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(tint, hsl)
            hsl[2] = hsl[2].coerceAtMost(0.32f)
            return ColorUtils.HSLToColor(hsl)
        }

    val cream: Int get() = ColorUtils.blendARGB(SLEEVE, tint, 0.10f)
}

/**
 * Draws a share card off screen, on a plain [Bitmap]. It only ever sees the card's text and the
 * cover's pixels: nothing that names or reaches the server.
 */
class CardRenderer(context: Context) {
    private val display: Typeface = font(context, R.font.fraunces_semibold, Typeface.SERIF)
    private val medium: Typeface = font(context, R.font.dmsans_medium, Typeface.SANS_SERIF)
    private val regular: Typeface = font(context, R.font.dmsans_regular, Typeface.SANS_SERIF)
    private val signatureText = "Sillon"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val measure = TextMeasure { text, font, size -> textPaint(font, size).measureText(text) }

    fun layout(format: CardFormat, template: CardTemplate, text: CardText): CardLayout =
        CardLayoutEngine.layout(format, template, text, measure)

    /** Renders at [scale] (1 for the shared file, less for the on-screen preview). */
    fun render(layout: CardLayout, cover: Bitmap?, palette: CardPalette, scale: Float = 1f): Bitmap {
        val bitmap = createBitmap(
            (layout.width * scale).toInt().coerceAtLeast(1),
            (layout.height * scale).toInt().coerceAtLeast(1),
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val light = layout.template == CardTemplate.POLAROID
        background(canvas, layout, palette)
        when (layout.template) {
            CardTemplate.VINYL -> {
                layout.disc?.let { record(canvas, it, labelRatio = 0.34f, palette) }
                shadow(canvas, layout.cover, radius = 16f)
                coverSquare(canvas, cover, layout.cover, radius = 16f, palette)
            }
            CardTemplate.LABEL -> {
                layout.disc?.let { record(canvas, it, labelRatio = null, palette) }
                coverRound(canvas, cover, layout.cover, palette)
            }
            CardTemplate.POLAROID -> {
                layout.frame?.let { frame ->
                    shadow(canvas, frame, radius = 6f)
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = CardPalette.PRINT
                    canvas.drawRoundRect(frame.rect(), 6f, 6f, paint)
                }
                coverSquare(canvas, cover, layout.cover, radius = 2f, palette)
            }
        }
        val ink = if (light) CardPalette.VINYL else CardPalette.SLEEVE
        val accent = if (light) CardPalette.LABEL_ON_CREAM else CardPalette.LABEL
        layout.texts.forEach { text(canvas, it, ink, accent) }
        layout.stars?.let { stars(canvas, it, layout, accent, ink) }
        signature(canvas, layout.signature, ink, light)
        return bitmap
    }

    private fun background(canvas: Canvas, layout: CardLayout, palette: CardPalette) {
        val w = layout.width.toFloat()
        val h = layout.height.toFloat()
        paint.reset()
        if (layout.template == CardTemplate.POLAROID) {
            paint.color = palette.cream
            canvas.drawRect(0f, 0f, w, h, paint)
            return
        }
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(ColorUtils.blendARGB(CardPalette.VINYL, palette.deep, 0.85f), CardPalette.VINYL, CardPalette.VINYL),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun record(canvas: Canvas, box: Box, labelRatio: Float?, palette: CardPalette) {
        val cx = box.left + box.width / 2f
        val cy = box.top + box.height / 2f
        val r = box.width / 2f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = 0xFF0C0A08.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        // Grooves, then a soft sheen across them.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, r / 260f)
        paint.color = ColorUtils.setAlphaComponent(CardPalette.GROOVE, 0xB0)
        var groove = r * 0.97f
        val inner = r * (labelRatio ?: LABEL_RING) + r * 0.04f
        while (groove > inner) {
            canvas.drawCircle(cx, cy, groove, paint)
            groove -= r / 34f
        }
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.45f, r * 1.1f,
            intArrayOf(ColorUtils.setAlphaComponent(palette.tint, 0x40), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        if (labelRatio != null) {
            paint.color = CardPalette.LABEL
            canvas.drawCircle(cx, cy, r * labelRatio, paint)
            paint.color = 0xFF0C0A08.toInt()
            canvas.drawCircle(cx, cy, r * 0.035f, paint)
        }
    }

    private fun shadow(canvas: Canvas, box: Box, radius: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.TRANSPARENT
        paint.setShadowLayer(box.width * 0.035f, 0f, box.width * 0.015f, 0x66000000)
        paint.color = 0x01000000
        canvas.drawRoundRect(box.rect(), radius, radius, paint)
        paint.clearShadowLayer()
    }

    private fun coverSquare(canvas: Canvas, cover: Bitmap?, box: Box, radius: Float, palette: CardPalette) {
        val path = Path().apply { addRoundRect(box.rect(), radius, radius, Path.Direction.CW) }
        canvas.withClip(path) { coverFill(this, cover, box, palette) }
    }

    private fun coverRound(canvas: Canvas, cover: Bitmap?, box: Box, palette: CardPalette) {
        val cx = box.left + box.width / 2f
        val cy = box.top + box.height / 2f
        val r = box.width / 2f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = CardPalette.LABEL
        canvas.drawCircle(cx, cy, r / LABEL_COVER_IN_RING, paint)
        canvas.withClip(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }) { coverFill(this, cover, box, palette) }
        paint.reset()
        paint.isAntiAlias = true
        paint.color = CardPalette.LABEL
        canvas.drawCircle(cx, cy, r * 0.06f, paint)
        paint.color = 0xFF0C0A08.toInt()
        canvas.drawCircle(cx, cy, r * 0.03f, paint)
    }

    private fun coverFill(canvas: Canvas, cover: Bitmap?, box: Box, palette: CardPalette) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        if (cover == null) {
            // No cover (none on the server, or not cached offline): the spiral on a groove-coloured sleeve.
            paint.color = ColorUtils.blendARGB(CardPalette.GROOVE, palette.tint, 0.2f)
            canvas.drawRect(box.rect(), paint)
            val inset = box.width * 0.2f
            mark(canvas, Box(box.left + inset, box.top + inset, box.width - 2 * inset, box.height - 2 * inset), ColorUtils.setAlphaComponent(CardPalette.SLEEVE, 0x70), CardPalette.LABEL)
            return
        }
        // Centre-crop the cover into the box.
        val scale = max(box.width / cover.width, box.height / cover.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                box.left + (box.width - cover.width * scale) / 2f,
                box.top + (box.height - cover.height * scale) / 2f,
            )
        }
        paint.shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        canvas.drawRect(box.rect(), paint)
        paint.shader = null
    }

    private fun text(canvas: Canvas, block: TextBlock, ink: Int, accent: Int) {
        val p = textPaint(block.font, block.size)
        p.color = when (block.role) {
            TextRole.TITLE, TextRole.NOTE -> ink
            TextRole.ARTIST -> ColorUtils.setAlphaComponent(ink, 0xE6)
            TextRole.DETAIL, TextRole.LYRICS -> ColorUtils.setAlphaComponent(ink, 0xB3)
        }
        val metrics = p.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        if (block.role == TextRole.LYRICS && block.align == Align.START) {
            paint.reset()
            paint.color = accent
            val bar = block.size * 0.12f
            canvas.drawRoundRect(
                RectF(block.box.left, block.box.top + block.lineHeight * 0.15f, block.box.left + bar, block.box.bottom - block.lineHeight * 0.15f),
                bar / 2, bar / 2, paint,
            )
        }
        block.lines.forEachIndexed { i, line ->
            val lineTop = block.box.top + i * block.lineHeight
            val baseline = lineTop + (block.lineHeight - textHeight) / 2f - metrics.ascent
            val x = when (block.align) {
                Align.START -> block.box.left + block.indent
                Align.CENTER -> block.box.left + (block.box.width - p.measureText(line)) / 2f
            }
            canvas.drawText(line, x, baseline, p)
        }
    }

    private fun stars(canvas: Canvas, box: Box, layout: CardLayout, accent: Int, ink: Int) {
        val value = layout.rating ?: return
        val size = box.height
        val step = size * (CardLayoutEngine.STAR_ROW / 5f)
        paint.reset()
        paint.isAntiAlias = true
        repeat(5) { i ->
            val path = starPath(box.left + i * step + size / 2f, box.top + size / 2f, size / 2f)
            paint.color = ColorUtils.setAlphaComponent(ink, 0x38)
            canvas.drawPath(path, paint)
            val fill = (value - i).coerceIn(0f, 1f)
            if (fill > 0f) {
                canvas.withClip(box.left + i * step, box.top, box.left + i * step + size * fill, box.bottom) {
                    paint.color = accent
                    drawPath(path, paint)
                }
            }
        }
    }

    private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
        for (k in 0 until 10) {
            val radius = if (k % 2 == 0) r else r * 0.45f
            val a = -PI / 2 + k * PI / 5
            val x = cx + (radius * cos(a)).toFloat()
            val y = cy + (radius * sin(a)).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun signature(canvas: Canvas, box: Box, ink: Int, light: Boolean) {
        val color = ColorUtils.setAlphaComponent(ink, if (light) 0x8C else 0x99)
        val p = textPaint(CardFont.DISPLAY, box.height * 0.62f)
        p.color = color
        val gap = box.height * 0.28f
        val textWidth = p.measureText(signatureText)
        val total = box.height + gap + textWidth
        val left = box.left + (box.width - total) / 2f
        mark(canvas, Box(left, box.top, box.height, box.height), color, CardPalette.LABEL)
        val metrics = p.fontMetrics
        val baseline = box.top + (box.height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        canvas.drawText(signatureText, left + box.height + gap, baseline, p)
    }

    /** The logo's groove, the same spiral as SillonMark, at any size. */
    private fun mark(canvas: Canvas, box: Box, groove: Int, label: Int) {
        val scale = box.width / MARK_VIEWPORT
        // The spiral spans 31/54 of the viewport; enlarge it to fill the box.
        val zoom = 54f / 33f
        val cx = box.left + box.width / 2f
        val cy = box.top + box.height / 2f
        fun x(v: Float) = cx + (v - MARK_CENTER) * scale * zoom
        fun y(v: Float) = cy + (v - MARK_CENTER) * scale * zoom
        val path = Path()
        spiralPoints().forEachIndexed { i, (px, py) -> if (i == 0) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py)) }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 2.6f * scale * zoom
        paint.color = groove
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = label
        canvas.drawCircle(cx, cy, 11f * scale * zoom, paint)
    }

    private fun textPaint(font: CardFont, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = when (font) {
            CardFont.DISPLAY -> display
            CardFont.MEDIUM -> medium
            CardFont.REGULAR -> regular
        }
        textSize = size
    }

    private fun Box.rect() = RectF(left, top, right, bottom)

    private companion object {
        const val MARK_VIEWPORT = 108f
        const val MARK_CENTER = 54f
        const val LABEL_RING = 0.80f
        val LABEL_COVER_IN_RING = CardLayoutEngine.LABEL_COVER / LABEL_RING

        fun font(context: Context, id: Int, fallback: Typeface): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
    }
}

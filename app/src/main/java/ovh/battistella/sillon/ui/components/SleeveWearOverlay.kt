package ovh.battistella.sillon.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.wear.SleeveWear
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Immutable
data class Wear(val level: Int, val seed: Int, val intensity: Float = 1f) {
    companion object {
        fun of(levels: Map<String, Int>, albumKey: String?, intensity: Float = 1f): Wear? {
            val level = albumKey?.let { levels[it] } ?: return null
            return Wear(level.coerceIn(SleeveWear.SEALED, SleeveWear.MAX), albumKey.hashCode(), intensity)
        }
    }
}

@StringRes
fun wearDescription(level: Int): Int = when (level) {
    SleeveWear.SEALED -> R.string.cover_wear_0
    1 -> R.string.cover_wear_1
    2 -> R.string.cover_wear_2
    3 -> R.string.cover_wear_3
    else -> R.string.cover_wear_4
}

private val Paper = Color(0xFFF6EFE4)
private val Aged = Color(0xFFB8893F)
private val Vinyl = Color(0xFF15120F)

fun Modifier.sleeveWear(wear: Wear): Modifier = drawWithCache {
    val s = min(size.width, size.height)
    val a = wear.intensity
    val random = Random(wear.seed)
    val hairline = 1.dp.toPx()
    val ops = mutableListOf<DrawScope.() -> Unit>()

    if (wear.level == SleeveWear.SEALED) {
        val sheen = Brush.linearGradient(
            0.30f to Color.Transparent,
            0.38f to Color.White.copy(alpha = 0.09f * a),
            0.46f to Color.Transparent,
            0.54f to Color.Transparent,
            0.58f to Color.White.copy(alpha = 0.04f * a),
            0.62f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val edge = Color.White.copy(alpha = 0.10f * a)
        ops += { drawRect(sheen) }
        ops += { drawRect(edge, style = Stroke(hairline)) }
    }

    if (wear.level >= 1) {
        val depth = s * (0.05f + 0.015f * wear.level)
        val tone = Aged.copy(alpha = (0.10f + 0.04f * (wear.level - 1)) * a)
        val w = size.width
        val h = size.height
        val top = Brush.verticalGradient(listOf(tone, Color.Transparent), startY = 0f, endY = depth)
        val bottom = Brush.verticalGradient(listOf(Color.Transparent, tone), startY = h - depth, endY = h)
        val left = Brush.horizontalGradient(listOf(tone, Color.Transparent), startX = 0f, endX = depth)
        val right = Brush.horizontalGradient(listOf(Color.Transparent, tone), startX = w - depth, endX = w)
        ops += { drawRect(top, size = Size(w, depth)) }
        ops += { drawRect(bottom, topLeft = Offset(0f, h - depth), size = Size(w, depth)) }
        ops += { drawRect(left, size = Size(depth, h)) }
        ops += { drawRect(right, topLeft = Offset(w - depth, 0f), size = Size(depth, h)) }
    }

    if (wear.level >= 2) {
        val corners = listOf(
            Offset.Zero,
            Offset(size.width, 0f),
            Offset(size.width, size.height),
            Offset(0f, size.height),
        ).shuffled(random).take(wear.level)
        val radius = s * (0.06f + 0.01f * (wear.level - 2))
        val scuff = Paper.copy(alpha = (0.30f + 0.06f * (wear.level - 2)) * a)
        corners.forEach { corner ->
            val brush = Brush.radialGradient(listOf(scuff, Color.Transparent), center = corner, radius = radius)
            ops += { drawCircle(brush, radius = radius, center = corner) }
        }
    }

    if (wear.level >= 3) {
        // The disc pressing through the sleeve: a soft pale band, a little stronger on one side.
        val center = Offset(
            size.width / 2f + s * (random.nextFloat() - 0.5f) * 0.03f,
            size.height / 2f + s * (random.nextFloat() - 0.5f) * 0.03f,
        )
        val ringRadius = s * 0.44f
        val band = s * 0.045f
        val strength = if (wear.level >= 4) 0.16f else 0.11f
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val lean = Offset(cos(angle), sin(angle)) * (s * 0.012f)
        listOf(center to strength, center + lean to strength * 0.6f).forEach { (c, alpha) ->
            val outer = ringRadius + band
            val brush = Brush.radialGradient(
                0f to Color.Transparent,
                (ringRadius - band) / outer to Color.Transparent,
                ringRadius / outer to Paper.copy(alpha = alpha * a),
                1f to Color.Transparent,
                center = c,
                radius = outer,
            )
            ops += { drawCircle(brush, radius = outer, center = c) }
        }
    }

    if (wear.level >= 4) {
        val x = size.width * (0.62f + random.nextFloat() * 0.12f)
        val start = Offset(x, 0f)
        val end = Offset(x + size.width * (random.nextFloat() - 0.5f) * 0.1f, size.height * 0.62f)
        val light = Brush.linearGradient(
            listOf(Paper.copy(alpha = 0.24f * a), Color.Transparent),
            start = start,
            end = end,
        )
        val dark = Brush.linearGradient(
            listOf(Vinyl.copy(alpha = 0.16f * a), Color.Transparent),
            start = start,
            end = end,
        )
        val shift = Offset(hairline, 0f)
        ops += { drawLine(light, start, end, strokeWidth = hairline) }
        ops += { drawLine(dark, start + shift, end + shift, strokeWidth = hairline) }
    }

    onDrawBehind { ops.forEach { it() } }
}

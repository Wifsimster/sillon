package ovh.battistella.sillon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import ovh.battistella.sillon.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SillonMark(
    modifier: Modifier = Modifier,
    groove: Color,
    label: Color = Brand.Label,
    hole: Color = Color.Transparent,
) {
    val spiral = remember { spiralPoints() }
    Canvas(modifier) {
        val scale = size.minDimension / VIEWPORT
        val origin = Offset((size.width - VIEWPORT * scale) / 2, (size.height - VIEWPORT * scale) / 2)
        fun at(x: Float, y: Float) = Offset(origin.x + x * scale, origin.y + y * scale)
        val path = Path().apply {
            spiral.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(at(x, y).x, at(x, y).y) else lineTo(at(x, y).x, at(x, y).y) }
        }
        drawPath(path, groove, style = Stroke(width = 2.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(label, radius = 11f * scale, center = at(CENTER, CENTER))
        if (hole != Color.Transparent) drawCircle(hole, radius = 2.4f * scale, center = at(CENTER, CENTER))
    }
}

private const val VIEWPORT = 108f
private const val CENTER = 54f

private fun spiralPoints(): List<Pair<Float, Float>> {
    val outer = 31.0
    val inner = 15.5
    val turns = 2.75
    val start = -PI / 3
    val steps = 220
    return (0..steps).map { i ->
        val t = i.toDouble() / steps
        val a = start + t * turns * 2 * PI
        val r = outer - (outer - inner) * t
        (CENTER + r * cos(a)).toFloat() to (CENTER + r * sin(a)).toFloat()
    }
}

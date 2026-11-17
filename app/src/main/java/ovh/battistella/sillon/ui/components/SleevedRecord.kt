package ovh.battistella.sillon.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.ui.theme.Brand

@Composable
fun SleevedRecord(
    artworkUrl: String?,
    playing: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    wear: Wear? = null,
    maxSleeve: Dp = 240.dp,
) {
    val out by animateFloatAsState(
        targetValue = if (playing) PEEK_PLAYING else PEEK_RESTING,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "record-out",
    )
    val turn = if (playing && !reducedMotion) rememberTurn() else remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sleeve = minOf(maxSleeve, maxWidth / (1f + PEEK_PLAYING))
        val disc = sleeve * DISC_RATIO
        val peek = sleeve * out
        val left = (maxWidth - sleeve - peek) / 2
        Box(Modifier.fillMaxWidth().height(sleeve)) {
            Record(
                Modifier
                    .offset(x = left + sleeve + peek - disc, y = (sleeve - disc) / 2)
                    .size(disc)
                    .graphicsLayer { rotationZ = turn.value },
            )
            Artwork(
                artworkUrl,
                Modifier
                    .offset(x = left)
                    .shadow(8.dp, RoundedCornerShape(SLEEVE_CORNER))
                    .size(sleeve),
                cornerRadius = SLEEVE_CORNER,
                wear = wear,
            )
        }
    }
}

@Composable
private fun rememberTurn(): State<Float> =
    rememberInfiniteTransition(label = "record-turn").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(TURN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "record-turn",
    )

@Composable
private fun Record(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val rim = radius * 0.012f
        drawCircle(Brand.Vinyl, radius = radius)
        val groove = Brand.Sleeve.copy(alpha = 0.12f)
        var r = radius * 0.94f
        while (r > radius * 0.42f) {
            drawCircle(groove, radius = r, style = Stroke(width = rim * 0.6f))
            r -= radius * 0.035f
        }
        val sheen = Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.10f to Brand.Sleeve.copy(alpha = 0.16f),
            0.20f to Color.Transparent,
            0.50f to Color.Transparent,
            0.60f to Brand.Sleeve.copy(alpha = 0.16f),
            0.70f to Color.Transparent,
            1.00f to Color.Transparent,
            center = center,
        )
        drawCircle(sheen, radius = radius * 0.95f)
        drawCircle(Brand.Sleeve.copy(alpha = 0.22f), radius = radius - rim, style = Stroke(width = rim * 2))
        drawCircle(Brand.Label, radius = radius * 0.36f)
        val arc = radius * 0.25f
        rotate(-40f) {
            drawArc(
                Brand.Vinyl.copy(alpha = 0.55f),
                startAngle = 0f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - arc, center.y - arc),
                size = Size(arc * 2, arc * 2),
                style = Stroke(width = radius * 0.03f, cap = StrokeCap.Round),
            )
        }
        drawCircle(Brand.Vinyl, radius = radius * 0.035f)
    }
}

private const val PEEK_RESTING = 0.14f
private const val PEEK_PLAYING = 0.44f
private const val DISC_RATIO = 0.94f
private const val TURN_MS = 1_800
private val SLEEVE_CORNER = 12.dp

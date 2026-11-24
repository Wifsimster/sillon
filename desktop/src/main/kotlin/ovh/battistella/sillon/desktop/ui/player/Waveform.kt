package ovh.battistella.sillon.desktop.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.player.EngineState
import ovh.battistella.sillon.desktop.player.Playback
import ovh.battistella.sillon.desktop.ui.components.formatDuration
import kotlin.math.pow

object Waveform {
    const val SAMPLES = 240
    private const val WIDEST_RANGE_DB = 30f
    private const val NARROWEST_RANGE_DB = 6f
    private const val MIN_HEIGHT = 0.08f

    fun heights(levels: List<Float>, count: Int = SAMPLES): List<Float> {
        if (levels.size < 2 || count < 1) return emptyList()
        val finite = levels.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return emptyList()
        val loudest = finite.last()
        val quiet = finite[(finite.size * 0.1f).toInt().coerceAtMost(finite.lastIndex)]
        val range = (loudest - quiet).coerceIn(NARROWEST_RANGE_DB, WIDEST_RANGE_DB)
        val scaled = levels.map { level ->
            val above = ((if (level.isFinite()) level else loudest - range) - (loudest - range)).coerceIn(0f, range)
            (above / range).pow(1.4f).coerceIn(MIN_HEIGHT, 1f)
        }
        return List(count) { i ->
            val from = i * scaled.size / count
            val to = ((i + 1) * scaled.size / count).coerceAtLeast(from + 1).coerceAtMost(scaled.size)
            scaled.subList(from.coerceAtMost(scaled.size - 1), to).average().toFloat()
        }
    }
}

@Composable
fun WaveformSeekBar(playback: Playback, state: EngineState, heights: List<Float>, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatDuration((fraction * duration).toLong()),
            Modifier.width(48.dp).padding(end = 8.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WaveformShape(
            heights,
            fraction,
            Modifier.weight(1f).height(48.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset -> playback.seekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                }
                .pointerInput(duration) {
                    detectDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { playback.seekTo((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
        )
        Text(
            if (state.durationMs > 0) formatDuration(state.durationMs) else "",
            Modifier.width(48.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WaveformShape(heights: List<Float>, fraction: Float, modifier: Modifier = Modifier) {
    val played = MaterialTheme.colorScheme.onSurface
    val ahead = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Canvas(modifier) {
        val middle = size.height / 2
        val step = size.width / (heights.size - 1).coerceAtLeast(1)
        val shape = Path().apply {
            moveTo(0f, middle)
            heights.forEachIndexed { i, h -> lineTo(i * step, middle - h * middle) }
            lineTo(size.width, middle)
            for (i in heights.indices.reversed()) lineTo(i * step, middle + heights[i] * middle)
            close()
        }
        val split = size.width * fraction.coerceIn(0f, 1f)
        clipRectTo(0f, split) { drawPath(shape, played) }
        clipRectTo(split, size.width) { drawPath(shape, ahead) }
        drawLine(played, Offset(0f, middle), Offset(split, middle), strokeWidth = 1.dp.toPx())
        drawLine(ahead, Offset(split, middle), Offset(size.width, middle), strokeWidth = 1.dp.toPx())
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectTo(
    left: Float,
    right: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    if (right <= left) return
    drawContext.canvas.save()
    drawContext.canvas.clipRect(left, 0f, right, size.height)
    block()
    drawContext.canvas.restore()
}

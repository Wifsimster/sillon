package ovh.battistella.sillon.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.theme.Brand
import java.io.File
import java.net.URI
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

fun Throwable.toFailed(): Load.Failed = Load.Failed(message ?: javaClass.simpleName)

@Composable
fun <T> LoadContent(load: Load<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val strings = LocalStrings.current
    when (load) {
        Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> Message(strings.errorGeneric(load.message), modifier, action = strings.retry to onRetry)
        is Load.Ready -> content(load.value)
    }
}

@Composable
fun Message(
    text: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, Modifier.widthIn(max = 520.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = action.second) { Text(action.first) }
        if (secondary != null) OutlinedButton(onClick = secondary.second) { Text(secondary.first) }
    }
}

@Composable
fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    description: String? = null,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (!loaded) SillonMark(Modifier.fillMaxSize().padding(6.dp), groove = MaterialTheme.colorScheme.outlineVariant)
        if (url != null) {
            AsyncImage(
                model = remember(url) { localFile(url) ?: url },
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
            )
        }
    }
}

@Composable
fun SillonMark(modifier: Modifier = Modifier, groove: Color, label: Color = Brand.Label, hole: Color = Color.Transparent) {
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

private fun localFile(url: String): File? =
    if (url.startsWith("file:")) runCatching { File(URI(url)) }.getOrNull() else null

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatSize(bytes: Long): String = when {
    bytes >= GIGA -> "%.1f GB".format(bytes / GIGA.toDouble())
    bytes >= MEGA -> "%.0f MB".format(bytes / MEGA.toDouble())
    else -> "%.0f kB".format(bytes / 1024.0)
}

private const val MEGA = 1024L * 1024
private const val GIGA = MEGA * 1024

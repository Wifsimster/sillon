package ovh.battistella.sillon.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.i18n.LocalStrings

class WindowFrame(
    val custom: Boolean,
    val isMaximized: () -> Boolean,
    val minimize: () -> Unit,
    val toggleMaximize: () -> Unit,
    val close: () -> Unit,
    val draggable: @Composable (Modifier, @Composable () -> Unit) -> Unit,
)

val LocalWindowFrame = staticCompositionLocalOf<WindowFrame?> { null }

val TITLE_STRIP_HEIGHT = 36.dp

@Composable
fun DragArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val frame = LocalWindowFrame.current
    if (frame == null || !frame.custom) {
        Box(modifier) { content() }
        return
    }
    val maximizeOnDoubleClick = Modifier.pointerInput(frame) {
        awaitPointerEventScope {
            var lastPress = 0L
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) {
                    val now = System.currentTimeMillis()
                    if (now - lastPress < DOUBLE_CLICK_MS) {
                        lastPress = 0
                        frame.toggleMaximize()
                    } else {
                        lastPress = now
                    }
                }
            }
        }
    }
    frame.draggable(modifier.then(maximizeOnDoubleClick), content)
}

@Composable
fun TitleStrip(modifier: Modifier = Modifier) {
    val frame = LocalWindowFrame.current
    if (frame == null || !frame.custom) return
    val strings = LocalStrings.current
    Row(modifier.fillMaxWidth().height(TITLE_STRIP_HEIGHT).background(MaterialTheme.colorScheme.background)) {
        DragArea(Modifier.weight(1f).fillMaxHeight()) { Spacer(Modifier.fillMaxWidth()) }
        WindowButton(strings.windowMinimize, onClick = frame.minimize) { color ->
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        }
        WindowButton(if (frame.isMaximized()) strings.windowRestore else strings.windowMaximize, onClick = frame.toggleMaximize) { color ->
            val stroke = Stroke(1.dp.toPx())
            if (frame.isMaximized()) {
                val inset = size.width * 0.22f
                drawRect(color, topLeft = Offset(0f, inset), size = Size(size.width - inset, size.height - inset), style = stroke)
                drawLine(color, Offset(inset, inset), Offset(inset, 0f), stroke.width)
                drawLine(color, Offset(inset, 0f), Offset(size.width, 0f), stroke.width)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height - inset), stroke.width)
                drawLine(color, Offset(size.width, size.height - inset), Offset(size.width - inset, size.height - inset), stroke.width)
            } else {
                drawRect(color, style = stroke)
            }
        }
        WindowButton(strings.close, danger = true, onClick = frame.close) { color ->
            drawLine(color, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun WindowButton(label: String, danger: Boolean = false, onClick: () -> Unit, glyph: DrawScope.(Color) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        hovered && danger -> CLOSE_HOVER
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val colour = if (hovered && danger) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.width(46.dp).fillMaxHeight().background(background).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) { glyph(colour) }
    }
}

private val CLOSE_HOVER = Color(0xFFC42B1C)
private const val DOUBLE_CLICK_MS = 400L

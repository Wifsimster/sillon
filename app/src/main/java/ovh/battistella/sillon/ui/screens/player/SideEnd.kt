package ovh.battistella.sillon.ui.screens.player

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R
import ovh.battistella.sillon.playback.SideEnd
import ovh.battistella.sillon.ui.components.SillonMark
import ovh.battistella.sillon.ui.theme.Brand

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun SideEndRecord(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val lift = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) lift.animateTo(1f, tween(ARM_LIFT_MS, easing = FastOutSlowInEasing))
    }
    val description = stringResource(R.string.side_end_animation)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brand.Vinyl, radius = size.minDimension * 0.46f)
        }
        SillonMark(Modifier.fillMaxSize(), groove = Brand.Sleeve.copy(alpha = 0.85f), hole = Brand.Vinyl)
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val pivot = Offset(size.width / 2 + s * 0.44f, size.height / 2 - s * 0.42f)
            val progress = lift.value
            rotate(degrees = -ARM_SWING_DEGREES * progress, pivot = pivot) {
                val needle = Offset(size.width / 2 + s * 0.20f, size.height / 2 + s * 0.12f)
                val width = s * (0.022f + 0.008f * progress)
                drawLine(Brand.Sleeve, pivot, needle, strokeWidth = width, cap = StrokeCap.Round)
                drawCircle(Brand.Label, radius = s * (0.035f + 0.006f * progress), center = needle)
            }
            drawCircle(Brand.Groove, radius = s * 0.055f, center = pivot)
            drawCircle(Brand.Label, radius = s * 0.025f, center = pivot)
        }
    }
}

@Composable
fun SideEndPanel(end: SideEnd, onFlip: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.side_end_title, end.ended),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.side_flip_hint, end.next),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onFlip, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Rounded.Autorenew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.side_flip))
        }
    }
}

private const val ARM_LIFT_MS = 900
private const val ARM_SWING_DEGREES = 28f

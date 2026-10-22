package ovh.battistella.sillon.ui.screens.mystery

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import ovh.battistella.sillon.R
import ovh.battistella.sillon.playback.mystery.KeepResult
import ovh.battistella.sillon.playback.mystery.MysteryLauncher
import ovh.battistella.sillon.playback.mystery.MysteryProblem
import ovh.battistella.sillon.playback.mystery.SleeveColors
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.SillonMark
import ovh.battistella.sillon.ui.theme.Brand
import ovh.battistella.sillon.ui.theme.Fraunces
import javax.inject.Inject

@HiltViewModel
class MysteryViewModel @Inject constructor(
    private val launcher: MysteryLauncher,
) : ViewModel() {
    val starting = launcher.starting
    val problem = launcher.problem
    val anyAlbum = launcher.anyAlbum

    fun start() = launcher.start()
    fun setAnyAlbum(value: Boolean) = launcher.setAnyAlbum(value)
}

@StringRes
fun MysteryProblem.message(): Int = when (this) {
    MysteryProblem.NO_MUSIC -> R.string.library_no_music
    MysteryProblem.NOTHING_TO_DRAW -> R.string.mystery_none
    MysteryProblem.NOTHING_DOWNLOADED -> R.string.mystery_none_offline
    MysteryProblem.LISTENING_TOGETHER -> R.string.mystery_together
    MysteryProblem.FAILED -> R.string.mystery_failed
}

@StringRes
fun KeepResult.message(): Int = when (this) {
    KeepResult.KEPT -> R.string.mystery_kept
    KeepResult.ALREADY_KEPT -> R.string.mystery_already_kept
    KeepResult.OFFLINE -> R.string.mystery_keep_offline
    KeepResult.DENIED -> R.string.mystery_keep_denied
    KeepResult.FAILED -> R.string.mystery_keep_failed
}

@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun KraftSleeve(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val side = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        drawRect(Color(SleeveColors.KRAFT))
        drawCircle(Color(SleeveColors.KRAFT_SHADE), radius = side * 0.44f, center = center, style = Stroke(width = side * 0.012f))
        drawCircle(Color(SleeveColors.LABEL), radius = side * 0.19f, center = center)
        val mark = measurer.measure(
            "?",
            TextStyle(color = Color(SleeveColors.VINYL), fontFamily = Fraunces, fontSize = (side * 0.24f).toSp()),
        )
        drawText(mark, topLeft = Offset(center.x - mark.size.width / 2f, center.y - mark.size.height / 2f))
    }
}

@Composable
private fun Disc(modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(Brand.Vinyl) }
        SillonMark(Modifier.fillMaxSize(0.92f), groove = Brand.Sleeve.copy(alpha = 0.35f), hole = Brand.Vinyl)
    }
}

@Composable
fun MysteryCover(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.mystery_album)
    KraftSleeve(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = description },
    )
}

@Composable
fun MysteryReveal(coverUri: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(if (reduced) FADE_MS else REVEAL_MS, easing = FastOutSlowInEasing))
        onDone()
    }
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val p = progress.value
        if (reduced) {
            Artwork(coverUri, Modifier.fillMaxSize(), cornerRadius = 16.dp)
            KraftSleeve(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - p }.clip(RoundedCornerShape(16.dp)))
        } else {
            val slide = (p / SLIDE_END).coerceIn(0f, 1f)
            val swap = ((p - SLIDE_END) / (1f - SLIDE_END)).coerceIn(0f, 1f)
            Disc(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = slide * width * 0.42f
                    rotationZ = slide * 140f
                    scaleX = 0.94f
                    scaleY = 0.94f
                    alpha = 1f - swap
                },
            )
            KraftSleeve(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = -slide * width * 0.16f
                    alpha = 1f - swap
                }.clip(RoundedCornerShape(16.dp)),
            )
            Artwork(
                coverUri,
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = swap
                    scaleX = 0.9f + 0.1f * swap
                    scaleY = 0.9f + 0.1f * swap
                },
                cornerRadius = 16.dp,
            )
        }
    }
}

@Composable
fun MysteryHiddenActions(onReveal: () -> Unit, offline: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = onReveal) {
            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.mystery_reveal), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        Text(
            stringResource(if (offline) R.string.mystery_reveal_hint_offline else R.string.mystery_reveal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MysteryRevealedActions(
    keeping: Boolean,
    keep: KeepResult?,
    starting: Boolean,
    onKeep: () -> Unit,
    onAnother: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onKeep, enabled = !keeping && keep != KeepResult.KEPT && keep != KeepResult.ALREADY_KEPT) {
                if (keeping) {
                    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Text(stringResource(R.string.mystery_keep), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
            FilledTonalButton(onClick = onAnother, enabled = !starting) {
                if (starting) {
                    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Text(stringResource(R.string.mystery_another), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
        keep?.let {
            Text(
                stringResource(it.message()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MysteryCard(viewModel: MysteryViewModel = hiltViewModel(), enabled: Boolean = true) {
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val anyAlbum by viewModel.anyAlbum.collectAsStateWithLifecycle()
    Card(
        onClick = viewModel::start,
        enabled = enabled && !starting,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KraftSleeve(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clearAndSetSemantics { })
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.mystery_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                )
                Text(
                    stringResource(if (anyAlbum) R.string.mystery_hint_any else R.string.mystery_hint_rare),
                    style = MaterialTheme.typography.bodySmall,
                )
                FilterChip(
                    selected = anyAlbum,
                    onClick = { viewModel.setAnyAlbum(!anyAlbum) },
                    label = { Text(stringResource(R.string.mystery_any_album)) },
                )
                problem?.let {
                    Text(stringResource(it.message()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            FilledIconButton(onClick = viewModel::start, enabled = enabled && !starting, modifier = Modifier.size(48.dp)) {
                if (starting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.mystery_start))
                }
            }
        }
    }
}

@Composable
fun MysteryStationCard(viewModel: MysteryViewModel = hiltViewModel()) {
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    Card(
        onClick = viewModel::start,
        enabled = !starting,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (starting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                KraftSleeve(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clearAndSetSemantics { })
            }
            Text(
                stringResource(R.string.mystery_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun MysteryProblemLine(viewModel: MysteryViewModel = hiltViewModel()) {
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    problem?.let {
        Text(
            stringResource(it.message()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private const val REVEAL_MS = 1_600
private const val FADE_MS = 400
private const val SLIDE_END = 0.6f

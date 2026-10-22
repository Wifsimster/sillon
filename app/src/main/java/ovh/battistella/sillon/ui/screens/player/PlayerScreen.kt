package ovh.battistella.sillon.ui.screens.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import ovh.battistella.sillon.playback.PlayerUiState
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.formatDuration
import ovh.battistella.sillon.ui.share.ShareCardSheet
import ovh.battistella.sillon.ui.share.ShareSource

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onTogether: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startingRadio by viewModel.startingRadio.collectAsStateWithLifecycle()
    val wear by viewModel.wear.collectAsStateWithLifecycle()
    val sides by viewModel.sides.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotion()
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var shareSource by remember { mutableStateOf<ShareSource?>(null) }

    val tint by animateColorAsState(
        coverColor(state.artworkUri) ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(600),
        label = "cover tint",
    )
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to tint.copy(alpha = 0.55f), 0.75f to background))
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlayerHeader(
            onClose = onClose,
            onTogether = onTogether,
            startingRadio = startingRadio,
            radioEnabled = state.hasMedia,
            onRadio = viewModel::radioFromCurrent,
            onShareCard = if (state.hasMedia) {
                {
                    shareSource = ShareSource(
                        kind = ShareSource.Kind.TRACK,
                        key = state.currentKey,
                        title = state.title,
                        artist = state.artist.ifBlank { null },
                        album = state.album.ifBlank { null },
                        artworkUri = state.artworkUri,
                    )
                }
            } else {
                null
            },
        )
        Crossfade(sides.ended, label = "side end") { end ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (end != null) {
                    SideEndRecord(reducedMotion, Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f))
                    SideEndPanel(end, onFlip = viewModel::flipSide)
                } else {
                    Artwork(
                        state.artworkUri,
                        Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f),
                        cornerRadius = 16.dp,
                        wear = wear,
                    )
                    NowPlayingTitle(state, sides.current)
                }
            }
        }

        val duration = state.durationMs.coerceAtLeast(1L).toFloat()
        Column {
            Slider(
                value = scrubbing ?: state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                valueRange = 0f..duration,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { viewModel.seekTo(it.toLong()) }
                    scrubbing = null
                },
                enabled = state.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(scrubbing?.toLong() ?: state.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val active = MaterialTheme.colorScheme.primary
            val inactive = MaterialTheme.colorScheme.onSurfaceVariant
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, stringResource(R.string.album_shuffle), tint = if (state.shuffle) active else inactive)
            }
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(36.dp))
            }
            FilledIconButton(onClick = viewModel::playPause, modifier = Modifier.size(72.dp)) {
                if (state.isPlaying) {
                    Icon(Icons.Rounded.Pause, stringResource(R.string.player_pause), Modifier.size(40.dp))
                } else {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play), Modifier.size(40.dp))
                }
            }
            IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.size(36.dp))
            }
            IconButton(onClick = viewModel::cycleRepeat) {
                val (icon, tint) = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to active
                    Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to active
                    else -> Icons.Rounded.Repeat to inactive
                }
                Icon(icon, contentDescription = null, tint = tint)
            }
        }
    }
    shareSource?.let { ShareCardSheet(it, onDismiss = { shareSource = null }) }
}

@Composable
private fun NowPlayingTitle(state: PlayerUiState, side: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (side != null) {
            Text(
                stringResource(R.string.side_label, side),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            state.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun coverColor(uri: String?): Color? {
    val context = LocalContext.current
    var color by remember(uri) { mutableStateOf<Color?>(null) }
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(uri).size(24).allowHardware(false).build()
        val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        var r = 0L
        var g = 0L
        var b = 0L
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEach { p ->
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size.coerceAtLeast(1)
        color = Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
    return color
}

@Composable
fun PlayerHeader(
    onClose: () -> Unit,
    onTogether: () -> Unit,
    startingRadio: Boolean,
    radioEnabled: Boolean,
    onRadio: () -> Unit,
    onShareCard: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.action_back))
        }
        Spacer(Modifier.weight(1f))
        if (onShareCard != null) {
            IconButton(onClick = onShareCard) {
                Icon(Icons.Rounded.Share, stringResource(R.string.share_card))
            }
        }
        IconButton(onClick = onTogether) {
            Icon(Icons.Rounded.Groups, stringResource(R.string.together_open))
        }
        if (startingRadio) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onRadio, enabled = radioEnabled) {
                Icon(Icons.Rounded.Radio, stringResource(R.string.track_radio))
            }
        }
    }
}

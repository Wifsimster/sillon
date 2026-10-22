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
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.material.icons.rounded.Speaker
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
import androidx.compose.material3.LocalContentColor
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
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.Artwork
import ovh.battistella.sillon.ui.components.formatDuration
import ovh.battistella.sillon.playback.PlayerUiState
import ovh.battistella.sillon.playback.SideState
import ovh.battistella.sillon.playback.mystery.KeepResult
import ovh.battistella.sillon.playback.mystery.Mystery
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.sonos.Output
import ovh.battistella.sillon.sonos.OutputNotice
import ovh.battistella.sillon.ui.components.Wear
import ovh.battistella.sillon.ui.screens.mystery.MysteryCover
import ovh.battistella.sillon.ui.screens.mystery.MysteryHiddenActions
import ovh.battistella.sillon.ui.screens.mystery.MysteryReveal
import ovh.battistella.sillon.ui.screens.mystery.MysteryRevealedActions
import ovh.battistella.sillon.ui.share.ShareCardSheet
import ovh.battistella.sillon.ui.share.ShareSource

interface PlayerControls {
    fun radioFromCurrent()
    fun reveal()
    fun revealSeen()
    fun keepSurprise()
    fun anotherSurprise()
    fun flipSide()
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun rate(stars: Int?) = Unit
    fun retryRating() = Unit
}

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onTogether: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    outputViewModel: OutputViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startingRadio by viewModel.startingRadio.collectAsStateWithLifecycle()
    val mystery by viewModel.mystery.collectAsStateWithLifecycle()
    val mysteryStarting by viewModel.mysteryStarting.collectAsStateWithLifecycle()
    val keeping by viewModel.keeping.collectAsStateWithLifecycle()
    val keep by viewModel.keep.collectAsStateWithLifecycle()
    val wear by viewModel.wear.collectAsStateWithLifecycle()
    val sides by viewModel.sides.collectAsStateWithLifecycle()
    val rating by viewModel.rating.collectAsStateWithLifecycle()
    val together by viewModel.together.collectAsStateWithLifecycle()
    val output by outputViewModel.output.collectAsStateWithLifecycle()
    val notice by outputViewModel.notice.collectAsStateWithLifecycle()
    var choosingOutput by remember { mutableStateOf(false) }
    PlayerContent(
        state = state,
        startingRadio = startingRadio,
        mystery = mystery,
        mysteryStarting = mysteryStarting,
        keeping = keeping,
        keep = keep,
        wear = wear,
        sides = sides,
        rating = rating,
        controls = viewModel,
        onClose = onClose,
        onTogether = onTogether.takeIf { together },
        output = output,
        outputNotice = notice,
        onOutput = { choosingOutput = true },
        onNoticeSeen = outputViewModel::noticeSeen,
    )
    if (choosingOutput) OutputSheet(onDismiss = { choosingOutput = false }, viewModel = outputViewModel)
}

@Composable
fun PlayerContent(
    state: PlayerUiState,
    startingRadio: Boolean,
    mystery: Mystery?,
    mysteryStarting: Boolean,
    keeping: Boolean,
    keep: KeepResult?,
    wear: Wear?,
    controls: PlayerControls,
    onClose: () -> Unit,
    onTogether: (() -> Unit)?,
    sides: SideState = SideState(),
    rating: RatingUi? = null,
    output: Output = Output.Phone,
    outputNotice: OutputNotice? = null,
    onOutput: (() -> Unit)? = null,
    onNoticeSeen: () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val surprise = mystery?.takeIf { state.currentKey == MysteryMask.HIDDEN_KEY || it.contains(state.currentKey.orEmpty()) }
    val hidden = surprise != null && !surprise.revealed
    val revealing = surprise != null && surprise.revealed && !surprise.revealSeen
    val view = LocalView.current
    val revealedAnnouncement = stringResource(R.string.mystery_revealed, state.title, state.artist)
    val announcement by rememberUpdatedState(revealedAnnouncement)
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
            onRadio = controls::radioFromCurrent,
            showRadio = !hidden,
            // A card of a hidden surprise would give the album away.
            onShareCard = if (state.hasMedia && !hidden) {
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
            onOutput = onOutput,
            outputName = (output as? Output.OnSonos)?.group?.name ?: (output as? Output.Connecting)?.group?.name,
        )
        if (onOutput != null) OutputBanner(output, outputNotice, onOpen = onOutput, onNoticeSeen = onNoticeSeen)
        Crossfade(sides.ended, label = "side end") { end ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val coverModifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f)
                if (end != null) {
                    SideEndRecord(reducedMotion, coverModifier)
                    SideEndPanel(end, onFlip = controls::flipSide)
                } else {
                    when {
                        hidden -> MysteryCover(coverModifier)
                        revealing -> MysteryReveal(
                            coverUri = state.artworkUri,
                            onDone = {
                                controls.revealSeen()
                                view.announceForAccessibility(announcement)
                            },
                            modifier = coverModifier,
                        )
                        else -> Artwork(state.artworkUri, coverModifier, cornerRadius = 16.dp, wear = wear)
                    }
                    NowPlayingTitle(state, sides.current)
                    if (rating != null && !hidden) RatingBar(rating, onRate = controls::rate, onRetry = controls::retryRating)
                }
            }
        }
        surprise?.let {
            if (hidden) {
                MysteryHiddenActions(onReveal = controls::reveal, offline = it.offline)
            } else {
                MysteryRevealedActions(
                    keeping = keeping,
                    keep = keep,
                    starting = mysteryStarting,
                    onKeep = controls::keepSurprise,
                    onAnother = controls::anotherSurprise,
                )
            }
        }

        val duration = state.durationMs.coerceAtLeast(1L).toFloat()
        Column {
            Slider(
                value = scrubbing ?: state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                valueRange = 0f..duration,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { controls.seekTo(it.toLong()) }
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
            IconButton(onClick = controls::toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, stringResource(R.string.album_shuffle), tint = if (state.shuffle) active else inactive)
            }
            IconButton(onClick = controls::previous) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(36.dp))
            }
            FilledIconButton(onClick = controls::playPause, modifier = Modifier.size(72.dp)) {
                if (state.isPlaying) {
                    Icon(Icons.Rounded.Pause, stringResource(R.string.player_pause), Modifier.size(40.dp))
                } else {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play), Modifier.size(40.dp))
                }
            }
            IconButton(onClick = controls::next, enabled = state.hasNext) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.size(36.dp))
            }
            IconButton(onClick = controls::cycleRepeat) {
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
    onTogether: (() -> Unit)?,
    startingRadio: Boolean,
    radioEnabled: Boolean,
    onRadio: () -> Unit,
    showRadio: Boolean = true,
    onShareCard: (() -> Unit)? = null,
    onOutput: (() -> Unit)? = null,
    /** The Sonos group the music plays on, or null on the phone. */
    outputName: String? = null,
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
        if (onOutput != null) {
            IconButton(onClick = onOutput) {
                Icon(
                    Icons.Rounded.Speaker,
                    if (outputName != null) stringResource(R.string.output_open_active, outputName) else stringResource(R.string.output_open),
                    tint = if (outputName != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
        // Listen together plays on each phone, not on a speaker.
        if (onTogether != null) {
            IconButton(onClick = onTogether, enabled = outputName == null) {
                Icon(
                    Icons.Rounded.Groups,
                    stringResource(if (outputName == null) R.string.together_open else R.string.together_not_on_sonos),
                )
            }
        }
        when {
            !showRadio -> Unit
            startingRadio -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            else -> IconButton(onClick = onRadio, enabled = radioEnabled) {
                Icon(Icons.Rounded.Radio, stringResource(R.string.track_radio))
            }
        }
    }
}

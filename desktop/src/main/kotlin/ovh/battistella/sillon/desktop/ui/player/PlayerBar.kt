package ovh.battistella.sillon.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.data.rating.JellyfinStars
import ovh.battistella.sillon.data.rating.RatingSend
import ovh.battistella.sillon.data.rating.Stars
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.player.EngineState
import ovh.battistella.sillon.desktop.player.Playback
import ovh.battistella.sillon.desktop.player.RepeatMode
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.components.Cover
import ovh.battistella.sillon.desktop.ui.components.formatDuration
import ovh.battistella.sillon.playback.PlayableTrack

@Composable
fun PlayerBar(app: AppState) {
    val strings = LocalStrings.current
    val playback = app.graph.playback
    val state by playback.state.collectAsState()
    val track = state.current
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(enabled = track != null) { app.playerOpen = !app.playerOpen }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(track?.artworkUrl, Modifier.size(60.dp), description = strings.playerOpen)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(track?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track?.let(::byline).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Transport(playback, state)
                SeekBar(playback, state)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                VolumeAndQueue(app, state)
            }
        }
    }
}

@Composable
fun VolumeAndQueue(app: AppState, state: EngineState) {
    val strings = LocalStrings.current
    Icon(Icons.AutoMirrored.Filled.VolumeUp, strings.volume, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    SlimSlider(value = state.volume, onValueChange = { app.graph.playback.setVolume(it) }, modifier = Modifier.width(120.dp).padding(horizontal = 8.dp))
    IconButton(onClick = { app.queueOpen = !app.queueOpen }) {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic,
            strings.queue,
            tint = if (app.queueOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun byline(track: PlayableTrack): String =
    listOfNotNull(track.artist, track.album?.takeUnless { it.isBlank() || it.equals(track.title, ignoreCase = true) }).joinToString(" · ")

@Composable
fun Transport(playback: Playback, state: EngineState, large: Boolean = false) {
    val strings = LocalStrings.current
    val enabled = state.current != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 4.dp)) {
        IconButton(onClick = { playback.toggleShuffle() }, enabled = enabled) {
            Icon(Icons.Filled.Shuffle, strings.shuffle, tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { playback.previous() }, enabled = enabled) { Icon(Icons.Filled.SkipPrevious, strings.previous) }
        FilledIconButton(onClick = { playback.playPause() }, enabled = enabled, modifier = Modifier.size(if (large) 56.dp else 40.dp)) {
            when {
                state.buffering -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                state.playing -> Icon(Icons.Filled.Pause, strings.pause)
                else -> Icon(Icons.Filled.PlayArrow, strings.play)
            }
        }
        IconButton(onClick = { playback.next() }, enabled = enabled && (state.index < state.queue.lastIndex || state.repeat == RepeatMode.ALL)) {
            Icon(Icons.Filled.SkipNext, strings.next)
        }
        IconButton(onClick = { playback.cycleRepeat() }, enabled = enabled) {
            when (state.repeat) {
                RepeatMode.OFF -> Icon(Icons.Filled.Repeat, strings.repeatOff, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                RepeatMode.ALL -> Icon(Icons.Filled.Repeat, strings.repeatAll, tint = MaterialTheme.colorScheme.primary)
                RepeatMode.ONE -> Icon(Icons.Filled.RepeatOne, strings.repeatOne, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SeekBar(playback: Playback, state: EngineState, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatDuration(((dragging ?: fraction) * duration).toLong()),
            Modifier.width(48.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SlimSlider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { playback.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.current != null && state.durationMs > 0,
            modifier = Modifier.weight(1f).height(24.dp),
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
fun RatingStars(app: AppState, size: Dp) {
    val strings = LocalStrings.current
    val playback = app.graph.playback
    val state by playback.state.collectAsState()
    val ratings by playback.ratings.collectAsState()
    val jellyfin by app.graph.repository.isJellyfin.collectAsState(false)
    val track = state.current ?: return
    val stars = ratings.book.starsOf(track.ratingKey, track.userRating)
    val failed = ratings.book.sendState(track.ratingKey) == RatingSend.FAILED
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..Stars.MAX) {
            val supported = !jellyfin || JellyfinStars.supports(star)
            val filled = stars != null && star <= stars
            Box(
                Modifier.size(size + 8.dp).clip(MaterialTheme.shapes.extraSmall).alpha(if (supported) 1f else 0.3f)
                    .clickable(enabled = supported) { playback.rate(track.ratingKey, Stars.toggle(stars, star)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    strings.rate(star),
                    Modifier.size(size),
                    tint = when {
                        failed -> MaterialTheme.colorScheme.error
                        filled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = { Box(Modifier.size(14.dp).background(color, CircleShape)) },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(4.dp),
                enabled = enabled,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
            )
        },
    )
}

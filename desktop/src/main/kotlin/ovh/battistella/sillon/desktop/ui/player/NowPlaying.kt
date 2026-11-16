package ovh.battistella.sillon.desktop.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.battistella.sillon.data.rating.RatingSend
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.components.Cover
import ovh.battistella.sillon.desktop.ui.components.Wear
import ovh.battistella.sillon.playback.PlayableTrack

@Composable
fun NowPlaying(app: AppState) {
    val strings = LocalStrings.current
    val playback = app.graph.playback
    val repository = app.graph.repository
    val state by playback.state.collectAsState()
    val ratings by playback.ratings.collectAsState()
    val jellyfin by repository.isJellyfin.collectAsState(false)
    val wear by app.graph.sleeveWear.levels.collectAsState(emptyMap())
    val scope = rememberCoroutineScope()
    val track = state.current
    val context = LocalPlatformContext.current
    var tint by remember { mutableStateOf<Color?>(null) }
    var lyrics by remember { mutableStateOf<List<String>?>(null) }
    var startingRadio by remember { mutableStateOf(false) }
    LaunchedEffect(track?.artworkUrl) {
        tint = track?.artworkUrl?.let { url ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val data: Any = if (url.startsWith("file:")) runCatching { java.io.File(java.net.URI(url)) }.getOrDefault(url) else url
                    val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(data).size(48, 48).build())
                    (result as? SuccessResult)?.image?.toBitmap()?.let { bitmap ->
                        var r = 0L
                        var g = 0L
                        var b = 0L
                        var n = 0L
                        for (x in 0 until bitmap.width step 2) {
                            for (y in 0 until bitmap.height step 2) {
                                val c = bitmap.getColor(x, y)
                                r += (c shr 16) and 0xFF
                                g += (c shr 8) and 0xFF
                                b += c and 0xFF
                                n++
                            }
                        }
                        if (n == 0L) null else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
                    }
                }
            }.getOrNull()
        }
    }
    LaunchedEffect(track?.ratingKey) {
        lyrics = null
        val key = track?.ratingKey ?: return@LaunchedEffect
        lyrics = runCatching { repository.lyrics(key) }.getOrDefault(emptyList())
    }
    val surface = MaterialTheme.colorScheme.surface
    val wash by animateColorAsState((tint ?: surface).copy(alpha = 0.55f), tween(600))
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(wash, surface)))) {
            IconButton(onClick = { app.playerOpen = false }, Modifier.padding(12.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, strings.playerClose)
            }
            if (track == null) {
                Text(strings.playerNothing, Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
                return@Box
            }
            Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(
                        track.artworkUrl,
                        Modifier.weight(1f, fill = false).widthIn(max = 440.dp).aspectRatio(1f, matchHeightConstraintsFirst = true),
                        shape = RoundedCornerShape(16.dp),
                        wear = Wear.of(wear, track.albumKey, intensity = 0.6f),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(track.artist, track.album).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    RatingStars(app, 28.dp)
                    val notice = when {
                        ratings.book.sendState(track.ratingKey) == RatingSend.FAILED -> strings.ratingFailed
                        ratings.book.sendState(track.ratingKey) == RatingSend.WAITING_FOR_NETWORK -> strings.ratingWaiting
                        jellyfin -> strings.ratingJellyfinOnly
                        else -> null
                    }
                    if (notice != null) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Transport(playback, state, large = true)
                    SeekBar(playback, state, Modifier.widthIn(max = 520.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            startingRadio = true
                            scope.launch {
                                val draw: suspend () -> List<PlayableTrack> = { repository.playable(repository.trackRadioTracks(track.ratingKey)) }
                                runCatching { draw() }.onSuccess { playback.replaceUpcoming(it, more = draw) }
                                startingRadio = false
                            }
                        },
                        enabled = !startingRadio,
                    ) {
                        if (startingRadio) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Radio, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(strings.trackRadio, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(strings.lyrics, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    val lines = lyrics
                    when {
                        lines == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        lines.isEmpty() -> Text(strings.lyricsNone, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
        }
    }
}

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import ovh.battistella.sillon.data.settings.HiddenArtists
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.DragArea
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
    val lyricsShown by app.graph.prefs.lyricsShown.collectAsState(true)
    val scope = rememberCoroutineScope()
    val track = state.current
    val context = LocalPlatformContext.current
    var tint by remember { mutableStateOf<Color?>(null) }
    var lyrics by remember { mutableStateOf<List<String>?>(null) }
    var startingRadio by remember { mutableStateOf(false) }
    var waveform by remember { mutableStateOf<List<Float>>(emptyList()) }
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
        waveform = emptyList()
        val key = track?.ratingKey ?: return@LaunchedEffect
        waveform = app.graph.waveforms.of(key)
    }
    LaunchedEffect(track?.ratingKey) {
        lyrics = null
        val key = track?.ratingKey ?: return@LaunchedEffect
        lyrics = runCatching { repository.lyrics(key) }.getOrDefault(emptyList())
    }
    val surface = MaterialTheme.colorScheme.surface
    val wash by animateColorAsState((tint ?: surface).copy(alpha = 0.55f), tween(600))
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(wash, surface)))) {
            // One top bar: collapse on the left, volume and queue on the right. It shares the content's
            // horizontal inset, so the lyrics' close button lines up under the queue button.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { app.playerOpen = false }) {
                    Icon(Icons.Filled.KeyboardArrowDown, strings.playerClose)
                }
                DragArea(Modifier.weight(1f).height(48.dp)) { Spacer(Modifier.fillMaxSize()) }
                if (track != null) {
                    if (!lyricsShown && !lyrics.isNullOrEmpty()) {
                        IconButton(onClick = { scope.launch { app.graph.prefs.setLyricsShown(true) } }) {
                            Icon(Icons.Filled.Lyrics, strings.lyricsShow)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    VolumeAndQueue(app, state)
                }
            }
            if (track == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.playerNothing, style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(start = EDGE, end = EDGE, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(64.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(
                        track.artworkUrl,
                        Modifier.weight(1f, fill = false).widthIn(max = 440.dp).aspectRatio(1f, matchHeightConstraintsFirst = true),
                        shape = RoundedCornerShape(16.dp),
                        wear = Wear.of(wear, track.albumKey, intensity = 0.6f),
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        byline(track),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    RatingStars(app, 28.dp)
                    val notice = when {
                        ratings.book.sendState(track.ratingKey) == RatingSend.FAILED -> strings.ratingFailed
                        ratings.book.sendState(track.ratingKey) == RatingSend.WAITING_FOR_NETWORK -> strings.ratingWaiting
                        jellyfin -> strings.ratingJellyfinOnly
                        else -> null
                    }
                    if (notice != null) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Transport(playback, state, large = true)
                    // Room between the buttons and the waveform's peaks.
                    Spacer(Modifier.height(16.dp))
                    if (waveform.isEmpty()) {
                        SeekBar(playback, state, Modifier.widthIn(max = 520.dp))
                    } else {
                        WaveformSeekBar(playback, state, waveform, Modifier.widthIn(max = 520.dp))
                    }
                    Spacer(Modifier.height(24.dp))
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
                    val artist = HiddenArtists.primary(track.artist)
                    if (artist != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    app.graph.settings.setArtistHidden(artist, true)
                                    playback.dropUpcoming { HiddenArtists.credits(it.artist, setOf(artist)) }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.VisibilityOff, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.playerHideArtist(artist), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val lines = lyrics
                if (lyricsShown && !lines.isNullOrEmpty()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.lyrics, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = { scope.launch { app.graph.prefs.setLyricsShown(false) } }) {
                                Icon(Icons.Filled.Close, strings.lyricsHide)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier.weight(1f).fadingEdges().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
        }
    }
}

/** Horizontal inset shared by the top bar and the content, so their edges line up. */
private val EDGE = 32.dp

/** Fades the top and bottom of a scrolling column instead of cutting a line in half. */
private fun Modifier.fadingEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val edge = (24.dp.toPx() / size.height).coerceAtMost(0.5f)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

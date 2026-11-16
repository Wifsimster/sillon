package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.data.repository.LIBRARY_SHUFFLE_STATION
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.Screen
import ovh.battistella.sillon.desktop.ui.StackEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenScreen(app: AppState, entry: StackEntry) {
    val strings = LocalStrings.current
    val model = entry.model("listen") { scope -> ListenModel(app.graph, scope) }
    val online by model.online.collectAsState()
    val jellyfin by model.isJellyfin.collectAsState()
    val stations by model.stations.collectAsState()
    val starting by model.starting.collectAsState()
    val notice by model.notice.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!online) Notice(strings.listenOffline)
        notice?.let {
            Notice(
                when (it) {
                    ListenNotice.SHUFFLE_FROM_DOWNLOADS -> strings.listenFromDownloads
                    ListenNotice.FIVE_STARS_FROM_DOWNLOADS -> strings.listenFiveStarsFromDownloads
                    ListenNotice.NO_FIVE_STARS -> if (jellyfin) strings.listenNoFavourites else strings.listenNoFiveStars
                    ListenNotice.NOTHING -> strings.listenNothing
                    ListenNotice.FAILED -> strings.listenFailed
                },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ListenCard(Icons.Filled.Shuffle, strings.listenShuffle, strings.listenShuffleHint, starting == ListenTile.SHUFFLE.name, starting == null, model::shuffle)
            ListenCard(
                Icons.Filled.Star,
                if (jellyfin) strings.favourites else strings.fiveStars,
                if (jellyfin) strings.favouritesHint else strings.fiveStarsHint,
                starting == ListenTile.FIVE_STARS.name,
                starting == null,
                model::fiveStars,
            ) {
                TextButton(onClick = { app.open(Screen.FiveStars) }) { Text(strings.fiveStarsList) }
            }
            ListenCard(Icons.Filled.NewReleases, strings.neverPlayed, strings.neverPlayedHint, starting == ListenTile.NEVER_PLAYED.name, starting == null && online, model::neverPlayed)
        }
        if (stations.isNotEmpty()) {
            Text(strings.radios, style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stations.forEach { station ->
                    val title = station.title?.takeIf { it.isNotBlank() && station.key != LIBRARY_SHUFFLE_STATION } ?: strings.radioLibrary
                    Card(
                        Modifier.width(260.dp).clip(CardDefaults.shape).clickable(enabled = starting == null && online) { model.station(station) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (starting == station.key) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Radio, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ListenCard(
    icon: ImageVector,
    title: String,
    hint: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(
        Modifier.width(300.dp).height(168.dp).clip(CardDefaults.shape).clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                } else {
                    Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.weight(1f))
            if (extra != null) extra()
        }
    }
}

package ovh.battistella.sillon.ui.screens.listen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.playback.PlaybackConnection
import javax.inject.Inject

/**
 * The server's radios on Listen: Plex's mixes for you and library stations, Jellyfin's library
 * shuffle. They all play from the server, so offline they stay visible but off.
 */
@HiltViewModel
class ListenRadiosViewModel @Inject constructor(
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
    network: NetworkMonitor,
) : ViewModel() {
    val online: StateFlow<Boolean> = network.online.stateIn(viewModelScope, SharingStarted.Eagerly, network.isOnline())

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations = _stations.asStateFlow()

    private val _starting = MutableStateFlow<String?>(null)
    val starting = _starting.asStateFlow()

    private var loading: Job? = null

    init {
        // Loads now, and again when the connection comes back before any radio was found.
        viewModelScope.launch { online.collect { if (it && _stations.value.isEmpty()) load() } }
    }

    /** Asks the server again; a failure keeps the radios already shown. */
    fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            runCatching { repository.stations() }.onSuccess { _stations.value = it }
        }
    }

    fun play(station: Station) {
        if (_starting.value != null || !online.value) return
        _starting.value = station.key
        viewModelScope.launch {
            runCatching { repository.playable(repository.stationTracks(station)) }
                .onSuccess { if (it.isNotEmpty()) playback.play(it) }
            _starting.value = null
        }
    }
}

/** Whether the radios section shows at all: online it waits for a radio, offline it says why they're off. */
internal fun showRadios(stations: List<Station>, online: Boolean): Boolean = stations.isNotEmpty() || !online

/** The radios, as one row of compact cards under the tiles. */
@Composable
internal fun RadiosSection(stations: List<Station>, starting: String?, online: Boolean, onPlay: (Station) -> Unit) {
    Column {
        Text(
            stringResource(R.string.radios),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        if (stations.isNotEmpty()) {
            Box(Modifier.bleedRow()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(stations, key = { it.key }) { station ->
                        RadioCard(
                            title = station.title ?: stringResource(R.string.radio_library),
                            enabled = online && starting == null,
                            loading = starting == station.key,
                            onClick = { onPlay(station) },
                        )
                    }
                }
            }
        }
        if (!online) {
            Text(
                stringResource(R.string.listen_radios_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun RadioCard(title: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val faded = !enabled && !loading
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (faded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(148.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides LocalContentColor.current.copy(alpha = if (faded) 0.6f else 1f),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clickable(
                        enabled = enabled,
                        onClickLabel = stringResource(R.string.listen_radio_play),
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Lets the row scroll from one screen edge to the other, past the page's side margins. */
private fun Modifier.bleedRow(margin: Dp = 16.dp): Modifier = layout { measurable, constraints ->
    val extra = (margin * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

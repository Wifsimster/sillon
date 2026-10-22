package ovh.battistella.sillon.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.NeverPlayedMode
import ovh.battistella.sillon.data.repository.neverPlayedPercent
import ovh.battistella.sillon.data.settings.CachedLibraryPlays
import ovh.battistella.sillon.playback.NeverPlayedRadio
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.RadioStart
import java.text.NumberFormat
import javax.inject.Inject

enum class NeverPlayedNotice { OFFLINE, NO_OFFLINE_HISTORY, EXHAUSTED, FAILED }

@HiltViewModel
class NeverPlayedViewModel @Inject constructor(
    private val radio: NeverPlayedRadio,
    private val playback: PlaybackConnection,
) : ViewModel() {
    private val _plays = MutableStateFlow<CachedLibraryPlays?>(null)
    val plays = _plays.asStateFlow()

    private val _starting = MutableStateFlow<NeverPlayedMode?>(null)
    val starting = _starting.asStateFlow()

    private val _notice = MutableStateFlow<NeverPlayedNotice?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { radio.cachedPlays() }.getOrNull()?.let { _plays.value = it }
            runCatching { radio.refreshPlays() }.getOrNull()?.let { _plays.value = it }
        }
    }

    fun play(mode: NeverPlayedMode) {
        if (_starting.value != null) return
        _starting.value = mode
        _notice.value = null
        viewModelScope.launch {
            _notice.value = runCatching { radio.start(mode) }.fold(
                onSuccess = { start ->
                    when (start) {
                        is RadioStart.Ready -> {
                            playback.play(start.tracks)
                            if (start.offline) NeverPlayedNotice.OFFLINE else null
                        }
                        RadioStart.Exhausted -> NeverPlayedNotice.EXHAUSTED
                        RadioStart.NoOfflineHistory -> NeverPlayedNotice.NO_OFFLINE_HISTORY
                    }
                },
                onFailure = { NeverPlayedNotice.FAILED },
            )
            _starting.value = null
        }
    }
}

@Composable
fun NeverPlayedCard(viewModel: NeverPlayedViewModel = hiltViewModel()) {
    val plays by viewModel.plays.collectAsStateWithLifecycle()
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.never_played), style = MaterialTheme.typography.titleMedium)
                Text(playsText(plays), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = { viewModel.play(NeverPlayedMode.ALBUMS) },
                    enabled = starting == null,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (starting == NeverPlayedMode.ALBUMS) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Album, contentDescription = stringResource(R.string.never_played_albums_play))
                    }
                }
                FilledIconButton(
                    onClick = { viewModel.play(NeverPlayedMode.TRACKS) },
                    enabled = starting == null,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (starting == NeverPlayedMode.TRACKS) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Radio, contentDescription = stringResource(R.string.never_played_play))
                    }
                }
            }
        }
        notice?.let { current ->
            Text(
                stringResource(
                    when (current) {
                        NeverPlayedNotice.OFFLINE -> R.string.never_played_offline
                        NeverPlayedNotice.NO_OFFLINE_HISTORY -> R.string.never_played_no_history
                        NeverPlayedNotice.EXHAUSTED -> R.string.never_played_exhausted
                        NeverPlayedNotice.FAILED -> R.string.never_played_failed
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (current == NeverPlayedNotice.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun playsText(plays: CachedLibraryPlays?): String {
    val percent = plays?.let { neverPlayedPercent(it.neverPlayed, it.total) }
        ?: return stringResource(R.string.never_played_hint)
    val format = NumberFormat.getIntegerInstance()
    return stringResource(R.string.never_played_share, percent) + "\n" +
        stringResource(R.string.never_played_count, format.format(plays.neverPlayed), format.format(plays.total))
}

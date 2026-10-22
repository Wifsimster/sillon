package ovh.battistella.sillon.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.playback.cache.CacheLimit
import ovh.battistella.sillon.playback.cache.ListeningCache
import javax.inject.Inject

data class CacheUsage(val usedBytes: Long, val freeBytes: Long)

@HiltViewModel
class ListeningCacheViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val cache: ListeningCache,
) : ViewModel() {
    val enabled = settings.listeningCache.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val sizeGb = settings.listeningCacheGb.stateIn(viewModelScope, SharingStarted.Eagerly, CacheLimit.DEFAULT_GB)

    /** Re-read every second while the screen is open: the cache grows as music plays. */
    val usage = flow {
        while (true) {
            emit(CacheUsage(cache.usedBytes(), cache.freeBytes()))
            delay(USAGE_REFRESH_MS)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settings.setListeningCache(value) }
    }

    fun setSizeGb(value: Int) {
        viewModelScope.launch { settings.setListeningCacheGb(value) }
    }

    fun clear() {
        viewModelScope.launch { runCatching { cache.clear() } }
    }

    private companion object {
        const val USAGE_REFRESH_MS = 1_000L
    }
}

/** The listening cache, shown with the downloads in Settings. */
@Composable
fun ListeningCacheSection(viewModel: ListeningCacheViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val sizeGb by viewModel.sizeGb.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_cache)) },
            supportingContent = { Text(stringResource(R.string.settings_cache_hint)) },
            trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = viewModel::setEnabled),
        )
        if (!enabled) return@Column
        val current = usage
        if (current != null) {
            Text(
                stringResource(R.string.settings_cache_usage, size(current.usedBytes), size(CacheLimit.bytes(sizeGb))),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val effective = CacheLimit.effective(sizeGb, current.usedBytes, current.freeBytes)
            if (effective < CacheLimit.bytes(sizeGb)) {
                Text(
                    stringResource(R.string.settings_cache_low_space, size(effective)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            stringResource(R.string.settings_cache_size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        CacheLimit.CHOICES_GB.forEach { gb ->
            val selected = gb == sizeGb
            // Sizes the phone has no room for can't be picked; the current one stays shown as chosen.
            val fits = current == null || CacheLimit.fits(gb, current.usedBytes, current.freeBytes)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_cache_size_gb, gb)) },
                supportingContent = if (!fits) {
                    { Text(stringResource(R.string.settings_cache_size_no_room)) }
                } else {
                    null
                },
                leadingContent = { RadioButton(selected = selected, onClick = null, enabled = fits || selected) },
                modifier = Modifier.selectable(
                    selected = selected,
                    enabled = fits || selected,
                    role = Role.RadioButton,
                    onClick = { viewModel.setSizeGb(gb) },
                ),
            )
        }
        val empty = (current?.usedBytes ?: 0L) == 0L
        val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_cache_clear)) },
            leadingContent = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            colors = if (empty) ListItemDefaults.colors(headlineColor = dimmed, leadingIconColor = dimmed) else ListItemDefaults.colors(),
            modifier = Modifier.clickable(enabled = !empty, role = Role.Button, onClick = viewModel::clear),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f

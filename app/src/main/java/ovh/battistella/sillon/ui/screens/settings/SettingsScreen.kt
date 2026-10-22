package ovh.battistella.sillon.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.LoudnessMode
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.settings.StreamQuality
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.ui.screens.downloads.DownloadsTotal
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val downloads: DownloadManager,
    repository: PlexRepository,
) : ViewModel() {
    val downloadsTotal: StateFlow<DownloadsTotal?> = flow {
        val serverId = runCatching { repository.currentServerId() }.getOrNull()
        if (serverId == null) emit(DownloadsTotal.NONE) else emitAll(downloads.observe(serverId).map(DownloadsTotal::of))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val wifiQuality = settings.wifiQuality.stateIn(viewModelScope, SharingStarted.Eagerly, StreamQuality.ORIGINAL)
    val mobileQuality = settings.mobileQuality.stateIn(viewModelScope, SharingStarted.Eagerly, StreamQuality.KBPS_320)
    val downloadWifiOnly = settings.downloadOnWifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val loudness = settings.loudness.stateIn(viewModelScope, SharingStarted.Eagerly, LoudnessMode.TRACK)
    val wallpaperColors = settings.wallpaperColors.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val sleeveWear = settings.sleeveWear.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSleeveWear(value: Boolean) {
        viewModelScope.launch { settings.setSleeveWear(value) }
    }

    val listenBySide = settings.listenBySide.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setListenBySide(value: Boolean) {
        viewModelScope.launch { settings.setListenBySide(value) }
    }

    val skipOneStar = settings.skipOneStar.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSkipOneStar(value: Boolean) {
        viewModelScope.launch { settings.setSkipOneStar(value) }
    }

    fun setWallpaperColors(value: Boolean) {
        viewModelScope.launch { settings.setWallpaperColors(value) }
    }

    fun setLoudness(value: LoudnessMode) {
        viewModelScope.launch { settings.setLoudness(value) }
    }

    fun setWifiQuality(value: StreamQuality) {
        viewModelScope.launch { settings.setWifiQuality(value) }
    }

    fun setMobileQuality(value: StreamQuality) {
        viewModelScope.launch { settings.setMobileQuality(value) }
    }

    fun setDownloadWifiOnly(value: Boolean) {
        viewModelScope.launch { downloads.setWifiOnly(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloads: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
    /** The listening cache settings, shown with the downloads; the app passes [ListeningCacheSection]. */
    listeningCache: @Composable () -> Unit = {},
) {
    val wifiQuality by viewModel.wifiQuality.collectAsStateWithLifecycle()
    val mobileQuality by viewModel.mobileQuality.collectAsStateWithLifecycle()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsStateWithLifecycle()
    val loudness by viewModel.loudness.collectAsStateWithLifecycle()
    val wallpaperColors by viewModel.wallpaperColors.collectAsStateWithLifecycle()
    val sleeveWear by viewModel.sleeveWear.collectAsStateWithLifecycle()
    val listenBySide by viewModel.listenBySide.collectAsStateWithLifecycle()
    val skipOneStar by viewModel.skipOneStar.collectAsStateWithLifecycle()
    val downloadsTotal by viewModel.downloadsTotal.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            // The downloads come first: they are also the way to the music when there is no connection.
            item { SectionTitle(stringResource(R.string.library_downloads)) }
            item { DownloadsEntry(downloadsTotal, onDownloads) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.downloads_wifi_only)) },
                    trailingContent = { Switch(checked = downloadWifiOnly, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = downloadWifiOnly,
                        role = Role.Switch,
                        onValueChange = viewModel::setDownloadWifiOnly,
                    ),
                )
            }
            item { listeningCache() }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_quality_wifi)) }
            item { QualityChoices(wifiQuality, viewModel::setWifiQuality) }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_quality_mobile)) }
            item { QualityChoices(mobileQuality, viewModel::setMobileQuality) }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_loudness)) }
            item {
                Text(
                    stringResource(R.string.settings_loudness_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Choices(
                    options = LoudnessMode.entries,
                    selected = loudness,
                    label = {
                        stringResource(
                            when (it) {
                                LoudnessMode.OFF -> R.string.settings_loudness_off
                                LoudnessMode.TRACK -> R.string.settings_loudness_track
                                LoudnessMode.ALBUM -> R.string.settings_loudness_album
                            },
                        )
                    },
                    onSelect = viewModel::setLoudness,
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sides)) },
                    supportingContent = { Text(stringResource(R.string.settings_sides_hint)) },
                    trailingContent = { Switch(checked = listenBySide, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = listenBySide,
                        role = Role.Switch,
                        onValueChange = viewModel::setListenBySide,
                    ),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_skip_one_star)) },
                    supportingContent = { Text(stringResource(R.string.settings_skip_one_star_hint)) },
                    trailingContent = { Switch(checked = skipOneStar, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = skipOneStar,
                        role = Role.Switch,
                        onValueChange = viewModel::setSkipOneStar,
                    ),
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_appearance)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sleeve_wear)) },
                    supportingContent = { Text(stringResource(R.string.settings_sleeve_wear_hint)) },
                    trailingContent = { Switch(checked = sleeveWear, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = sleeveWear,
                        role = Role.Switch,
                        onValueChange = viewModel::setSleeveWear,
                    ),
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_wallpaper_colors)) },
                        supportingContent = { Text(stringResource(R.string.settings_wallpaper_colors_hint)) },
                        trailingContent = { Switch(checked = wallpaperColors, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = wallpaperColors,
                            role = Role.Switch,
                            onValueChange = viewModel::setWallpaperColors,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsEntry(total: DownloadsTotal?, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_downloads)) },
        supportingContent = {
            Text(
                when {
                    total == null -> ""
                    total.albums == 0 -> stringResource(R.string.settings_downloads_none)
                    else -> pluralStringResource(
                        R.plurals.settings_downloads_summary,
                        total.albums,
                        total.albums,
                        Formatter.formatShortFileSize(context, total.bytes),
                    )
                },
            )
        },
        leadingContent = { Icon(Icons.Rounded.DownloadDone, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    options.forEach { option ->
        ListItem(
            headlineContent = { Text(label(option)) },
            leadingContent = { RadioButton(selected = option == selected, onClick = null) },
            modifier = Modifier.selectable(
                selected = option == selected,
                role = Role.RadioButton,
                onClick = { onSelect(option) },
            ),
        )
    }
}

@Composable
private fun QualityChoices(selected: StreamQuality, onSelect: (StreamQuality) -> Unit) {
    StreamQuality.entries.forEach { quality ->
        ListItem(
            headlineContent = {
                Text(
                    quality.kbps?.let { stringResource(R.string.settings_quality_kbps, it) }
                        ?: stringResource(R.string.settings_quality_original),
                )
            },
            supportingContent = if (quality == StreamQuality.ORIGINAL) {
                { Text(stringResource(R.string.settings_quality_original_hint)) }
            } else {
                null
            },
            leadingContent = { RadioButton(selected = quality == selected, onClick = null) },
            modifier = Modifier.selectable(
                selected = quality == selected,
                role = Role.RadioButton,
                onClick = { onSelect(quality) },
            ),
        )
    }
}

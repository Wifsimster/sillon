package ovh.battistella.sillon.ui.screens.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.settings.ThemeAccent
import ovh.battistella.sillon.data.settings.ThemeMode
import ovh.battistella.sillon.data.settings.ThemePreferences
import ovh.battistella.sillon.ui.components.SillonMark
import ovh.battistella.sillon.ui.theme.SillonTheme

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {
    val theme = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreferences())

    fun setMode(value: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(value) }
    }

    fun setAccent(value: ThemeAccent) {
        viewModelScope.launch { settings.setThemeAccent(value) }
    }

    fun setPureBlack(value: Boolean) {
        viewModelScope.launch { settings.setPureBlack(value) }
    }

    fun setWallpaperColors(value: Boolean) {
        viewModelScope.launch { settings.setWallpaperColors(value) }
    }
}

/** Light or dark, the accent colour, pure black and Material You, with a preview at the top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ThemeViewModel = hiltViewModel(),
    /** Material You needs Android 12; tests pass a value to render the page either way. */
    wallpaperAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val dark = theme.isDark(isSystemInDarkTheme())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme)) },
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
            item { ThemePreview(theme, Modifier.padding(16.dp)) }

            item { ThemeSectionTitle(stringResource(R.string.settings_theme_mode)) }
            item {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        ListItem(
                            headlineContent = { Text(stringResource(mode.label)) },
                            leadingContent = { RadioButton(selected = theme.mode == mode, onClick = null) },
                            modifier = Modifier.selectable(
                                selected = theme.mode == mode,
                                role = Role.RadioButton,
                                onClick = { viewModel.setMode(mode) },
                            ),
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme_pure_black)) },
                    supportingContent = { Text(stringResource(R.string.settings_theme_pure_black_hint)) },
                    trailingContent = { Switch(checked = theme.pureBlack, onCheckedChange = null, enabled = dark) },
                    modifier = Modifier.toggleable(
                        value = theme.pureBlack,
                        enabled = dark,
                        role = Role.Switch,
                        onValueChange = viewModel::setPureBlack,
                    ),
                )
            }

            item { HorizontalDivider() }
            item { ThemeSectionTitle(stringResource(R.string.settings_theme_accent)) }
            if (wallpaperAvailable) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_wallpaper_colors)) },
                        supportingContent = { Text(stringResource(R.string.settings_wallpaper_colors_hint)) },
                        trailingContent = { Switch(checked = theme.wallpaperColors, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = theme.wallpaperColors,
                            role = Role.Switch,
                            onValueChange = viewModel::setWallpaperColors,
                        ),
                    )
                }
            }
            val accentsOn = !(wallpaperAvailable && theme.wallpaperColors)
            item {
                AccentSwatches(
                    selected = theme.accent,
                    dark = dark,
                    enabled = accentsOn,
                    onSelect = viewModel::setAccent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!accentsOn) {
                item {
                    Text(
                        stringResource(R.string.settings_theme_accent_wallpaper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** A small now-playing card in the chosen theme: accent, surfaces, switches. */
@Composable
private fun ThemePreview(theme: ThemePreferences, modifier: Modifier = Modifier) {
    SillonTheme(theme) {
        val colors = MaterialTheme.colorScheme
        val description = stringResource(R.string.settings_theme_preview)
        Surface(
            modifier = modifier
                .clearAndSetSemantics { contentDescription = description }
                .fillMaxWidth()
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large),
            shape = MaterialTheme.shapes.large,
            color = colors.background,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_theme_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        SillonMark(Modifier.size(40.dp), groove = colors.onSurfaceVariant, label = colors.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_theme_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.settings_theme_preview_artist),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, tint = colors.onPrimary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 0.4f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = true, onCheckedChange = null)
                    Switch(checked = false, onCheckedChange = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(
    selected: ThemeAccent,
    dark: Boolean,
    enabled: Boolean,
    onSelect: (ThemeAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup().alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeAccent.entries.forEach { accent ->
            val name = stringResource(accent.label)
            val isSelected = accent == selected
            val ring by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                label = "ring",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(accent) },
                    )
                    .padding(4.dp),
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .border(2.dp, ring, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(accent.tones(dark).primary)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(accent.tones(dark).onPrimary),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ThemeSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

internal val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

internal val ThemeAccent.label: Int
    get() = when (this) {
        ThemeAccent.AMBER -> R.string.accent_amber
        ThemeAccent.RUST -> R.string.accent_rust
        ThemeAccent.OLIVE -> R.string.accent_olive
        ThemeAccent.TEAL -> R.string.accent_teal
        ThemeAccent.INDIGO -> R.string.accent_indigo
        ThemeAccent.PLUM -> R.string.accent_plum
    }

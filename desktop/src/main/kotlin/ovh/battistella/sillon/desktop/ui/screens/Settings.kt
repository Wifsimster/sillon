package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.LoudnessMode
import ovh.battistella.sillon.data.settings.StreamQuality
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.update.UpdateState

@Composable
fun SettingsScreen(app: AppState) {
    val strings = LocalStrings.current
    val graph = app.graph
    val settings = graph.settings
    val scope = rememberCoroutineScope()
    val quality by settings.wifiQuality.collectAsState(StreamQuality.ORIGINAL)
    val loudness by settings.loudness.collectAsState(LoudnessMode.TRACK)
    val skipOneStar by settings.skipOneStar.collectAsState(true)
    val sleeveWear by settings.sleeveWear.collectAsState(true)
    val autoUpdate by graph.prefs.autoUpdate.collectAsState(true)
    val update by graph.updater.state.collectAsState()
    val session by graph.repository.session.collectAsState(null)
    val sectionKey by graph.repository.musicSectionKey.collectAsState(null)
    var sections by remember { mutableStateOf<List<PlexDirectory>>(emptyList()) }
    LaunchedEffect(Unit) { sections = runCatching { graph.repository.musicSections() }.getOrDefault(emptyList()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Header(strings.settingsPlayback)
        Label(strings.settingsQuality)
        StreamQuality.entries.forEach { option ->
            val kbps = option.kbps
            Choice(
                selected = quality == option,
                title = if (kbps == null) strings.settingsQualityOriginal else strings.settingsQualityKbps(kbps),
                hint = if (kbps == null) strings.settingsQualityOriginalHint else null,
            ) { scope.launch { settings.setWifiQuality(option) } }
        }
        Label(strings.settingsLoudness, strings.settingsLoudnessHint)
        LoudnessMode.entries.forEach { option ->
            Choice(
                selected = loudness == option,
                title = when (option) {
                    LoudnessMode.OFF -> strings.settingsLoudnessOff
                    LoudnessMode.TRACK -> strings.settingsLoudnessTrack
                    LoudnessMode.ALBUM -> strings.settingsLoudnessAlbum
                },
            ) { scope.launch { settings.setLoudness(option) } }
        }
        Toggle(strings.settingsSkipOneStar, strings.settingsSkipOneStarHint, skipOneStar) { scope.launch { settings.setSkipOneStar(it) } }
        Header(strings.settingsAppearance)
        Toggle(strings.settingsSleeveWear, strings.settingsSleeveWearHint, sleeveWear) { scope.launch { settings.setSleeveWear(it) } }
        if (sections.size > 1) {
            Header(strings.settingsLibrary)
            val chosen = sectionKey ?: sections.first().key
            sections.forEach { section ->
                Choice(selected = section.key == chosen, title = section.title) {
                    scope.launch { graph.repository.selectMusicSection(section.key) }
                }
            }
        }
        Header(strings.settingsAccount)
        (session as? Session.Ready)?.let { Text(strings.settingsServer(it.server.name), style = MaterialTheme.typography.bodyLarge) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = {
                graph.playback.stop()
                scope.launch { graph.repository.changeServer() }
            }) { Text(strings.changeServer) }
            OutlinedButton(onClick = {
                graph.playback.stop()
                scope.launch { graph.repository.signOut() }
            }) { Text(strings.signOut) }
        }
        Header(strings.settingsAbout)
        Text(strings.settingsVersion(graph.version), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (graph.updater.enabled) {
            Header(strings.settingsUpdates)
            Toggle(strings.settingsAutoUpdate, strings.settingsAutoUpdateHint, autoUpdate) { scope.launch { graph.prefs.setAutoUpdate(it) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = graph.updater::retry, enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading) {
                    Text(strings.updateCheckNow)
                }
                val status = when (val u = update) {
                    UpdateState.Checking -> strings.updateChecking
                    UpdateState.UpToDate -> strings.updateUpToDate
                    is UpdateState.Failed -> if (u.release == null) strings.updateCheckFailed else strings.updateFailed(u.release.version)
                    is UpdateState.Available -> strings.updateAvailable(u.release.version)
                    is UpdateState.Downloading -> strings.updateDownloading(u.release.version, (u.progress * 100).toInt())
                    is UpdateState.Ready -> strings.updateReady(u.release.version)
                    UpdateState.Idle -> null
                }
                if (status != null) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Label(title: String, hint: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

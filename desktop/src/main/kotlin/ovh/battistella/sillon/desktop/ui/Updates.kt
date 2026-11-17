package ovh.battistella.sillon.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.update.Changelog
import ovh.battistella.sillon.desktop.update.Release
import ovh.battistella.sillon.desktop.update.UpdateState
import java.awt.Desktop
import java.net.URI

private fun openPage(release: Release) {
    runCatching { Desktop.getDesktop().browse(URI(release.pageUrl)) }
}

@Composable
fun UpdateBanner(app: AppState) {
    val strings = LocalStrings.current
    val updater = app.graph.updater
    val state by updater.state.collectAsState()
    val installed by updater.whatsNew.collectAsState()
    var notes by remember { mutableStateOf<Release?>(null) }
    var installFailed by remember { mutableStateOf(false) }

    fun install() {
        if (updater.install()) app.exit() else installFailed = true
    }

    (state as? UpdateState.NotInstalled)?.let { missed ->
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                strings.updateNotInstalled(missed.version),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = updater::retry) { Text(strings.retry) }
            TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(missed.pageUrl)) } }) { Text(strings.updateOpenPage) }
            TextButton(onClick = updater::later) { Text(strings.updateLater) }
        }
    }
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release.takeUnless { s.quiet }
        is UpdateState.Ready -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    }
    if (release != null) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.SystemUpdateAlt, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    when (val s = state) {
                        is UpdateState.Ready -> if (installFailed) strings.updateInstallFailed else strings.updateReady(release.version)
                        is UpdateState.Downloading -> strings.updateDownloading(release.version, (s.progress * 100).toInt())
                        is UpdateState.Failed -> strings.updateFailed(release.version)
                        else -> strings.updateAvailable(release.version)
                    },
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { notes = release }) { Text(strings.updateWhatsNew) }
                when (state) {
                    is UpdateState.Ready -> if (installFailed) {
                        TextButton(onClick = { openPage(release) }) { Text(strings.updateOpenPage) }
                    } else {
                        Button(onClick = ::install) { Text(strings.updateInstall) }
                    }
                    is UpdateState.Downloading -> Unit
                    is UpdateState.NotInstalled -> Unit
                    is UpdateState.Failed -> {
                        TextButton(onClick = updater::retry) { Text(strings.retry) }
                        TextButton(onClick = { openPage(release) }) { Text(strings.updateOpenPage) }
                    }
                    else -> TextButton(onClick = { openPage(release) }) { Text(strings.updateOpenPage) }
                }
                if (state !is UpdateState.Downloading) TextButton(onClick = updater::later) { Text(strings.updateLater) }
            }
            (state as? UpdateState.Downloading)?.let { LinearProgressIndicator(progress = { it.progress }, Modifier.fillMaxWidth()) }
        }
    }
    notes?.let { shown ->
        NotesDialog(
            title = strings.updateNotesTitle(shown.version),
            release = shown,
            onDismiss = { notes = null },
            confirm = if (state is UpdateState.Ready && !installFailed) strings.updateInstall to { notes = null; install() } else null,
            dismiss = strings.updateSkip to {
                notes = null
                updater.skip()
            },
        )
    }
    installed?.let { shown ->
        NotesDialog(
            title = strings.updateInstalledTitle(shown.version),
            release = shown,
            onDismiss = updater::dismissWhatsNew,
            confirm = null,
            dismiss = null,
        )
    }
}

@Composable
private fun NotesDialog(
    title: String,
    release: Release,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>?,
    dismiss: Pair<String, () -> Unit>?,
) {
    val strings = LocalStrings.current
    val sections = remember(release) { Changelog.parse(release.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (sections.isEmpty()) Text(strings.updateNoNotes)
                sections.forEach { section ->
                    section.title?.let { Text(strings.changelogTitle(it), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium) }
                    section.items.forEach { item ->
                        Row {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium)
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirm != null) Button(onClick = confirm.second) { Text(confirm.first) } else TextButton(onClick = onDismiss) { Text(strings.close) }
        },
        dismissButton = {
            Row {
                if (dismiss != null) TextButton(onClick = dismiss.second) { Text(dismiss.first) }
                if (confirm != null) TextButton(onClick = onDismiss) { Text(strings.close) }
            }
        },
    )
}

package ovh.battistella.sillon.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.sonos.Output
import ovh.battistella.sillon.sonos.OutputNotice
import ovh.battistella.sillon.sonos.SonosGroup

/** "Play on…": this phone, or a Sonos group found on the Wi-Fi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputSheet(onDismiss: () -> Unit, viewModel: OutputViewModel = hiltViewModel()) {
    val output by viewModel.output.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val block by viewModel.block.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.scan() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        OutputSheetContent(
            output = output,
            groups = groups,
            scanning = scanning,
            block = block,
            reducedMotion = rememberReducedMotion(),
            onPhone = {
                viewModel.backToPhone()
                onDismiss()
            },
            onGroup = { group ->
                viewModel.playOn(group)
                onDismiss()
            },
            onRescan = viewModel::scan,
        )
    }
}

@Composable
fun OutputSheetContent(
    output: Output,
    groups: List<SonosGroup>,
    scanning: Boolean,
    block: OutputBlock?,
    reducedMotion: Boolean,
    onPhone: () -> Unit,
    onGroup: (SonosGroup) -> Unit,
    onRescan: () -> Unit,
) {
    val current = when (output) {
        is Output.OnSonos -> output.group.id
        is Output.Connecting -> output.group.id
        Output.Phone -> null
    }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            stringResource(R.string.output_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        OutputRow(
            icon = Icons.Rounded.PhoneAndroid,
            title = stringResource(R.string.output_phone),
            detail = null,
            selected = current == null,
            enabled = true,
            onClick = onPhone,
        )
        groups.forEach { group ->
            OutputRow(
                icon = if (group.members.size > 1) Icons.Rounded.SpeakerGroup else Icons.Rounded.Speaker,
                title = group.name,
                detail = if (group.id == current && output is Output.Connecting) {
                    stringResource(R.string.output_connecting_short)
                } else {
                    pluralStringResource(R.plurals.output_rooms, group.members.size, group.members.size)
                },
                selected = group.id == current,
                enabled = block == null,
                onClick = { onGroup(group) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                scanning -> {
                    if (!reducedMotion) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.output_searching), style = MaterialTheme.typography.bodyMedium)
                }
                groups.isEmpty() -> Text(
                    stringResource(R.string.output_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                else -> Spacer(Modifier.weight(1f))
            }
            if (!scanning) TextButton(onClick = onRescan) { Text(stringResource(R.string.output_rescan)) }
        }
        block?.let {
            Text(
                stringResource(
                    when (it) {
                        OutputBlock.NOTHING_PLAYING -> R.string.output_block_nothing
                        OutputBlock.TOGETHER -> R.string.output_block_together
                        OutputBlock.MYSTERY -> R.string.output_block_mystery
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.output_sonos_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun OutputRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val state = stringResource(if (selected) R.string.output_selected else R.string.output_not_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = state }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val tint = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Under the player's header: where the music plays, or what just happened to the output. */
@Composable
fun OutputBanner(output: Output, notice: OutputNotice?, onOpen: () -> Unit, onNoticeSeen: () -> Unit) {
    val text = when {
        notice != null -> stringResource(noticeText(notice))
        output is Output.OnSonos -> stringResource(R.string.output_playing_on, output.group.name)
        output is Output.Connecting -> stringResource(R.string.output_connecting, output.group.name)
        else -> return
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .then(if (notice == null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (notice != null) TextButton(onClick = onNoticeSeen) { Text(stringResource(R.string.output_notice_ok)) }
        }
    }
}

private fun noticeText(notice: OutputNotice): Int = when (notice) {
    OutputNotice.UNREACHABLE -> R.string.output_notice_unreachable
    OutputNotice.COULD_NOT_PLAY -> R.string.output_notice_could_not_play
    OutputNotice.TAKEN_OVER -> R.string.output_notice_taken_over
    OutputNotice.FAILED -> R.string.output_notice_failed
    OutputNotice.PLEX_TOKEN_REFUSED -> R.string.output_notice_plex_token
    OutputNotice.JELLYFIN_TOKEN_REFUSED -> R.string.output_notice_jellyfin_token
    OutputNotice.MYSTERY_ON_PHONE -> R.string.output_notice_mystery
    OutputNotice.MYSTERY_NOT_ADDED -> R.string.output_notice_mystery_not_added
    OutputNotice.TOGETHER_ON_PHONE -> R.string.output_notice_together
}

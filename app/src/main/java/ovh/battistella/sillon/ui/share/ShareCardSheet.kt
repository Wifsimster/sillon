package ovh.battistella.sillon.ui.share

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R

/** "Share a card": pick a format and a style, add a few words, and hand the image to Android's share sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(
    source: ShareSource,
    onDismiss: () -> Unit,
    viewModel: ShareCardViewModel = hiltViewModel(key = "share-card"),
) {
    val context = LocalContext.current
    val quoteFormat = stringResource(R.string.share_card_quote)
    LaunchedEffect(source) { viewModel.open(source) { quoteFormat.format(it) } }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_card)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ShareCardContent(
            ui = ui,
            onFormat = viewModel::setFormat,
            onTemplate = viewModel::setTemplate,
            onNote = viewModel::setNote,
            onShowStars = viewModel::setShowStars,
            onLyric = viewModel::toggleLyric,
            onShare = {
                scope.launch {
                    val uri = viewModel.export() ?: return@launch
                    try {
                        context.startActivity(CardFiles.shareIntent(uri, chooserTitle))
                    } catch (_: ActivityNotFoundException) {
                        // No app can receive an image: nothing to do.
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardContent(
    ui: ShareCardUi,
    onFormat: (CardFormat) -> Unit,
    onTemplate: (CardTemplate) -> Unit,
    onNote: (String) -> Unit,
    onShowStars: (Boolean) -> Unit,
    onLyric: (Int) -> Unit,
    onShare: () -> Unit,
) {
    val source = ui.source ?: return
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.share_card), style = MaterialTheme.typography.titleLarge)

        val description = stringResource(
            R.string.share_card_preview,
            stringResource(templateName(ui.template)),
            listOfNotNull(source.title, source.artist).joinToString(", "),
        )
        val ratio = ui.format.width.toFloat() / ui.format.height
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .heightIn(max = 360.dp)
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ui.format == CardFormat.STORY)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                val preview = ui.preview
                if (preview != null) {
                    Image(preview.asImageBitmap(), contentDescription = null, modifier = Modifier.matchParentSize())
                } else {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
        }

        Text(stringResource(R.string.share_card_format), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CardFormat.entries.forEachIndexed { i, format ->
                SegmentedButton(
                    selected = ui.format == format,
                    onClick = { onFormat(format) },
                    shape = SegmentedButtonDefaults.itemShape(i, CardFormat.entries.size),
                ) { Text(stringResource(formatName(format))) }
            }
        }

        Text(stringResource(R.string.share_card_style), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CardTemplate.entries.forEachIndexed { i, template ->
                SegmentedButton(
                    selected = ui.template == template,
                    onClick = { onTemplate(template) },
                    shape = SegmentedButtonDefaults.itemShape(i, CardTemplate.entries.size),
                ) { Text(stringResource(templateName(template))) }
            }
        }

        OutlinedTextField(
            value = ui.note,
            onValueChange = onNote,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.share_card_note)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.share_card_note_count,
                        ui.note.codePointCount(0, ui.note.length),
                        CardRules.NOTE_MAX,
                    ),
                )
            },
            maxLines = 4,
        )

        if (ui.stars != null) {
            val label = stringResource(R.string.share_card_stars)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onShowStars(!ui.showStars) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f))
                Switch(checked = ui.showStars, onCheckedChange = null)
            }
        }
        if (ui.isJellyfin && !ui.loading) {
            Text(
                stringResource(R.string.share_card_stars_jellyfin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (ui.lyrics.isNotEmpty()) {
            Column {
                Text(stringResource(R.string.share_card_lyrics), style = MaterialTheme.typography.labelLarge)
                Text(
                    pluralStringResource(R.plurals.share_card_lyrics_hint, CardRules.LYRICS_MAX, CardRules.LYRICS_MAX),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val full = ui.chosenLyrics.size >= CardRules.LYRICS_MAX
                val chosenState = stringResource(R.string.share_card_lyric_chosen)
                ui.lyrics.forEachIndexed { index, line ->
                    val chosen = index in ui.chosenLyrics
                    val enabled = chosen || !full
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled, role = Role.Checkbox) { onLyric(index) }
                            .semantics { if (chosen) stateDescription = chosenState }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = chosen, onCheckedChange = null, enabled = enabled)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.share_card_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onShare,
            enabled = !ui.loading && !ui.exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.exporting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Share, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.share_card_send))
        }
    }
}

private fun formatName(format: CardFormat) = when (format) {
    CardFormat.SQUARE -> R.string.share_card_square
    CardFormat.STORY -> R.string.share_card_story
}

private fun templateName(template: CardTemplate) = when (template) {
    CardTemplate.VINYL -> R.string.share_card_vinyl
    CardTemplate.LABEL -> R.string.share_card_label
    CardTemplate.POLAROID -> R.string.share_card_polaroid
}

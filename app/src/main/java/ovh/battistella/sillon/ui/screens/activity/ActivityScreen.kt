package ovh.battistella.sillon.ui.screens.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.activity.ActiveListen
import ovh.battistella.sillon.data.activity.ActivityState
import ovh.battistella.sillon.data.activity.ListenState
import ovh.battistella.sillon.ui.components.Artwork

@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notify by viewModel.notify.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ActivityContent(
        state = state,
        notify = notify,
        onNotify = { on ->
            viewModel.setNotify(on)
            val missing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (on && missing) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onBack = onBack,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityContent(
    state: ActivityState,
    notify: Boolean,
    onNotify: (Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title)) },
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
            if (state == ActivityState.NotAllowed) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.activity_not_allowed_title)) },
                        supportingContent = { Text(stringResource(R.string.activity_not_allowed_body)) },
                    )
                }
                return@LazyColumn
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.activity_notify)) },
                    supportingContent = { Text(stringResource(R.string.activity_notify_hint)) },
                    trailingContent = { Switch(checked = notify, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = notify, role = Role.Switch, onValueChange = onNotify),
                )
            }
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.activity_now),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
            }
            when (state) {
                ActivityState.Loading -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                ActivityState.Unreachable -> item {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.activity_offline)) },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                is ActivityState.Live -> if (state.listens.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.activity_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(state.listens, key = { it.id }) { ListenRow(it) }
                }
                ActivityState.NotAllowed -> Unit
            }
        }
    }
}

@Composable
private fun ListenRow(listen: ActiveListen) {
    val user = listen.user.ifBlank { stringResource(R.string.activity_user_unknown) }
    val who = if (listen.mine) stringResource(R.string.activity_you, user) else user
    val paused = listen.state == ListenState.PAUSED
    ListItem(
        leadingContent = { Artwork(listen.artwork, Modifier.size(56.dp)) },
        overlineContent = {
            Text(
                listOf(who, listen.device).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = { Text(listen.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                val details = listOfNotNull(
                    listen.subtitle,
                    stringResource(R.string.activity_transcoding).takeIf { listen.transcoding },
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (listen.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { listen.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        drawStopIndicator = {},
                    )
                }
            }
        },
        trailingContent = if (paused) {
            { Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.activity_paused)) }
        } else {
            null
        },
    )
}

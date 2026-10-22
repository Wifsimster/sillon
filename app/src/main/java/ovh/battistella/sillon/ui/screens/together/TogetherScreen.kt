package ovh.battistella.sillon.ui.screens.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.jellyfin.GroupState
import ovh.battistella.sillon.together.EndReason
import ovh.battistella.sillon.together.MyStatus
import ovh.battistella.sillon.together.TogetherState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: TogetherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.together_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (available == true && !state.isActive) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.together_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            when {
                available == null -> Unit
                available == false -> notOnPlex()
                else -> when (val s = state) {
                    TogetherState.Off -> lobby(groups, viewModel)
                    is TogetherState.Connecting -> connecting()
                    is TogetherState.InGroup -> inGroup(s, viewModel::leave)
                    is TogetherState.Ended -> ended(s.reason, viewModel::dismiss)
                }
            }
        }
    }
}

private fun LazyListScope.intro() {
    item { Body(stringResource(R.string.together_intro)) }
}

private fun LazyListScope.notOnPlex() {
    intro()
    item {
        ListItem(
            leadingContent = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.together_plex_title)) },
            supportingContent = { Text(stringResource(R.string.together_plex_body)) },
        )
    }
}

private fun LazyListScope.lobby(groups: GroupList, viewModel: TogetherViewModel) {
    intro()
    item {
        Button(
            onClick = { viewModel.create(viewModel.deviceName) },
            enabled = groups !is GroupList.Unreachable,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.together_start)) }
    }
    item { Body(stringResource(R.string.together_start_hint), small = true) }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item { SectionTitle(stringResource(R.string.together_sessions)) }
    when (groups) {
        GroupList.Loading -> item {
            CircularProgressIndicator(Modifier.padding(16.dp))
        }
        GroupList.Unreachable -> item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.together_offline)) },
                trailingContent = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) } },
            )
        }
        is GroupList.Loaded -> if (groups.groups.isEmpty()) {
            item { Body(stringResource(R.string.together_no_sessions), small = true) }
        } else {
            items(groups.groups, key = { it.groupId }) { group ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Groups, contentDescription = null) },
                    headlineContent = { Text(group.groupName) },
                    supportingContent = {
                        Text(
                            pluralStringResource(R.plurals.together_member_count, group.participants.size, group.participants.size) +
                                " · " + group.participants.joinToString(", "),
                        )
                    },
                    trailingContent = {
                        OutlinedButton(onClick = { viewModel.join(group) }) { Text(stringResource(R.string.together_join)) }
                    },
                )
            }
        }
    }
}

private fun LazyListScope.connecting() {
    item {
        ListItem(
            leadingContent = { CircularProgressIndicator(Modifier.padding(4.dp)) },
            headlineContent = { Text(stringResource(R.string.together_connecting)) },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun LazyListScope.inGroup(state: TogetherState.InGroup, onLeave: () -> Unit) {
    item {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                state.groupName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(groupStateText(state)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (state.reconnecting) {
        item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.together_reconnecting)) },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
    item { SectionTitle(stringResource(R.string.together_this_phone)) }
    item {
        ListItem(
            leadingContent = { Icon(Icons.Rounded.Smartphone, contentDescription = null) },
            headlineContent = { Text(stringResource(statusText(state.me))) },
            supportingContent = state.driftMs?.let { drift ->
                @Composable { Text(stringResource(R.string.together_drift, abs(drift))) }
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    item {
        SectionTitle(pluralStringResource(R.plurals.together_member_count, state.members.size, state.members.size))
    }
    items(state.members.withIndex().toList(), key = { (index, name) -> "$index:$name" }) { (_, name) ->
        ListItem(
            leadingContent = { Icon(Icons.Rounded.Person, contentDescription = null) },
            headlineContent = { Text(name) },
            supportingContent = { Text(stringResource(memberText(state.groupState))) },
        )
    }
    item { Body(stringResource(R.string.together_members_hint), small = true) }
    item {
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.together_leave))
        }
    }
}

private fun LazyListScope.ended(reason: EndReason, onDismiss: () -> Unit) {
    item {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(endedText(reason)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Button(onClick = onDismiss) { Text(stringResource(R.string.together_ok)) }
        }
    }
}

private fun groupStateText(state: TogetherState.InGroup): Int = when {
    state.queue == null -> R.string.together_group_empty
    else -> when (state.groupState) {
        GroupState.Playing -> R.string.together_group_playing
        GroupState.Paused -> R.string.together_group_paused
        GroupState.Waiting -> R.string.together_group_waiting
        GroupState.Idle -> R.string.together_group_empty
    }
}

private fun memberText(state: GroupState): Int = when (state) {
    GroupState.Playing -> R.string.together_member_playing
    GroupState.Paused, GroupState.Idle -> R.string.together_member_paused
    GroupState.Waiting -> R.string.together_member_waiting
}

private fun statusText(status: MyStatus): Int = when (status) {
    MyStatus.Loading -> R.string.together_me_loading
    MyStatus.Ready -> R.string.together_me_ready
    MyStatus.Playing -> R.string.together_me_in_sync
    MyStatus.Paused -> R.string.together_me_paused
    MyStatus.CatchingUp -> R.string.together_me_catching_up
    MyStatus.CantPlay -> R.string.together_me_cant_play
}

private fun endedText(reason: EndReason): Int = when (reason) {
    EndReason.Left -> R.string.together_ended_left
    EndReason.ConnectionLost -> R.string.together_ended_connection
    EndReason.GroupGone -> R.string.together_ended_gone
    EndReason.AccessDenied -> R.string.together_ended_denied
    EndReason.Failed -> R.string.together_ended_failed
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Body(text: String, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

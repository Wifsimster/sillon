package ovh.battistella.sillon.ui.screens.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.components.LoadContent
import ovh.battistella.sillon.ui.components.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(viewModel: ServersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                actions = {
                    IconButton(onClick = viewModel::signOut) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.action_sign_out),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LoadContent(state, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { ui ->
            if (ui.servers.isEmpty()) {
                Message(
                    stringResource(R.string.servers_empty),
                    Modifier.padding(padding),
                    action = stringResource(R.string.action_retry) to viewModel::load,
                )
                return@LoadContent
            }
            Column(Modifier.padding(padding)) {
                ui.unreachable?.let {
                    Text(
                        stringResource(R.string.servers_unreachable, it),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn {
                    items(ui.servers, key = { it.clientIdentifier }) { server ->
                        val connecting = ui.connecting == server.name
                        ListItem(
                            headlineContent = { Text(server.name) },
                            supportingContent = {
                                Text(
                                    when {
                                        connecting -> stringResource(R.string.servers_connecting, server.name)
                                        server.owned -> stringResource(R.string.servers_owned)
                                        else -> stringResource(R.string.servers_shared)
                                    },
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                            trailingContent = {
                                if (connecting) CircularProgressIndicator(Modifier.size(24.dp))
                            },
                            modifier = Modifier.clickable { viewModel.select(server) },
                        )
                    }
                }
            }
        }
    }
}

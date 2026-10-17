package ovh.battistella.sillon.ui.screens.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R

/** Jellyfin sign-in: server address, then user name and password or Quick Connect. */
@Composable
fun JellyfinSignIn(onBack: () -> Unit, viewModel: JellyfinSignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val server = state.server

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.jellyfin_title), style = MaterialTheme.typography.headlineMedium)

        if (server == null) {
            Text(
                stringResource(R.string.jellyfin_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.jellyfin_address)) },
                placeholder = { Text(stringResource(R.string.jellyfin_address_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.findServer(address) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.findServer(address) },
                enabled = address.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_continue)) }
        } else if (state.quickConnectCode != null) {
            Text(
                stringResource(R.string.jellyfin_quick_connect_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                state.quickConnectCode.orEmpty(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = 6.sp,
            )
            Text(
                stringResource(R.string.signin_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.jellyfin_server_found, server.second.serverName.ifBlank { server.first }),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.jellyfin_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.jellyfin_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.signIn(username, password) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.signIn(username, password) },
                enabled = username.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.jellyfin_sign_in)) }
            OutlinedButton(
                onClick = viewModel::startQuickConnect,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.jellyfin_quick_connect)) }
        }

        if (state.busy) CircularProgressIndicator()
        state.error?.let { error ->
            Text(
                stringResource(
                    when (error) {
                        JellyfinSignInError.SERVER_NOT_FOUND -> R.string.jellyfin_error_server
                        JellyfinSignInError.WRONG_CREDENTIALS -> R.string.jellyfin_error_credentials
                        JellyfinSignInError.QUICK_CONNECT_UNAVAILABLE -> R.string.jellyfin_error_quick_connect
                        JellyfinSignInError.OTHER -> R.string.jellyfin_error_other
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        TextButton(onClick = { if (server != null) viewModel.changeServer() else onBack() }) {
            Text(stringResource(if (server != null) R.string.jellyfin_other_server else R.string.action_back))
        }
    }
}

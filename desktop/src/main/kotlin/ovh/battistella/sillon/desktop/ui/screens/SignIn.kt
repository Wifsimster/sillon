package ovh.battistella.sillon.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.jellyfin.JellyfinPublicInfo
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.AppState
import ovh.battistella.sillon.desktop.ui.components.SillonMark
import java.awt.Desktop
import java.net.URI

private enum class Provider { PLEX, JELLYFIN }

@Composable
fun SignInScreen(app: AppState) {
    val strings = LocalStrings.current
    var provider by rememberSaveable { mutableStateOf<Provider?>(null) }
    DisposableEffect(Unit) {
        app.typing = true
        onDispose { app.typing = false }
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SillonMark(Modifier.size(112.dp), groove = MaterialTheme.colorScheme.onSurface)
                when (provider) {
                    null -> {
                        Text(strings.chooseTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(strings.chooseBody, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { provider = Provider.PLEX }, Modifier.fillMaxWidth()) { Text("Plex") }
                        OutlinedButton(onClick = { provider = Provider.JELLYFIN }, Modifier.fillMaxWidth()) { Text("Jellyfin") }
                    }
                    Provider.PLEX -> PlexSignIn(app.graph.repository) { provider = null }
                    Provider.JELLYFIN -> JellyfinSignIn(app.graph.repository) { provider = null }
                }
            }
        }
    }
}

private sealed interface PlexSignInState {
    data object Loading : PlexSignInState
    data class Waiting(val code: String, val authUrl: String) : PlexSignInState
    data object Expired : PlexSignInState
    data class Failed(val message: String) : PlexSignInState
}

private class PlexSignInModel(private val repository: PlexRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<PlexSignInState>(PlexSignInState.Loading)
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        job?.cancel()
        _state.value = PlexSignInState.Loading
        job = scope.launch {
            try {
                val pins = repository.createPins()
                _state.value = PlexSignInState.Waiting(pins.link.code, repository.authUrl(pins.browser))
                val deadline = System.currentTimeMillis() + MAX_WAIT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(POLL_INTERVAL_MS)
                    for (pin in listOf(pins.browser, pins.link)) {
                        val approved = runCatching { repository.completeSignInIfApproved(pin) }.getOrDefault(false)
                        if (approved) return@launch
                    }
                }
                _state.value = PlexSignInState.Expired
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PlexSignInState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_WAIT_MS = 10 * 60_000L
    }
}

@Composable
private fun CopyableCode(code: String) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_SHOWN_MS)
            copied = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectionContainer {
            Text(code, style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 6.sp))
        }
        FilledTonalIconButton(onClick = {
            clipboard.setText(AnnotatedString(code))
            copied = true
        }) {
            Icon(
                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                if (copied) strings.codeCopied else strings.codeCopy,
                Modifier.size(20.dp),
            )
        }
    }
    Text(
        if (copied) strings.codeCopied else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private const val COPIED_SHOWN_MS = 2_000L

private fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

@Composable
private fun PlexSignIn(repository: PlexRepository, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val model = remember { PlexSignInModel(repository, scope) }
    LaunchedEffect(model) { model.start() }
    val state by model.state.collectAsState()
    Text(strings.plexTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(strings.plexBody, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    when (val s = state) {
        PlexSignInState.Loading -> CircularProgressIndicator()
        is PlexSignInState.Waiting -> {
            Button(onClick = { openInBrowser(s.authUrl) }, Modifier.fillMaxWidth()) { Text(strings.plexOpenBrowser) }
            Text(strings.plexOrLink, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            CopyableCode(s.code)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(strings.plexWaiting, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PlexSignInState.Expired -> {
            Text(strings.plexExpired, color = MaterialTheme.colorScheme.error)
            Button(onClick = model::start) { Text(strings.tryAgain) }
        }
        is PlexSignInState.Failed -> {
            Text(strings.errorGeneric(s.message), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Button(onClick = model::start) { Text(strings.tryAgain) }
        }
    }
    TextButton(onClick = onBack) { Text(strings.back) }
}

private enum class JellyfinError { SERVER_NOT_FOUND, WRONG_CREDENTIALS, QUICK_CONNECT_UNAVAILABLE, OTHER }

private data class JellyfinState(
    val server: Pair<String, JellyfinPublicInfo>? = null,
    val quickConnectCode: String? = null,
    val busy: Boolean = false,
    val error: JellyfinError? = null,
)

private class JellyfinSignInModel(private val repository: PlexRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(JellyfinState())
    val state = _state.asStateFlow()
    private var quickConnect: Job? = null

    fun findServer(address: String) {
        if (address.isBlank()) return
        busyWhile {
            val found = runCatching { repository.findJellyfinServer(address) }.getOrNull()
            if (found == null) {
                _state.update { it.copy(error = JellyfinError.SERVER_NOT_FOUND) }
            } else {
                _state.update { it.copy(server = found, error = null) }
            }
        }
    }

    fun signIn(username: String, password: String) {
        if (username.isBlank()) return
        val (url, info) = _state.value.server ?: return
        busyWhile {
            try {
                repository.signInJellyfin(url, info, username.trim(), password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = if (e is PlexHttpException && e.code in 400..401) JellyfinError.WRONG_CREDENTIALS else JellyfinError.OTHER
                _state.update { it.copy(error = error) }
            }
        }
    }

    fun startQuickConnect() {
        val (url, info) = _state.value.server ?: return
        quickConnect?.cancel()
        quickConnect = scope.launch {
            val request = runCatching { repository.startQuickConnect(url) }.getOrNull()
            if (request == null) {
                _state.update { it.copy(error = JellyfinError.QUICK_CONNECT_UNAVAILABLE) }
                return@launch
            }
            _state.update { it.copy(quickConnectCode = request.code, error = null) }
            repeat(QUICK_CONNECT_POLLS) {
                delay(POLL_MS)
                val done = runCatching { repository.completeQuickConnectIfApproved(url, info, request.secret) }.getOrDefault(false)
                if (done) return@launch
            }
            _state.update { it.copy(quickConnectCode = null) }
        }
    }

    fun changeServer() {
        quickConnect?.cancel()
        _state.value = JellyfinState()
    }

    private fun busyWhile(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val QUICK_CONNECT_POLLS = 100
    }
}

@Composable
private fun JellyfinSignIn(repository: PlexRepository, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val model = remember { JellyfinSignInModel(repository, scope) }
    val state by model.state.collectAsState()
    var address by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text(strings.jellyfinTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    val server = state.server
    if (server == null) {
        Text(strings.jellyfinBody, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.jellyfinAddress) },
            placeholder = { Text(strings.jellyfinAddressHint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { model.findServer(address) }),
        )
        Button(onClick = { model.findServer(address) }, Modifier.fillMaxWidth(), enabled = !state.busy && address.isNotBlank()) {
            Text(strings.continueAction)
        }
    } else {
        Text(strings.jellyfinServerFound(server.second.serverName.ifBlank { server.first }), style = MaterialTheme.typography.bodyLarge)
        val code = state.quickConnectCode
        if (code != null) {
            Text(strings.jellyfinQuickConnectBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            CopyableCode(code)
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.jellyfinUsername) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.jellyfinPassword) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { model.signIn(username, password) }),
            )
            Button(onClick = { model.signIn(username, password) }, Modifier.fillMaxWidth(), enabled = !state.busy && username.isNotBlank()) {
                Text(strings.signIn)
            }
            OutlinedButton(onClick = model::startQuickConnect, Modifier.fillMaxWidth(), enabled = !state.busy) {
                Text(strings.jellyfinQuickConnect)
            }
        }
        TextButton(onClick = model::changeServer) { Text(strings.jellyfinOtherServer) }
    }
    if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    state.error?.let { error ->
        Text(
            when (error) {
                JellyfinError.SERVER_NOT_FOUND -> strings.jellyfinErrorServer
                JellyfinError.WRONG_CREDENTIALS -> strings.jellyfinErrorCredentials
                JellyfinError.QUICK_CONNECT_UNAVAILABLE -> strings.jellyfinErrorQuickConnect
                JellyfinError.OTHER -> strings.jellyfinErrorOther
            },
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
    TextButton(onClick = onBack) { Text(strings.back) }
}

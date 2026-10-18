package ovh.battistella.sillon.ui.screens.signin

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.ui.components.SillonMark
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.R

/** First screen: pick the kind of server, then sign in to it. */
@Composable
fun SignInScreen() {
    var kind by rememberSaveable { mutableStateOf<ServerKind?>(null) }
    BackHandler(enabled = kind != null) { kind = null }
    when (kind) {
        null -> ServerKindChoice(onChoose = { kind = it })
        ServerKind.PLEX -> PlexSignIn(onBack = { kind = null })
        ServerKind.JELLYFIN -> JellyfinSignIn(onBack = { kind = null })
    }
}

@Composable
private fun ServerKindChoice(onChoose: (ServerKind) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SillonMark(Modifier.size(96.dp), groove = MaterialTheme.colorScheme.onBackground)
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(stringResource(R.string.signin_choose_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.signin_choose_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        ServerButton(R.drawable.ic_plex, stringResource(R.string.server_plex)) { onChoose(ServerKind.PLEX) }
        ServerButton(R.drawable.ic_jellyfin, stringResource(R.string.server_jellyfin)) { onChoose(ServerKind.JELLYFIN) }
    }
}

/** A server type, with its logo in its own colours. */
@Composable
private fun ServerButton(@DrawableRes logo: Int, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(painterResource(logo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlexSignIn(onBack: () -> Unit, viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.startIfIdle() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.signin_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            SignInState.Loading -> CircularProgressIndicator()
            is SignInState.Waiting -> {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.authUrl)))
                }) {
                    Text(stringResource(R.string.signin_open_browser))
                }
                Text(
                    stringResource(R.string.signin_or_link),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    s.code,
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
            }
            SignInState.Expired -> {
                Text(stringResource(R.string.signin_expired))
                OutlinedButton(onClick = viewModel::start) { Text(stringResource(R.string.signin_retry)) }
            }
            is SignInState.Failed -> {
                Text(stringResource(R.string.error_generic, s.message), textAlign = TextAlign.Center)
                OutlinedButton(onClick = viewModel::start) { Text(stringResource(R.string.signin_retry)) }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}


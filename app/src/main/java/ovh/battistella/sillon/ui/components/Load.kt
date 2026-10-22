package ovh.battistella.sillon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/**
 * Opens the downloaded music, when the screen sits in the library. A failed load is usually a
 * missing connection: the error then offers the downloads, which play without one.
 */
val LocalOpenDownloads = staticCompositionLocalOf<(() -> Unit)?> { null }

fun Throwable.toFailed(): Load.Failed = Load.Failed(message ?: javaClass.simpleName)

@Composable
fun <T> LoadContent(
    load: Load<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (load) {
        Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Load.Failed -> Message(
            text = stringResource(R.string.error_generic, load.message),
            modifier = modifier,
            action = stringResource(R.string.action_retry) to onRetry,
            secondary = LocalOpenDownloads.current?.let { stringResource(R.string.action_open_downloads) to it },
        )
        is Load.Ready -> content(load.value)
    }
}

@Composable
fun Message(
    text: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = action.second) { Text(action.first) }
        if (secondary != null) OutlinedButton(onClick = secondary.second) { Text(secondary.first) }
    }
}

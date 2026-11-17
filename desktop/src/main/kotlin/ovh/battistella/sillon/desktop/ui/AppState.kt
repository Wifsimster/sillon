package ovh.battistella.sillon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import ovh.battistella.sillon.desktop.AppGraph

sealed interface Screen {
    data object Listen : Screen
    data object Search : Screen
    data object Artists : Screen
    data object Albums : Screen
    data object Playlists : Screen
    data object Genres : Screen
    data class Genre(val key: String) : Screen
    data object Downloads : Screen
    data object Settings : Screen
    data object FiveStars : Screen
    data class Artist(val key: String) : Screen
    data class Album(val key: String) : Screen
    data class Playlist(val key: String) : Screen
}

class StackEntry(val id: Int, val screen: Screen, val scope: CoroutineScope) {
    private val models = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> model(name: String, create: (CoroutineScope) -> T): T = models.getOrPut(name) { create(scope) } as T
}

class AppState(val graph: AppGraph) {
    val stack = mutableStateListOf<StackEntry>()
    var playerOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var typing by mutableStateOf(false)
    var exit: () -> Unit = {}
    private var nextId = 0

    val current: StackEntry? get() = stack.lastOrNull()

    val root: Screen? get() = stack.firstOrNull()?.screen

    private fun entry(screen: Screen) = StackEntry(nextId++, screen, graph.scope + Job())

    fun reset(screen: Screen = Screen.Listen) {
        stack.forEach { it.scope.cancel() }
        stack.clear()
        stack.add(entry(screen))
        playerOpen = false
    }

    fun open(screen: Screen) {
        playerOpen = false
        if (current?.screen == screen) return
        stack.add(entry(screen))
    }

    fun back(): Boolean {
        if (playerOpen) {
            playerOpen = false
            return true
        }
        if (stack.size <= 1) return false
        stack.removeLast().scope.cancel()
        return true
    }

    fun clear() {
        stack.forEach { it.scope.cancel() }
        stack.clear()
        playerOpen = false
        queueOpen = false
    }
}

@Composable
fun rememberAppState(graph: AppGraph): AppState = remember(graph) { AppState(graph) }

fun handleShortcut(event: KeyEvent, app: AppState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    val playback = app.graph.playback
    return when {
        event.key == Key.Escape -> app.back()
        command && event.key == Key.DirectionRight -> true.also { playback.next() }
        command && event.key == Key.DirectionLeft -> true.also { playback.previous() }
        command && event.key == Key.F -> true.also { if (app.stack.isNotEmpty()) app.reset(Screen.Search) }
        event.key == Key.Spacebar && !app.typing && app.stack.isNotEmpty() -> true.also { playback.playPause() }
        else -> false
    }
}

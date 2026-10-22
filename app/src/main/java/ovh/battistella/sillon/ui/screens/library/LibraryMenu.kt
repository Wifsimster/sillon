package ovh.battistella.sillon.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.repository.PlexRepository
import javax.inject.Inject

data class LibraryMenuUi(val sections: List<PlexDirectory> = emptyList(), val currentKey: String? = null)

@HiltViewModel
class LibraryMenuViewModel @Inject constructor(
    private val repository: PlexRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryMenuUi())
    val state = _state.asStateFlow()

    /** Listen together runs on Jellyfin SyncPlay; Plex has no equivalent, so the entry is not offered there. */
    val together = repository.isJellyfin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            repository.musicSectionKey.collect {
                runCatching {
                    _state.value = LibraryMenuUi(repository.musicSections(), repository.currentMusicSection()?.key)
                }
            }
        }
    }

    fun select(key: String) {
        viewModelScope.launch { repository.selectMusicSection(key) }
    }

    fun changeServer() {
        viewModelScope.launch { repository.changeServer() }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}

val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }
val LocalOpenTogether = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun LibraryMenu(viewModel: LibraryMenuViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val together by viewModel.together.collectAsStateWithLifecycle()
    LibraryMenu(
        state = state,
        together = together,
        onSelect = viewModel::select,
        onChangeServer = viewModel::changeServer,
        onSignOut = viewModel::signOut,
    )
}

@Composable
fun LibraryMenu(
    state: LibraryMenuUi,
    together: Boolean,
    onSelect: (String) -> Unit,
    onChangeServer: () -> Unit,
    onSignOut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (state.sections.size > 1) {
            state.sections.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.title) },
                    leadingIcon = {
                        if (section.key == state.currentKey) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        onSelect(section.key)
                    },
                )
            }
            HorizontalDivider()
        }
        if (together) {
            val openTogether = LocalOpenTogether.current
            DropdownMenuItem(
                text = { Text(stringResource(R.string.together_open)) },
                onClick = {
                    open = false
                    openTogether()
                },
            )
        }
        val openSettings = LocalOpenSettings.current
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            onClick = {
                open = false
                openSettings()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_change_server)) },
            onClick = {
                open = false
                onChangeServer()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_sign_out)) },
            onClick = {
                open = false
                onSignOut()
            },
        )
    }
}

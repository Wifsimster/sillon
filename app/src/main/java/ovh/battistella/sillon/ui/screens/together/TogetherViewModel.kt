package ovh.battistella.sillon.ui.screens.together

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.jellyfin.SyncPlayGroupInfo
import ovh.battistella.sillon.together.ListenTogether
import javax.inject.Inject

sealed interface GroupList {
    data object Loading : GroupList
    data class Loaded(val groups: List<SyncPlayGroupInfo>) : GroupList

    data object Unreachable : GroupList
}

@HiltViewModel
class TogetherViewModel @Inject constructor(
    private val together: ListenTogether,
) : ViewModel() {
    val state = together.state
    val available = together.available.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _groups = MutableStateFlow<GroupList>(GroupList.Loading)
    val groups = _groups.asStateFlow()

    val deviceName: String = listOfNotNull(Build.MODEL).joinToString(" ").ifBlank { "Sillon" }

    init {
        refresh()
    }

    fun refresh() {
        _groups.value = GroupList.Loading
        viewModelScope.launch {
            _groups.value = runCatching { together.groups() }
                .fold(onSuccess = { GroupList.Loaded(it) }, onFailure = { GroupList.Unreachable })
        }
    }

    fun create(name: String) = together.create(name)

    fun join(group: SyncPlayGroupInfo) = together.join(group)

    fun cancel() {
        together.cancel()
        refresh()
    }

    fun leave() = together.leave()

    fun dismiss() {
        together.dismiss()
        refresh()
    }
}

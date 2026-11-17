package ovh.battistella.sillon.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.sillon.activity.ServerActivityWatch
import ovh.battistella.sillon.data.activity.ActivityState
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val watch: ServerActivityWatch,
) : ViewModel() {
    val state: StateFlow<ActivityState> =
        watch.states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState.Loading)

    val notify: StateFlow<Boolean> = watch.notify.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setNotify(value: Boolean) = watch.setNotify(value)
}

@HiltViewModel
class ActivityEntryViewModel @Inject constructor(
    private val watch: ServerActivityWatch,
) : ViewModel() {
    val allowed: StateFlow<Boolean> = watch.allowed

    val openRequested: StateFlow<Boolean> = watch.openRequested

    fun opened() = watch.opened()
}

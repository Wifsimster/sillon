package ovh.battistella.sillon.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.sillon.data.genres.GenreRepository
import ovh.battistella.sillon.data.home.SearchHomeRepository
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.Session
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    repository: PlexRepository,
    searchHome: SearchHomeRepository,
    genres: GenreRepository,
) : ViewModel() {
    val session: StateFlow<Session?> = repository.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Records what plays for the search page, and forgets it on sign-out.
        searchHome.start()
        genres.start()
    }
}

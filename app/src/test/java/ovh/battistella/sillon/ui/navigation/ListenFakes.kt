package ovh.battistella.sillon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import ovh.battistella.sillon.data.repository.Station
import ovh.battistella.sillon.data.settings.CachedLibraryPlays
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.screens.library.LibraryMenuUi
import ovh.battistella.sillon.ui.screens.library.LibraryMenuViewModel
import ovh.battistella.sillon.ui.screens.library.NeverPlayedViewModel
import ovh.battistella.sillon.ui.screens.library.PlaylistsViewModel
import ovh.battistella.sillon.ui.screens.library.TileUi
import ovh.battistella.sillon.ui.screens.listen.ListenRadiosViewModel
import ovh.battistella.sillon.ui.screens.listen.ListenViewModel
import ovh.battistella.sillon.ui.screens.listen.OfflineMusic
import ovh.battistella.sillon.ui.screens.mystery.MysteryViewModel

/**
 * The Listen and Playlists view models, with synthetic data, already in a store: the screens and
 * cards that call `hiltViewModel()` find them there, so they draw without Hilt.
 */
internal class ListenFakes(
    online: Boolean = true,
    offline: OfflineMusic = OfflineMusic(),
    playlists: List<TileUi> = PLAYLISTS,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val listen = mockk<ListenViewModel>(relaxed = true) {
        every { this@mockk.online } returns MutableStateFlow(online)
        every { this@mockk.offline } returns MutableStateFlow(offline)
        every { isJellyfin } returns MutableStateFlow(false)
        every { starting } returns MutableStateFlow(null)
        every { notice } returns MutableStateFlow(null)
    }

    val radios = mockk<ListenRadiosViewModel>(relaxed = true) {
        every { this@mockk.online } returns MutableStateFlow(online)
        every { stations } returns MutableStateFlow(
            listOf(Station("s1", null), Station("s2", "Synthetic radio"), Station("s3", "Synthetic deep cuts")),
        )
        every { starting } returns MutableStateFlow(null)
    }

    init {
        seed(radios)
        seed(listen)
        seed(
            mockk<PlaylistsViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow(Load.Ready(playlists))
            },
        )
        seed(
            mockk<NeverPlayedViewModel>(relaxed = true) {
                every { plays } returns MutableStateFlow(CachedLibraryPlays(neverPlayed = 1_234, total = 4_321, fetchedAt = 0))
                every { starting } returns MutableStateFlow(null)
                every { notice } returns MutableStateFlow(null)
            },
        )
        seed(
            mockk<MysteryViewModel>(relaxed = true) {
                every { starting } returns MutableStateFlow(false)
                every { problem } returns MutableStateFlow(null)
                every { anyAlbum } returns MutableStateFlow(false)
            },
        )
        seed(mockk<LibraryMenuViewModel>(relaxed = true) { every { state } returns MutableStateFlow(LibraryMenuUi()) })
    }

    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides this, content = content)
    }

    private inline fun <reified VM : ViewModel> seed(model: VM) {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
        }
        ViewModelProvider(viewModelStore, factory)[VM::class.java]
    }

    private companion object {
        val PLAYLISTS = listOf("Synthetic Mix", "Sunday Morning", "Road Trip", "Late Night").mapIndexed { i, title ->
            TileUi(key = "p$i", title = title, subtitle = null, artworkUrl = null, trackCount = 10 + i * 7)
        }
    }
}

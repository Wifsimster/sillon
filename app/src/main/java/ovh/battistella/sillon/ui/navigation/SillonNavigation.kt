package ovh.battistella.sillon.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.ui.components.MiniPlayer
import ovh.battistella.sillon.ui.screens.library.AlbumScreen
import ovh.battistella.sillon.ui.screens.library.ArtistScreen
import ovh.battistella.sillon.ui.screens.library.ArtistsScreen
import ovh.battistella.sillon.ui.screens.player.PlayerScreen
import ovh.battistella.sillon.ui.screens.player.PlayerViewModel
import ovh.battistella.sillon.ui.screens.servers.ServersScreen
import ovh.battistella.sillon.ui.screens.signin.SignInScreen

/** Sign-in, then server choice, then the library — driven by the stored session. */
@Composable
fun SillonRoot(viewModel: RootViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val s = session) {
        null -> Unit
        Session.SignedOut -> SignInScreen()
        is Session.NeedsServer -> ServersScreen()
        // Keyed on the server so switching servers starts from a fresh back stack.
        is Session.Ready -> key(s.server.machineId) { LibraryNavHost() }
    }
}

@Composable
private fun LibraryNavHost() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val onPlayerScreen = backStack?.destination?.route == Routes.PLAYER

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = player.hasMedia && !onPlayerScreen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                MiniPlayer(
                    state = player,
                    onOpen = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                    onPlayPause = playerViewModel::playPause,
                    onNext = playerViewModel::next,
                )
            }
        },
    ) { padding ->
        // Screens draw under the top system bar themselves; only the bottom
        // inset (mini-player) is passed down for their scrolling content.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = Routes.ARTISTS) {
                composable(Routes.ARTISTS) {
                    ArtistsScreen(onArtist = { navController.navigate(Routes.artist(it)) }, contentPadding = bottom)
                }
                composable(
                    Routes.ARTIST,
                    arguments = listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
                ) {
                    ArtistScreen(
                        onAlbum = { navController.navigate(Routes.album(it)) },
                        onBack = { navController.popBackStack() },
                        contentPadding = bottom,
                    )
                }
                composable(
                    Routes.ALBUM,
                    arguments = listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
                ) {
                    AlbumScreen(onBack = { navController.popBackStack() }, contentPadding = bottom)
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(onClose = { navController.popBackStack() })
                }
            }
        }
    }
}

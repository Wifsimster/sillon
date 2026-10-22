package ovh.battistella.sillon.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import ovh.battistella.sillon.R
import ovh.battistella.sillon.ui.screens.downloads.DownloadedAlbumScreen
import ovh.battistella.sillon.ui.screens.downloads.DownloadsScreen
import ovh.battistella.sillon.ui.screens.library.AlbumsScreen
import ovh.battistella.sillon.ui.screens.library.FiveStarsScreen
import ovh.battistella.sillon.ui.screens.library.LocalOpenSettings
import ovh.battistella.sillon.ui.screens.library.LocalOpenTogether
import ovh.battistella.sillon.ui.screens.library.PlaylistScreen
import ovh.battistella.sillon.ui.screens.library.PlaylistsScreen
import ovh.battistella.sillon.ui.screens.search.SearchScreen
import ovh.battistella.sillon.ui.screens.settings.SettingsScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ovh.battistella.sillon.ui.components.LocalOpenDownloads
import ovh.battistella.sillon.ui.components.MiniPlayer
import ovh.battistella.sillon.ui.screens.library.AlbumScreen
import ovh.battistella.sillon.ui.screens.library.ArtistScreen
import ovh.battistella.sillon.ui.screens.library.ArtistsScreen
import ovh.battistella.sillon.ui.screens.listen.ListenScreen
import ovh.battistella.sillon.ui.screens.player.PlayerScreen
import ovh.battistella.sillon.ui.screens.player.PlayerViewModel
import ovh.battistella.sillon.ui.screens.servers.ServersScreen
import ovh.battistella.sillon.ui.screens.signin.SignInScreen
import ovh.battistella.sillon.ui.screens.together.TogetherScreen

@Composable
fun SillonRoot(viewModel: RootViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val s = session) {
        null -> Unit
        Session.SignedOut -> SignInScreen()
        is Session.NeedsServer -> ServersScreen()
        is Session.Ready -> key(s.server.machineId) { LibraryNavHost() }
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

/**
 * Listen, Playlists and Search. Artists and albums open from search, links and the player; the
 * downloads from the settings. Their routes stay in the graph.
 */
private val tabs = listOf(
    Tab(Routes.LISTEN, R.string.listen_tab, Icons.Rounded.Headphones),
    Tab(Routes.PLAYLISTS, R.string.library_playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    Tab(Routes.SEARCH, R.string.library_search, Icons.Rounded.Search),
)

/** The first page the app opens on. */
internal const val START_DESTINATION = Routes.LISTEN

/** The tab to show for a saved one: a tab that left the bar (Artists, Albums, Downloads) falls back to Listen. */
internal fun restoredTab(saved: String?): String =
    saved?.takeIf { route -> tabs.any { it.route == route } } ?: START_DESTINATION

internal val CurrentTabSaver: Saver<MutableState<String>, String> = Saver(
    save = { it.value },
    restore = { mutableStateOf(restoredTab(it)) },
)

@Composable
internal fun SillonNavigationBar(currentTab: String, onTab: (String) -> Unit) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == currentTab,
                onClick = { onTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun LibraryNavHost() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onPlayerScreen = destination?.route == Routes.PLAYER
    var currentTab by rememberSaveable(saver = CurrentTabSaver) { mutableStateOf(START_DESTINATION) }
    LaunchedEffect(destination?.route) {
        destination?.route?.takeIf { route -> tabs.any { it.route == route } }?.let { currentTab = it }
    }

    Scaffold(
        bottomBar = {
            if (!onPlayerScreen) {
                Column {
                    AnimatedVisibility(
                        visible = player.hasMedia,
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
                    SillonNavigationBar(currentTab) { route ->
                        if (route == currentTab) {
                            navController.popBackStack(route, inclusive = false)
                        } else {
                            currentTab = route
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        val keyArg = listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType })
        val openArtist: (String) -> Unit = { navController.navigate(Routes.artist(it)) }
        val openAlbum: (String) -> Unit = { navController.navigate(Routes.album(it)) }
        val back: () -> Unit = { navController.popBackStack() }
        Box(Modifier.fillMaxSize()) {
            val openTogether: () -> Unit = { navController.navigate(Routes.TOGETHER) { launchSingleTop = true } }
            val openDownloads: () -> Unit = { navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true } }
            CompositionLocalProvider(
                LocalOpenDownloads provides openDownloads,
                LocalOpenSettings provides { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                LocalOpenTogether provides openTogether,
            ) {
                NavHost(navController = navController, startDestination = START_DESTINATION) {
                    composable(Routes.LISTEN) {
                        ListenScreen(onFiveStarsList = { navController.navigate(Routes.FIVE_STARS) }, contentPadding = bottom)
                    }
                    // The full artist and album grids left the bar; kept as ordinary destinations,
                    // and for back stacks saved before the change.
                    composable(Routes.ARTISTS) { ArtistsScreen(onArtist = openArtist, contentPadding = bottom) }
                    composable(Routes.ALBUMS) { AlbumsScreen(onAlbum = openAlbum, contentPadding = bottom) }
                    composable(Routes.PLAYLISTS) {
                        PlaylistsScreen(
                            onPlaylist = { navController.navigate(Routes.playlist(it)) },
                            contentPadding = bottom,
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(onArtist = openArtist, onAlbum = openAlbum, contentPadding = bottom)
                    }
                    composable(Routes.DOWNLOADS) {
                        DownloadsScreen(
                            onAlbum = { navController.navigate(Routes.downloadedAlbum(it)) },
                            onBack = back,
                            contentPadding = bottom,
                        )
                    }
                    composable(Routes.DOWNLOADED_ALBUM, arguments = keyArg) {
                        DownloadedAlbumScreen(onBack = back, contentPadding = bottom)
                    }
                    composable(Routes.ARTIST, arguments = keyArg) {
                        ArtistScreen(onAlbum = openAlbum, onBack = back, contentPadding = bottom)
                    }
                    composable(Routes.ALBUM, arguments = keyArg) {
                        AlbumScreen(onBack = back, contentPadding = bottom)
                    }
                    composable(Routes.FIVE_STARS) {
                        FiveStarsScreen(onBack = back, contentPadding = bottom)
                    }
                    composable(Routes.PLAYLIST, arguments = keyArg) {
                        PlaylistScreen(onBack = back, contentPadding = bottom)
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = back, onDownloads = openDownloads, contentPadding = bottom)
                    }
                    composable(Routes.TOGETHER) {
                        TogetherScreen(onBack = back, contentPadding = bottom)
                    }
                    composable(Routes.PLAYER) {
                        PlayerScreen(onClose = back, onTogether = openTogether)
                    }
                }
            }
        }
    }
}

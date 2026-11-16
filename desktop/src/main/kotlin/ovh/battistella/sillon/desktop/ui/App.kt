package ovh.battistella.sillon.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.desktop.i18n.LocalStrings
import ovh.battistella.sillon.desktop.ui.components.SillonMark
import ovh.battistella.sillon.desktop.ui.player.NowPlaying
import ovh.battistella.sillon.desktop.ui.player.PlayerBar
import ovh.battistella.sillon.desktop.ui.player.QueuePanel
import ovh.battistella.sillon.desktop.ui.screens.ArtistScreen
import ovh.battistella.sillon.desktop.ui.screens.CollectionKind
import ovh.battistella.sillon.desktop.ui.screens.CollectionScreen
import ovh.battistella.sillon.desktop.ui.screens.DownloadsScreen
import ovh.battistella.sillon.desktop.ui.screens.GenreScreen
import ovh.battistella.sillon.desktop.ui.screens.GenresScreen
import ovh.battistella.sillon.desktop.ui.screens.ListenScreen
import ovh.battistella.sillon.desktop.ui.screens.PlaylistsScreen
import ovh.battistella.sillon.desktop.ui.screens.SearchScreen
import ovh.battistella.sillon.desktop.ui.screens.SectionGridScreen
import ovh.battistella.sillon.desktop.ui.screens.ServersScreen
import ovh.battistella.sillon.desktop.ui.screens.SettingsScreen
import ovh.battistella.sillon.desktop.ui.screens.SignInScreen

@Composable
fun SillonApp(app: AppState) {
    val session by app.graph.repository.session.collectAsState(null)
    when (val s = session) {
        null -> Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        Session.SignedOut -> {
            LaunchedEffect(Unit) { app.clear() }
            Column {
                UpdateBanner(app)
                SignInScreen(app)
            }
        }
        is Session.NeedsServer -> {
            LaunchedEffect(Unit) { app.clear() }
            Column {
                UpdateBanner(app)
                ServersScreen(app)
            }
        }
        is Session.Ready -> key(s.server.machineId) {
            LaunchedEffect(Unit) {
                app.reset(Screen.Listen)
                app.graph.playback.restore()
            }
            Shell(app)
        }
    }
}

@Composable
private fun Shell(app: AppState) {
    val strings = LocalStrings.current
    val unavailable by app.graph.playback.unavailable.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            UpdateBanner(app)
            unavailable?.let { reason ->
                Text(
                    strings.audioUnavailable(reason),
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(Modifier.weight(1f)) {
                Sidebar(app)
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Content(app)
                    if (app.playerOpen) NowPlaying(app)
                }
                if (app.queueOpen) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    QueuePanel(app, Modifier.width(360.dp).fillMaxHeight())
                }
            }
            PlayerBar(app)
        }
    }
}

@Composable
private fun Sidebar(app: AppState) {
    val strings = LocalStrings.current
    Column(Modifier.width(232.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SillonMark(Modifier.size(40.dp), groove = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text("Sillon", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        NavItem(app, Screen.Listen, Icons.Filled.Headphones, strings.listen)
        NavItem(app, Screen.Search, Icons.Filled.Search, strings.search)
        NavItem(app, Screen.Artists, Icons.Filled.Person, strings.artists)
        NavItem(app, Screen.Albums, Icons.Filled.Album, strings.albums)
        NavItem(app, Screen.Playlists, Icons.AutoMirrored.Filled.QueueMusic, strings.playlists)
        NavItem(app, Screen.Genres, Icons.Filled.Category, strings.genres)
        NavItem(app, Screen.Downloads, Icons.Filled.Download, strings.downloads)
        Spacer(Modifier.weight(1f))
        NavItem(app, Screen.Settings, Icons.Filled.Settings, strings.settings)
    }
}

@Composable
private fun NavItem(app: AppState, screen: Screen, icon: ImageVector, label: String) {
    val selected = app.root == screen
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable { app.reset(screen) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Content(app: AppState) {
    val strings = LocalStrings.current
    val entry = app.current ?: return
    val holder = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.stack.size > 1) {
                IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, strings.back) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                when (entry.screen) {
                    Screen.Listen -> strings.listen
                    Screen.Search -> strings.search
                    Screen.Artists -> strings.artists
                    Screen.Albums -> strings.albums
                    Screen.Playlists -> strings.playlists
                    Screen.Genres -> strings.genres
                    Screen.Downloads -> strings.downloads
                    Screen.Settings -> strings.settings
                    else -> ""
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider(entry.id) {
                when (val screen = entry.screen) {
                    Screen.Listen -> ListenScreen(app, entry)
                    Screen.Search -> SearchScreen(app, entry)
                    Screen.Artists -> SectionGridScreen(app, entry, artists = true)
                    Screen.Albums -> SectionGridScreen(app, entry, artists = false)
                    Screen.Playlists -> PlaylistsScreen(app, entry)
                    Screen.Genres -> GenresScreen(app, entry)
                    is Screen.Genre -> GenreScreen(app, entry, screen.key)
                    Screen.Downloads -> DownloadsScreen(app, entry)
                    Screen.Settings -> SettingsScreen(app)
                    Screen.FiveStars -> CollectionScreen(app, entry, CollectionKind.FIVE_STARS, "")
                    is Screen.Artist -> ArtistScreen(app, entry, screen.key)
                    is Screen.Album -> CollectionScreen(app, entry, CollectionKind.ALBUM, screen.key)
                    is Screen.Playlist -> CollectionScreen(app, entry, CollectionKind.PLAYLIST, screen.key)
                }
            }
        }
    }
}

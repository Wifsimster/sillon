package ovh.battistella.sillon.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import ovh.battistella.sillon.data.settings.ThemeAccent
import ovh.battistella.sillon.desktop.ui.screens.ShelfKind
import java.util.Locale

interface Strings {
    val listen: String
    val artists: String
    val albums: String
    val playlists: String
    val tracks: String
    val search: String
    val downloads: String
    val settings: String

    val chooseTitle: String
    val chooseBody: String
    val plexTitle: String
    val plexBody: String
    val plexOpenBrowser: String
    val plexOrLink: String
    val plexWaiting: String
    val plexExpired: String
    val tryAgain: String
    val jellyfinTitle: String
    val jellyfinBody: String
    val jellyfinAddress: String
    val jellyfinAddressHint: String
    val continueAction: String
    fun jellyfinServerFound(name: String): String
    val jellyfinUsername: String
    val jellyfinPassword: String
    val signIn: String
    val jellyfinQuickConnect: String
    val jellyfinQuickConnectBody: String
    val jellyfinErrorServer: String
    val jellyfinErrorCredentials: String
    val jellyfinErrorQuickConnect: String
    val jellyfinErrorOther: String
    val jellyfinOtherServer: String
    val codeCopy: String
    val codeCopied: String

    val serversTitle: String
    val serversEmpty: String
    fun serversConnecting(name: String): String
    fun serversUnreachable(name: String): String
    val serversOwned: String
    val serversShared: String

    val back: String
    val retry: String
    val play: String
    val pause: String
    val shuffle: String
    val next: String
    val previous: String
    val repeatOff: String
    val repeatAll: String
    val repeatOne: String
    val volume: String
    val more: String
    fun errorGeneric(message: String): String
    val libraryNoMusic: String
    val libraryEmpty: String
    val playlistsEmpty: String
    fun trackCount(count: Int): String
    fun albumCount(count: Int): String

    val searchHint: String
    val searchClear: String
    fun searchNoResults(query: String): String
    val searchRecent: String
    val searchStart: String
    fun shelfTitle(kind: ShelfKind, label: String?): String

    val activity: String
    val activityEmpty: String
    val activityOffline: String
    val activityNotAllowed: String
    fun activityYou(name: String): String
    val activityPaused: String
    val activityTranscoding: String
    val activityUnknownUser: String
    val downloadsEmpty: String
    val download: String
    val downloadRemove: String
    val downloadDone: String
    fun downloadProgress(done: Int, total: Int): String
    fun downloadFailed(count: Int): String
    val downloadsRetryFailed: String
    fun downloadsSummary(tracks: Int, size: String): String
    val downloadsOpenFolder: String

    val fiveStars: String
    val fiveStarsHint: String
    val favourites: String
    val favouritesHint: String
    val fiveStarsList: String
    val listenShuffle: String
    val listenShuffleHint: String
    val neverPlayed: String
    val neverPlayedHint: String
    val radios: String
    val radioLibrary: String
    val artistRadio: String
    val artistHide: String
    val artistUnhide: String
    val artistHiddenNotice: String
    fun playerHideArtist(name: String): String
    val trackRadio: String
    val listenOffline: String
    val listenFromDownloads: String
    val listenFiveStarsFromDownloads: String
    val listenNoFiveStars: String
    val listenNoFavourites: String
    val listenFailed: String
    val listenNothing: String

    val queue: String
    val queueNowPlaying: String
    val queueUpNext: String
    val queueNothingNext: String
    val queueRadioContinues: String
    val queueRemove: String
    val queueMoveUp: String
    val queueMoveDown: String
    val queuePlayNext: String
    val queueAdd: String
    val queueClear: String
    val playerOpen: String
    val playerClose: String
    val playerNothing: String
    val lyrics: String
    val lyricsNone: String
    fun audioUnavailable(reason: String): String

    fun rate(stars: Int): String
    val ratingClear: String
    val ratingWaiting: String
    val ratingFailed: String
    val ratingJellyfinOnly: String
    val trackSkipped: String
    val nowPlaying: String
    fun stars(count: Int): String
    fun popularity(percent: Int): String
    val columnTitle: String
    val columnPopularity: String
    val columnRating: String
    val columnDuration: String
    fun sortBy(column: String): String
    val sortReverse: String
    val sortReset: String
    val columnPlays: String
    fun plays(count: Int): String

    val settingsPlayback: String
    val settingsQuality: String
    val settingsQualityOriginal: String
    val settingsQualityOriginalHint: String
    fun settingsQualityKbps(kbps: Int): String
    val settingsLoudness: String
    val settingsLoudnessHint: String
    val settingsLoudnessOff: String
    val settingsLoudnessTrack: String
    val settingsLoudnessAlbum: String
    val settingsSkipOneStar: String
    val settingsSkipOneStarHint: String
    val settingsHiddenArtists: String
    val settingsHiddenArtistsHint: String
    val settingsHiddenArtistsNone: String
    val settingsHiddenArtistRestore: String
    val settingsAppearance: String
    val settingsSleeveWear: String
    val settingsSleeveWearHint: String
    val settingsThemeMode: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsThemeAccent: String
    val settingsThemePureBlack: String
    val settingsThemePureBlackHint: String
    fun accentName(accent: ThemeAccent): String
    val genres: String
    val genresEmpty: String
    val genreShuffle: String
    val genreNothing: String
    val settingsLibrary: String
    val settingsDiscord: String
    val settingsDiscordPresence: String
    val settingsDiscordPresenceHint: String
    val settingsDiscordCovers: String
    val settingsDiscordCoversHint: String
    val discordLive: String
    val discordWaiting: String
    val discordPlaying: String
    val discordPaused: String
    val settingsUpdates: String
    val settingsAutoUpdate: String
    val settingsAutoUpdateHint: String
    val updateCheckNow: String
    val updateChecking: String
    val updateUpToDate: String
    val updateCheckFailed: String
    fun updateReady(version: String): String
    fun updateNotInstalled(version: String): String
    fun updateAvailable(version: String): String
    fun updateDownloading(version: String, percent: Int): String
    fun updateFailed(version: String): String
    val updateInstall: String
    val updateLater: String
    val updateSkip: String
    val updateWhatsNew: String
    val updateOpenPage: String
    fun updateNotesTitle(version: String): String
    fun updateInstalledTitle(version: String): String
    val updateNoNotes: String
    val updateInstallFailed: String
    val close: String
    val windowMinimize: String
    val windowMaximize: String
    val windowRestore: String
    val lyricsShow: String
    val lyricsHide: String
    fun changelogTitle(title: String): String
    val settingsAccount: String
    fun settingsServer(name: String): String
    val changeServer: String
    val signOut: String
    val settingsAbout: String
    fun settingsVersion(version: String): String
}

object EnglishStrings : Strings {
    override val listen = "Listen"
    override val artists = "Artists"
    override val albums = "Albums"
    override val playlists = "Playlists"
    override val tracks = "Tracks"
    override val search = "Search"
    override val downloads = "Downloads"
    override val settings = "Settings"

    override val chooseTitle = "Connect your music"
    override val chooseBody = "Sillon plays the music library of your own media server. Which one do you use?"
    override val plexTitle = "Sign in to Plex"
    override val plexBody = "Sillon plays the music library of your own Plex Media Server. Sign in with your Plex account to find it."
    override val plexOpenBrowser = "Sign in with Plex"
    override val plexOrLink = "Or go to plex.tv/link on another device and enter this code:"
    override val plexWaiting = "Waiting for confirmation…"
    override val plexExpired = "The code expired. Try again."
    override val tryAgain = "Try again"
    override val jellyfinTitle = "Sign in to Jellyfin"
    override val jellyfinBody = "Enter the address of your Jellyfin server."
    override val jellyfinAddress = "Server address"
    override val jellyfinAddressHint = "192.168.1.10:8096 or music.example.com"
    override val continueAction = "Continue"
    override fun jellyfinServerFound(name: String) = "Server found: $name"
    override val jellyfinUsername = "User name"
    override val jellyfinPassword = "Password"
    override val signIn = "Sign in"
    override val jellyfinQuickConnect = "Use Quick Connect"
    override val jellyfinQuickConnectBody = "In another Jellyfin app signed in to this server, open Quick Connect and enter this code:"
    override val jellyfinErrorServer = "No Jellyfin server answers at this address."
    override val jellyfinErrorCredentials = "Wrong user name or password."
    override val jellyfinErrorQuickConnect = "Quick Connect is turned off on this server."
    override val jellyfinErrorOther = "Sign-in failed. Check the server and try again."
    override val jellyfinOtherServer = "Another server"
    override val codeCopy = "Copy the code"
    override val codeCopied = "Code copied"

    override val serversTitle = "Choose a server"
    override val serversEmpty = "No Plex Media Server is linked to this account."
    override fun serversConnecting(name: String) = "Connecting to $name…"
    override fun serversUnreachable(name: String) = "$name can't be reached right now. Is it online?"
    override val serversOwned = "Your server"
    override val serversShared = "Shared with you"

    override val back = "Back"
    override val retry = "Retry"
    override val play = "Play"
    override val pause = "Pause"
    override val shuffle = "Shuffle"
    override val next = "Next track"
    override val previous = "Previous track"
    override val repeatOff = "Repeat: off"
    override val repeatAll = "Repeat: the queue"
    override val repeatOne = "Repeat: this track"
    override val volume = "Volume"
    override val more = "More options"
    override fun errorGeneric(message: String) = "Something went wrong: $message"
    override val libraryNoMusic = "This server has no music library."
    override val libraryEmpty = "Nothing here yet."
    override val playlistsEmpty = "No music playlists on this server yet. The radios are on Listen."
    override fun trackCount(count: Int) = if (count == 1) "1 track" else "$count tracks"
    override fun albumCount(count: Int) = if (count == 1) "1 album" else "$count albums"

    override val searchHint = "Artists, albums, tracks"
    override val searchClear = "Clear"
    override fun searchNoResults(query: String) = "Nothing found for “$query”."
    override val searchRecent = "Recently played"
    override val searchStart = "Search your library as you type."
    override fun shelfTitle(kind: ShelfKind, label: String?) = when (kind) {
        ShelfKind.HALLOWEEN -> "Halloween chills"
        ShelfKind.CHRISTMAS -> "Christmas is coming"
        ShelfKind.LOVE -> "For Valentine's Day"
        ShelfKind.SUMMER -> "Sounds of summer"
        ShelfKind.SOUNDTRACKS -> "Soundtracks"
        ShelfKind.LIVE -> "On stage"
        ShelfKind.CHILL -> "Taking it easy"
        ShelfKind.PARTY -> "For a party"
        ShelfKind.FOCUS -> "To concentrate"
        ShelfKind.DECADE -> "The ${label.orEmpty()}s"
        ShelfKind.GENRE -> "Today: ${label.orEmpty()}"
        ShelfKind.ARTIST -> "All of ${label.orEmpty()}"
        ShelfKind.REDISCOVER -> "To rediscover"
    }

    override val activity = "Activity"
    override val activityEmpty = "Nobody is listening right now."
    override val activityOffline = "Your server can't be reached right now."
    override val activityNotAllowed = "A server shows what the others play to its owner only (Plex), or to an administrator (Jellyfin)."
    override fun activityYou(name: String) = "$name (you)"
    override val activityPaused = "Paused"
    override val activityTranscoding = "Converted by the server"
    override val activityUnknownUser = "Someone"
    override val downloadsEmpty = "Nothing downloaded yet. Open an album or a playlist and choose Download to listen without a connection."
    override val download = "Download"
    override val downloadRemove = "Remove download"
    override val downloadDone = "Downloaded"
    override fun downloadProgress(done: Int, total: Int) = "Downloading $done of $total"
    override fun downloadFailed(count: Int) = "$count failed"
    override val downloadsRetryFailed = "Retry failed downloads"
    override fun downloadsSummary(tracks: Int, size: String) = "${trackCount(tracks)} · $size"
    override val downloadsOpenFolder = "Open the folder"

    override val fiveStars = "My 5 stars"
    override val fiveStarsHint = "Every track you rated 5 stars"
    override val favourites = "My favourites"
    override val favouritesHint = "Every track you marked as a favourite"
    override val fiveStarsList = "See the list"
    override val listenShuffle = "Shuffle"
    override val listenShuffleHint = "Your whole library, at random"
    override val neverPlayed = "Never played"
    override val neverPlayedHint = "Tracks you have never listened to, at random"
    override val radios = "Radios"
    override val radioLibrary = "Library radio"
    override val artistRadio = "Artist radio"
    override val artistHide = "Keep out of radios"
    override val artistUnhide = "Back in radios"
    override val artistHiddenNotice = "Never played in radios or shuffles."
    override fun playerHideArtist(name: String) = "Keep $name out of radios"
    override val trackRadio = "Radio from this track"
    override val listenOffline = "You are offline. Your downloads still play; what needs your server comes back with the connection."
    override val listenFromDownloads = "Offline: shuffling your downloads."
    override val listenFiveStarsFromDownloads = "Offline: shuffling the downloaded ones."
    override val listenNoFiveStars = "No 5-star tracks yet. Rate a track 5 stars and it lands here."
    override val listenNoFavourites = "No favourites yet. Mark a track as a favourite and it lands here."
    override val listenFailed = "That did not start. Try again."
    override val listenNothing = "Nothing to play here right now."

    override val queue = "Queue"
    override val queueNowPlaying = "Now playing"
    override val queueUpNext = "Up next"
    override val queueNothingNext = "Nothing after this track."
    override val queueRadioContinues = "The radio continues…"
    override val queueRemove = "Remove"
    override val queueMoveUp = "Move up"
    override val queueMoveDown = "Move down"
    override val queuePlayNext = "Play next"
    override val queueAdd = "Add to queue"
    override val queueClear = "Clear the queue"
    override val playerOpen = "Open the player"
    override val playerClose = "Close the player"
    override val playerNothing = "Nothing is playing. Pick an album, or start on Listen."
    override val lyrics = "Lyrics"
    override val lyricsNone = "No lyrics for this track on your server."
    override fun audioUnavailable(reason: String) = "Sillon can't play audio: its audio engine (libmpv) did not start. $reason"

    override fun rate(stars: Int) = if (stars == 1) "Rate 1 star" else "Rate $stars stars"
    override val ratingClear = "Clear the rating"
    override val ratingWaiting = "Offline: your rating leaves once you are back online."
    override val ratingFailed = "Your rating did not reach the server. Click a star to try again."
    override val ratingJellyfinOnly = "Jellyfin keeps 1 star (dislike) and 5 stars (favourite) only."
    override val trackSkipped = "Skipped when played in order"
    override val nowPlaying = "Now playing"
    override fun stars(count: Int) = if (count == 1) "1 star" else "$count stars"
    override fun popularity(percent: Int) = "Popularity: $percent%"
    override val columnTitle = "Title"
    override val columnPopularity = "Popularity"
    override val columnRating = "Rating"
    override val columnDuration = "Duration"
    override fun sortBy(column: String) = "Sort by ${column.lowercase()}"
    override val sortReverse = "Reverse the order"
    override val sortReset = "Back to the original order"
    override val columnPlays = "Plays"
    override fun plays(count: Int) = when (count) {
        0 -> "Never played"
        1 -> "Played once"
        else -> "Played $count times"
    }

    override val settingsPlayback = "Playback"
    override val settingsQuality = "Streaming quality"
    override val settingsQualityOriginal = "Original"
    override val settingsQualityOriginalHint = "The file as it is on the server (FLAC, MP3…)"
    override fun settingsQualityKbps(kbps: Int) = "$kbps kbps (AAC)"
    override val settingsLoudness = "Volume levelling"
    override val settingsLoudnessHint = "Evens out loudness between tracks with the gains your server measured (ReplayGain)."
    override val settingsLoudnessOff = "Off"
    override val settingsLoudnessTrack = "Per track: every track at the same loudness"
    override val settingsLoudnessAlbum = "Per album: keeps each album's own dynamics"
    override val settingsSkipOneStar = "Skip 1-star tracks"
    override val settingsSkipOneStarHint = "In albums and playlists. Double-click one to hear it anyway. Shuffle and radios never play them."
    override val settingsHiddenArtists = "Artists kept out of radios"
    override val settingsHiddenArtistsHint = "Their tracks never come up in radios or shuffles. Albums and playlists still play them."
    override val settingsHiddenArtistsNone = "None. Keep an artist out from their page."
    override val settingsHiddenArtistRestore = "Put back"
    override val settingsAppearance = "Appearance"
    override val settingsSleeveWear = "Sleeve wear"
    override val settingsSleeveWearHint = "Covers age as you play them, like a real record"
    override val settingsThemeMode = "Light or dark"
    override val settingsThemeSystem = "Follow the system"
    override val settingsThemeLight = "Light"
    override val settingsThemeDark = "Dark"
    override val settingsThemeAccent = "Accent colour"
    override val settingsThemePureBlack = "Pure black"
    override val settingsThemePureBlackHint = "In dark, true black instead of vinyl black"
    override fun accentName(accent: ThemeAccent) = when (accent) {
        ThemeAccent.AMBER -> "Amber"
        ThemeAccent.RUST -> "Rust"
        ThemeAccent.OLIVE -> "Olive"
        ThemeAccent.TEAL -> "Teal"
        ThemeAccent.INDIGO -> "Indigo"
        ThemeAccent.PLUM -> "Plum"
    }
    override val genres = "Genres"
    override val genresEmpty = "None of your albums has a genre on this server."
    override val genreShuffle = "Shuffle the genre"
    override val genreNothing = "Nothing to play in this genre right now."
    override val settingsLibrary = "Music library"
    override val settingsDiscord = "Discord"
    override val settingsDiscordPresence = "Show what I play on Discord"
    override val settingsDiscordPresenceHint = "Your Discord profile shows the track, the artist and a progress bar while Sillon plays. Discord must be open on this computer."
    override val settingsDiscordCovers = "Album covers on Discord"
    override val settingsDiscordCoversHint = "Sillon looks the album up on Apple's iTunes (the artist and album names only) and shows that public cover. Your server's covers never leave it."
    override val discordLive = "Connected to Discord"
    override val discordWaiting = "Waiting for Discord to open"
    override val discordPlaying = "Playing"
    override val discordPaused = "Paused"
    override val settingsUpdates = "Updates"
    override val settingsAutoUpdate = "Update automatically"
    override val settingsAutoUpdateHint = "Sillon asks GitHub now and then whether a new version is out, downloads it, and installs it the next time it starts."
    override val updateCheckNow = "Check now"
    override val updateChecking = "Checking…"
    override val updateUpToDate = "Sillon is up to date."
    override val updateCheckFailed = "Couldn't check for updates. Try again later."
    override fun updateReady(version: String) = "Sillon $version will be installed the next time Sillon starts."
    override fun updateNotInstalled(version: String) = "Sillon $version could not be installed on its own."
    override fun updateAvailable(version: String) = "Sillon $version is out."
    override fun updateDownloading(version: String, percent: Int) = "Downloading Sillon $version… $percent%"
    override fun updateFailed(version: String) = "Sillon $version couldn't be downloaded."
    override val updateInstall = "Restart now"
    override val updateLater = "Later"
    override val updateSkip = "Skip this version"
    override val updateWhatsNew = "What's new"
    override val updateOpenPage = "Download page"
    override fun updateNotesTitle(version: String) = "What's new in Sillon $version"
    override fun updateInstalledTitle(version: String) = "Sillon $version is installed"
    override val updateNoNotes = "No notes for this version."
    override val updateInstallFailed = "The installer couldn't be started. Use the download page instead."
    override val close = "Close"
    override val windowMinimize = "Minimize"
    override val windowMaximize = "Maximize"
    override val windowRestore = "Restore"
    override val lyricsShow = "Show the lyrics"
    override val lyricsHide = "Hide the lyrics"
    override fun changelogTitle(title: String) = title
    override val settingsAccount = "Server"
    override fun settingsServer(name: String) = "Connected to $name"
    override val changeServer = "Change server"
    override val signOut = "Sign out"
    override val settingsAbout = "About"
    override fun settingsVersion(version: String) = "Sillon $version for desktop"
}

object FrenchStrings : Strings {
    override val listen = "Écouter"
    override val artists = "Artistes"
    override val albums = "Albums"
    override val playlists = "Playlists"
    override val tracks = "Titres"
    override val search = "Recherche"
    override val downloads = "Téléchargements"
    override val settings = "Réglages"

    override val chooseTitle = "Connecte ta musique"
    override val chooseBody = "Sillon lit la bibliothèque musicale de ton propre serveur multimédia. Lequel utilises-tu ?"
    override val plexTitle = "Connexion à Plex"
    override val plexBody = "Sillon lit la bibliothèque musicale de ton propre Plex Media Server. Connecte-toi avec ton compte Plex pour le retrouver."
    override val plexOpenBrowser = "Se connecter avec Plex"
    override val plexOrLink = "Ou va sur plex.tv/link depuis un autre appareil et saisis ce code :"
    override val plexWaiting = "En attente de confirmation…"
    override val plexExpired = "Le code a expiré. Réessaie."
    override val tryAgain = "Réessayer"
    override val jellyfinTitle = "Connexion à Jellyfin"
    override val jellyfinBody = "Saisis l'adresse de ton serveur Jellyfin."
    override val jellyfinAddress = "Adresse du serveur"
    override val jellyfinAddressHint = "192.168.1.10:8096 ou musique.exemple.fr"
    override val continueAction = "Continuer"
    override fun jellyfinServerFound(name: String) = "Serveur trouvé : $name"
    override val jellyfinUsername = "Nom d'utilisateur"
    override val jellyfinPassword = "Mot de passe"
    override val signIn = "Se connecter"
    override val jellyfinQuickConnect = "Utiliser Quick Connect"
    override val jellyfinQuickConnectBody = "Dans une autre application Jellyfin connectée à ce serveur, ouvre Quick Connect et saisis ce code :"
    override val jellyfinErrorServer = "Aucun serveur Jellyfin ne répond à cette adresse."
    override val jellyfinErrorCredentials = "Nom d'utilisateur ou mot de passe incorrect."
    override val jellyfinErrorQuickConnect = "Quick Connect est désactivé sur ce serveur."
    override val jellyfinErrorOther = "La connexion a échoué. Vérifie le serveur et réessaie."
    override val jellyfinOtherServer = "Un autre serveur"
    override val codeCopy = "Copier le code"
    override val codeCopied = "Code copié"

    override val serversTitle = "Choisis un serveur"
    override val serversEmpty = "Aucun Plex Media Server n'est lié à ce compte."
    override fun serversConnecting(name: String) = "Connexion à $name…"
    override fun serversUnreachable(name: String) = "$name est injoignable pour l'instant. Est-il en ligne ?"
    override val serversOwned = "Ton serveur"
    override val serversShared = "Partagé avec toi"

    override val back = "Retour"
    override val retry = "Réessayer"
    override val play = "Lire"
    override val pause = "Pause"
    override val shuffle = "Aléatoire"
    override val next = "Titre suivant"
    override val previous = "Titre précédent"
    override val repeatOff = "Répétition : désactivée"
    override val repeatAll = "Répétition : la file"
    override val repeatOne = "Répétition : ce titre"
    override val volume = "Volume"
    override val more = "Plus d'options"
    override fun errorGeneric(message: String) = "Quelque chose s'est mal passé : $message"
    override val libraryNoMusic = "Ce serveur n'a pas de bibliothèque musicale."
    override val libraryEmpty = "Rien ici pour l'instant."
    override val playlistsEmpty = "Pas encore de playlist musicale sur ce serveur. Les radios sont dans Écouter."
    override fun trackCount(count: Int) = if (count <= 1) "$count titre" else "$count titres"
    override fun albumCount(count: Int) = if (count <= 1) "$count album" else "$count albums"

    override val searchHint = "Artistes, albums, titres"
    override val searchClear = "Effacer"
    override fun searchNoResults(query: String) = "Rien trouvé pour « $query »."
    override val searchRecent = "Écoutés récemment"
    override val searchStart = "Cherche dans ta bibliothèque au fil de la frappe."
    override fun shelfTitle(kind: ShelfKind, label: String?) = when (kind) {
        ShelfKind.HALLOWEEN -> "Frissons d'Halloween"
        ShelfKind.CHRISTMAS -> "Noël approche"
        ShelfKind.LOVE -> "Pour la Saint-Valentin"
        ShelfKind.SUMMER -> "Sons d'été"
        ShelfKind.SOUNDTRACKS -> "Bandes originales"
        ShelfKind.LIVE -> "En concert"
        ShelfKind.CHILL -> "Au calme"
        ShelfKind.PARTY -> "Pour faire la fête"
        ShelfKind.FOCUS -> "Pour se concentrer"
        ShelfKind.DECADE -> "Les années ${label.orEmpty()}"
        ShelfKind.GENRE -> "Aujourd'hui : ${label.orEmpty()}"
        ShelfKind.ARTIST -> "Tout ${label.orEmpty()}"
        ShelfKind.REDISCOVER -> "À redécouvrir"
    }

    override val activity = "Activité"
    override val activityEmpty = "Personne n'écoute pour l'instant."
    override val activityOffline = "Ton serveur est injoignable pour le moment."
    override val activityNotAllowed = "Un serveur ne montre ce que jouent les autres qu'à son propriétaire (Plex) ou à un administrateur (Jellyfin)."
    override fun activityYou(name: String) = "$name (toi)"
    override val activityPaused = "En pause"
    override val activityTranscoding = "Converti par le serveur"
    override val activityUnknownUser = "Quelqu'un"
    override val downloadsEmpty = "Rien de téléchargé pour l'instant. Ouvre un album ou une playlist et choisis Télécharger pour écouter sans connexion."
    override val download = "Télécharger"
    override val downloadRemove = "Supprimer le téléchargement"
    override val downloadDone = "Téléchargé"
    override fun downloadProgress(done: Int, total: Int) = "Téléchargement $done sur $total"
    override fun downloadFailed(count: Int) = if (count <= 1) "$count échec" else "$count échecs"
    override val downloadsRetryFailed = "Relancer les téléchargements échoués"
    override fun downloadsSummary(tracks: Int, size: String) = "${trackCount(tracks)} · $size"
    override val downloadsOpenFolder = "Ouvrir le dossier"

    override val fiveStars = "Mes 5 étoiles"
    override val fiveStarsHint = "Tous les titres que tu as notés 5 étoiles"
    override val favourites = "Mes favoris"
    override val favouritesHint = "Tous les titres que tu as mis en favori"
    override val fiveStarsList = "Voir la liste"
    override val listenShuffle = "Aléatoire"
    override val listenShuffleHint = "Toute ta bibliothèque, au hasard"
    override val neverPlayed = "Jamais écoutés"
    override val neverPlayedHint = "Des titres que tu n'as jamais écoutés, au hasard"
    override val radios = "Radios"
    override val radioLibrary = "Radio de la bibliothèque"
    override val artistRadio = "Radio de l'artiste"
    override val artistHide = "Écarter des radios"
    override val artistUnhide = "Remettre dans les radios"
    override val artistHiddenNotice = "Jamais joué dans les radios ni l'aléatoire."
    override fun playerHideArtist(name: String) = "Écarter $name des radios"
    override val trackRadio = "Radio à partir de ce titre"
    override val listenOffline = "Tu es hors ligne. Tes téléchargements restent lisibles ; ce qui dépend de ton serveur revient avec la connexion."
    override val listenFromDownloads = "Hors ligne : lecture aléatoire de tes téléchargements."
    override val listenFiveStarsFromDownloads = "Hors ligne : lecture aléatoire de ceux qui sont téléchargés."
    override val listenNoFiveStars = "Pas encore de titre 5 étoiles. Note un titre 5 étoiles et il arrive ici."
    override val listenNoFavourites = "Pas encore de favori. Mets un titre en favori et il arrive ici."
    override val listenFailed = "Ça n'a pas démarré. Réessaie."
    override val listenNothing = "Rien à lire ici pour l'instant."

    override val queue = "File d'attente"
    override val queueNowPlaying = "En cours"
    override val queueUpNext = "À suivre"
    override val queueNothingNext = "Rien après ce titre."
    override val queueRadioContinues = "La radio continue…"
    override val queueRemove = "Retirer"
    override val queueMoveUp = "Monter"
    override val queueMoveDown = "Descendre"
    override val queuePlayNext = "Lire ensuite"
    override val queueAdd = "Ajouter à la file"
    override val queueClear = "Vider la file"
    override val playerOpen = "Ouvrir le lecteur"
    override val playerClose = "Fermer le lecteur"
    override val playerNothing = "Rien en lecture. Choisis un album, ou lance-toi depuis Écouter."
    override val lyrics = "Paroles"
    override val lyricsNone = "Pas de paroles pour ce titre sur ton serveur."
    override fun audioUnavailable(reason: String) = "Sillon ne peut pas lire l'audio : son moteur audio (libmpv) n'a pas démarré. $reason"

    override fun rate(stars: Int) = if (stars == 1) "Noter 1 étoile" else "Noter $stars étoiles"
    override val ratingClear = "Effacer la note"
    override val ratingWaiting = "Hors ligne : ta note partira dès que tu seras de nouveau en ligne."
    override val ratingFailed = "Ta note n'est pas arrivée au serveur. Clique sur une étoile pour réessayer."
    override val ratingJellyfinOnly = "Jellyfin ne garde que 1 étoile (je n'aime pas) et 5 étoiles (favori)."
    override val trackSkipped = "Passé lors d'une lecture dans l'ordre"
    override val nowPlaying = "En cours de lecture"
    override fun stars(count: Int) = if (count <= 1) "$count étoile" else "$count étoiles"
    override fun popularity(percent: Int) = "Popularité : $percent %"
    override val columnTitle = "Titre"
    override val columnPopularity = "Popularité"
    override val columnRating = "Note"
    override val columnDuration = "Durée"
    override fun sortBy(column: String) = "Trier par ${column.lowercase()}"
    override val sortReverse = "Inverser l'ordre"
    override val sortReset = "Revenir à l'ordre d'origine"
    override val columnPlays = "Écoutes"
    override fun plays(count: Int) = when (count) {
        0 -> "Jamais écouté"
        1 -> "Écouté une fois"
        else -> "Écouté $count fois"
    }

    override val settingsPlayback = "Lecture"
    override val settingsQuality = "Qualité de streaming"
    override val settingsQualityOriginal = "Originale"
    override val settingsQualityOriginalHint = "Le fichier tel qu'il est sur le serveur (FLAC, MP3…)"
    override fun settingsQualityKbps(kbps: Int) = "$kbps kbit/s (AAC)"
    override val settingsLoudness = "Nivellement du volume"
    override val settingsLoudnessHint = "Égalise le volume entre les titres avec les gains mesurés par ton serveur (ReplayGain)."
    override val settingsLoudnessOff = "Désactivé"
    override val settingsLoudnessTrack = "Par titre : tous les titres au même volume"
    override val settingsLoudnessAlbum = "Par album : garde la dynamique propre à chaque album"
    override val settingsSkipOneStar = "Passer les titres 1 étoile"
    override val settingsSkipOneStarHint = "Dans les albums et les playlists. Double-clique sur l'un d'eux pour l'écouter quand même. L'aléatoire et les radios ne les jouent jamais."
    override val settingsHiddenArtists = "Artistes écartés des radios"
    override val settingsHiddenArtistsHint = "Leurs titres ne sortent jamais dans les radios ni l'aléatoire. Albums et playlists les jouent toujours."
    override val settingsHiddenArtistsNone = "Aucun. Écarte un artiste depuis sa page."
    override val settingsHiddenArtistRestore = "Remettre"
    override val settingsAppearance = "Apparence"
    override val settingsSleeveWear = "Usure des pochettes"
    override val settingsSleeveWearHint = "Les pochettes vieillissent à force d'être écoutées, comme un vrai disque"
    override val settingsThemeMode = "Clair ou sombre"
    override val settingsThemeSystem = "Comme le système"
    override val settingsThemeLight = "Clair"
    override val settingsThemeDark = "Sombre"
    override val settingsThemeAccent = "Couleur d'accent"
    override val settingsThemePureBlack = "Noir pur"
    override val settingsThemePureBlackHint = "En sombre, du vrai noir plutôt que le noir vinyle"
    override fun accentName(accent: ThemeAccent) = when (accent) {
        ThemeAccent.AMBER -> "Ambre"
        ThemeAccent.RUST -> "Rouille"
        ThemeAccent.OLIVE -> "Olive"
        ThemeAccent.TEAL -> "Canard"
        ThemeAccent.INDIGO -> "Indigo"
        ThemeAccent.PLUM -> "Prune"
    }
    override val genres = "Genres"
    override val genresEmpty = "Aucun de tes albums n'a de genre sur ce serveur."
    override val genreShuffle = "Lecture aléatoire du genre"
    override val genreNothing = "Rien à lire dans ce genre pour l'instant."
    override val settingsLibrary = "Bibliothèque musicale"
    override val settingsDiscord = "Discord"
    override val settingsDiscordPresence = "Afficher ce que j'écoute sur Discord"
    override val settingsDiscordPresenceHint = "Votre profil Discord montre le morceau, l'artiste et une barre de progression pendant que Sillon joue. Discord doit être ouvert sur cet ordinateur."
    override val settingsDiscordCovers = "Pochettes sur Discord"
    override val settingsDiscordCoversHint = "Sillon cherche l'album sur iTunes d'Apple (seulement les noms de l'artiste et de l'album) et affiche cette pochette publique. Les pochettes de votre serveur n'en sortent jamais."
    override val discordLive = "Connecté à Discord"
    override val discordWaiting = "En attente de l'ouverture de Discord"
    override val discordPlaying = "En lecture"
    override val discordPaused = "En pause"
    override val settingsUpdates = "Mises à jour"
    override val settingsAutoUpdate = "Mettre à jour automatiquement"
    override val settingsAutoUpdateHint = "Sillon demande de temps en temps à GitHub si une nouvelle version est sortie, la télécharge, et l'installe à son prochain démarrage."
    override val updateCheckNow = "Vérifier maintenant"
    override val updateChecking = "Vérification…"
    override val updateUpToDate = "Sillon est à jour."
    override val updateCheckFailed = "Impossible de vérifier les mises à jour. Réessaie plus tard."
    override fun updateReady(version: String) = "Sillon $version sera installée au prochain démarrage de Sillon."
    override fun updateNotInstalled(version: String) = "Sillon $version n'a pas pu s'installer toute seule."
    override fun updateAvailable(version: String) = "Sillon $version est sortie."
    override fun updateDownloading(version: String, percent: Int) = "Téléchargement de Sillon $version… $percent %"
    override fun updateFailed(version: String) = "Sillon $version n'a pas pu être téléchargée."
    override val updateInstall = "Redémarrer maintenant"
    override val updateLater = "Plus tard"
    override val updateSkip = "Ignorer cette version"
    override val updateWhatsNew = "Nouveautés"
    override val updateOpenPage = "Page de téléchargement"
    override fun updateNotesTitle(version: String) = "Nouveautés de Sillon $version"
    override fun updateInstalledTitle(version: String) = "Sillon $version est installée"
    override val updateNoNotes = "Pas de notes pour cette version."
    override val updateInstallFailed = "L'installateur n'a pas pu être lancé. Passe par la page de téléchargement."
    override val close = "Fermer"
    override val windowMinimize = "Réduire"
    override val windowMaximize = "Agrandir"
    override val windowRestore = "Restaurer"
    override val lyricsShow = "Afficher les paroles"
    override val lyricsHide = "Masquer les paroles"
    override fun changelogTitle(title: String) = when (title.lowercase()) {
        "features" -> "Nouveautés"
        "bug fixes" -> "Corrections"
        "performance improvements" -> "Performances"
        "reverts" -> "Retours en arrière"
        else -> title
    }
    override val settingsAccount = "Serveur"
    override fun settingsServer(name: String) = "Connecté à $name"
    override val changeServer = "Changer de serveur"
    override val signOut = "Se déconnecter"
    override val settingsAbout = "À propos"
    override fun settingsVersion(version: String) = "Sillon $version pour ordinateur"
}

fun stringsFor(locale: Locale = Locale.getDefault()): Strings =
    if (locale.language == "fr") FrenchStrings else EnglishStrings

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

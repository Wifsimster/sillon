package ovh.battistella.sillon.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf
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
    val settingsAppearance: String
    val settingsSleeveWear: String
    val settingsSleeveWearHint: String
    val genres: String
    val genresEmpty: String
    val genreShuffle: String
    val genreNothing: String
    val settingsLibrary: String
    val settingsUpdates: String
    val settingsAutoUpdate: String
    val settingsAutoUpdateHint: String
    val updateCheckNow: String
    val updateChecking: String
    val updateUpToDate: String
    val updateCheckFailed: String
    fun updateReady(version: String): String
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
    override val settingsAppearance = "Appearance"
    override val settingsSleeveWear = "Sleeve wear"
    override val settingsSleeveWearHint = "Covers age as you play them, like a real record"
    override val genres = "Genres"
    override val genresEmpty = "None of your albums has a genre on this server."
    override val genreShuffle = "Shuffle the genre"
    override val genreNothing = "Nothing to play in this genre right now."
    override val settingsLibrary = "Music library"
    override val settingsUpdates = "Updates"
    override val settingsAutoUpdate = "Update automatically"
    override val settingsAutoUpdateHint = "Sillon asks GitHub now and then whether a new version is out, downloads it, and tells you when it's ready to install."
    override val updateCheckNow = "Check now"
    override val updateChecking = "Checking…"
    override val updateUpToDate = "Sillon is up to date."
    override val updateCheckFailed = "Couldn't check for updates. Try again later."
    override fun updateReady(version: String) = "Sillon $version is ready to install."
    override fun updateAvailable(version: String) = "Sillon $version is out."
    override fun updateDownloading(version: String, percent: Int) = "Downloading Sillon $version… $percent%"
    override fun updateFailed(version: String) = "Sillon $version couldn't be downloaded."
    override val updateInstall = "Install and restart"
    override val updateLater = "Later"
    override val updateSkip = "Skip this version"
    override val updateWhatsNew = "What's new"
    override val updateOpenPage = "Download page"
    override fun updateNotesTitle(version: String) = "What's new in Sillon $version"
    override fun updateInstalledTitle(version: String) = "Sillon $version is installed"
    override val updateNoNotes = "No notes for this version."
    override val updateInstallFailed = "The installer couldn't be started. Use the download page instead."
    override val close = "Close"
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
    override val settingsAppearance = "Apparence"
    override val settingsSleeveWear = "Usure des pochettes"
    override val settingsSleeveWearHint = "Les pochettes vieillissent à force d'être écoutées, comme un vrai disque"
    override val genres = "Genres"
    override val genresEmpty = "Aucun de tes albums n'a de genre sur ce serveur."
    override val genreShuffle = "Lecture aléatoire du genre"
    override val genreNothing = "Rien à lire dans ce genre pour l'instant."
    override val settingsLibrary = "Bibliothèque musicale"
    override val settingsUpdates = "Mises à jour"
    override val settingsAutoUpdate = "Mettre à jour automatiquement"
    override val settingsAutoUpdateHint = "Sillon demande de temps en temps à GitHub si une nouvelle version est sortie, la télécharge, et te prévient quand elle est prête à installer."
    override val updateCheckNow = "Vérifier maintenant"
    override val updateChecking = "Vérification…"
    override val updateUpToDate = "Sillon est à jour."
    override val updateCheckFailed = "Impossible de vérifier les mises à jour. Réessaie plus tard."
    override fun updateReady(version: String) = "Sillon $version est prête à installer."
    override fun updateAvailable(version: String) = "Sillon $version est sortie."
    override fun updateDownloading(version: String, percent: Int) = "Téléchargement de Sillon $version… $percent %"
    override fun updateFailed(version: String) = "Sillon $version n'a pas pu être téléchargée."
    override val updateInstall = "Installer et redémarrer"
    override val updateLater = "Plus tard"
    override val updateSkip = "Ignorer cette version"
    override val updateWhatsNew = "Nouveautés"
    override val updateOpenPage = "Page de téléchargement"
    override fun updateNotesTitle(version: String) = "Nouveautés de Sillon $version"
    override fun updateInstalledTitle(version: String) = "Sillon $version est installée"
    override val updateNoNotes = "Pas de notes pour cette version."
    override val updateInstallFailed = "L'installateur n'a pas pu être lancé. Passe par la page de téléchargement."
    override val close = "Fermer"
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

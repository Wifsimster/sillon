package ovh.battistella.sillon.ui.navigation

object Routes {
    const val ARG_KEY = "key"

    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val PLAYLISTS = "playlists"
    const val FIVE_STARS = "five-stars"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val DOWNLOADED_ALBUM = "downloaded/{$ARG_KEY}"
    const val PLAYLIST = "playlist/{$ARG_KEY}"
    const val ARTIST = "artist/{$ARG_KEY}"
    const val ALBUM = "album/{$ARG_KEY}"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val TOGETHER = "together"

    fun artist(key: String) = "artist/${android.net.Uri.encode(key)}"
    fun album(key: String) = "album/${android.net.Uri.encode(key)}"
    fun playlist(key: String) = "playlist/${android.net.Uri.encode(key)}"
    fun downloadedAlbum(key: String) = "downloaded/${android.net.Uri.encode(key)}"
}

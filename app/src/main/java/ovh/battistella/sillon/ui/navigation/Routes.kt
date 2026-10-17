package ovh.battistella.sillon.ui.navigation

object Routes {
    const val ARG_KEY = "key"

    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val PLAYLISTS = "playlists"
    const val SEARCH = "search"
    const val PLAYLIST = "playlist/{$ARG_KEY}"
    const val ARTIST = "artist/{$ARG_KEY}"
    const val ALBUM = "album/{$ARG_KEY}"
    const val PLAYER = "player"

    fun artist(key: String) = "artist/${android.net.Uri.encode(key)}"
    fun album(key: String) = "album/${android.net.Uri.encode(key)}"
    fun playlist(key: String) = "playlist/${android.net.Uri.encode(key)}"
}

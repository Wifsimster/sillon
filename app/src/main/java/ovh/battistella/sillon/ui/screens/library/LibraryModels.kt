package ovh.battistella.sillon.ui.screens.library

/** An artist or album tile: key to open it, what to show, and its cover. */
data class TileUi(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

data class TrackUi(
    val key: String,
    val number: Int?,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
)

/** Cover sizes requested from the server, in pixels. */
internal const val TILE_ART_PX = 400
internal const val HEADER_ART_PX = 900

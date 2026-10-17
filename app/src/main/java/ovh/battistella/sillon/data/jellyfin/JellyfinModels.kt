package ovh.battistella.sillon.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.plex.PlexMedia
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.PlexPart

/*
 * Wire models for the Jellyfin API, and their mapping onto the models the rest
 * of the app already uses (named after Plex, which Sillon supported first).
 */

@Serializable
data class JellyfinPublicInfo(
    @SerialName("ServerName") val serverName: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("Version") val version: String? = null,
)

@Serializable
data class JellyfinAuthResult(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String,
    @SerialName("User") val user: JellyfinUser,
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
)

/** A Quick Connect request: show [code] to the user, poll with [secret]. */
@Serializable
data class JellyfinQuickConnect(
    @SerialName("Secret") val secret: String,
    @SerialName("Code") val code: String = "",
    @SerialName("Authenticated") val authenticated: Boolean = false,
)

@Serializable
data class JellyfinItems(
    @SerialName("Items") val items: List<JellyfinItem> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0,
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @SerialName("UserData") val userData: JellyfinUserData? = null,
    /** dB to bring the track to the server's target loudness (Jellyfin 10.9+). */
    @SerialName("NormalizationGain") val normalizationGain: Float? = null,
)

@Serializable
data class JellyfinUserData(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("PlayCount") val playCount: Int = 0,
)

/** Prefix marking an artwork reference as a Jellyfin item id (see [JellyfinApi.artworkUrl]). */
const val JELLYFIN_IMAGE = "jellyfin-item:"

/** A music library ("view" with the music collection type). */
fun JellyfinItem.toDirectory() = PlexDirectory(
    key = id,
    title = name,
    type = if (collectionType == "music") PlexDirectory.TYPE_MUSIC_SECTION else collectionType.orEmpty(),
)

/**
 * An artist, album, track or playlist in the app's model. Jellyfin has no
 * star ratings: a favourite counts as 5 stars, so "My 5 stars" lists the
 * favourites. Jellyfin has no popularity figure either (no bars).
 */
fun JellyfinItem.toMetadata(): PlexMetadata {
    val ownImage = if (imageTags.containsKey("Primary")) "$JELLYFIN_IMAGE$id" else null
    val albumImage = if (albumPrimaryImageTag != null && albumId != null) "$JELLYFIN_IMAGE$albumId" else null
    val trackArtist = artists.joinToString(", ").takeIf { it.isNotBlank() && it != albumArtist }
    return PlexMetadata(
        ratingKey = id,
        type = when (type) {
            "MusicArtist" -> "artist"
            "MusicAlbum" -> "album"
            "Audio" -> "track"
            "Playlist" -> "playlist"
            else -> type.lowercase()
        },
        title = name,
        parentRatingKey = albumId,
        // For an album, the "parent" is its artist (as in Plex); for a track, its album.
        parentTitle = if (type == "MusicAlbum") albumArtist else album,
        grandparentTitle = albumArtist,
        originalTitle = trackArtist,
        index = indexNumber,
        parentIndex = parentIndexNumber,
        year = productionYear,
        duration = runTimeTicks?.div(TICKS_PER_MS),
        leafCount = childCount,
        thumb = ownImage,
        parentThumb = albumImage,
        userRating = if (userData?.isFavorite == true) 10f else null,
        viewCount = userData?.playCount,
        normalizationGain = normalizationGain,
        media = if (type == "Audio") {
            listOf(PlexMedia(container = container, parts = listOf(PlexPart(key = streamPath(id, container)))))
        } else {
            emptyList()
        },
    )
}

/** Server path of a track's original file; the container in the name gives downloads their extension. */
fun streamPath(id: String, container: String?): String {
    val ext = container?.substringBefore(',')?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) }
    return if (ext != null) "/Audio/$id/stream.$ext?static=true" else "/Audio/$id/stream?static=true"
}

/** Jellyfin durations are in 100 ns ticks. */
const val TICKS_PER_MS = 10_000L

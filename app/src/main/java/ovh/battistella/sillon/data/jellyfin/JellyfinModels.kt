package ovh.battistella.sillon.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.plex.PlexMedia
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.PlexPart
import ovh.battistella.sillon.data.plex.PlexStream
import ovh.battistella.sillon.data.rating.JellyfinStars

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
    @SerialName("NormalizationGain") val normalizationGain: Float? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("AlbumArtists") val albumArtists: List<JellyfinNameId> = emptyList(),
    /** Only when asked for (`fields=MediaSources`). */
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSource> = emptyList(),
)

@Serializable
data class JellyfinNameId(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

@Serializable
data class JellyfinMediaSource(
    @SerialName("Container") val container: String? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("MediaStreams") val streams: List<JellyfinMediaStream> = emptyList(),
)

@Serializable
data class JellyfinMediaStream(
    @SerialName("Type") val type: String = "",
    @SerialName("Codec") val codec: String? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
)

@Serializable
data class JellyfinUserData(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("Likes") val likes: Boolean? = null,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

const val JELLYFIN_IMAGE = "jellyfin-item:"

fun JellyfinItem.toDirectory() = PlexDirectory(
    key = id,
    title = name,
    type = if (collectionType == "music") PlexDirectory.TYPE_MUSIC_SECTION else collectionType.orEmpty(),
)

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
        userRating = userData?.let { JellyfinStars.rating(it.isFavorite, it.likes) },
        viewCount = userData?.playCount,
        lastViewedAt = parseServerDate(userData?.lastPlayedDate)?.div(1_000),
        normalizationGain = normalizationGain,
        media = if (type == "Audio") {
            val audio = mediaSources.firstOrNull()?.streams?.firstOrNull { it.type == "Audio" }
            val streams = audio?.let {
                // Plex units: bitrate in kbps; stream type 2 is audio.
                listOf(PlexStream(streamType = 2, codec = it.codec, samplingRate = it.sampleRate, bitDepth = it.bitDepth, bitrate = it.bitRate?.div(1000)))
            }.orEmpty()
            listOf(
                PlexMedia(
                    audioCodec = audio?.codec,
                    container = container,
                    parts = listOf(PlexPart(key = streamPath(id, container), streams = streams)),
                ),
            )
        } else {
            emptyList()
        },
    )
}

fun streamPath(id: String, container: String?): String {
    val ext = container?.substringBefore(',')?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) }
    return if (ext != null) "/Audio/$id/stream.$ext?static=true" else "/Audio/$id/stream?static=true"
}

const val TICKS_PER_MS = 10_000L

package ovh.battistella.sillon.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
    val authToken: String? = null,
    val expiresIn: Long = 0,
)

@Serializable
data class PlexResource(
    val name: String,
    val clientIdentifier: String,
    val provides: String = "",
    val owned: Boolean = false,
    val accessToken: String? = null,
    val connections: List<PlexConnection> = emptyList(),
) {
    val isServer: Boolean get() = provides.split(',').any { it.trim() == "server" }
}

@Serializable
data class PlexConnection(
    val protocol: String = "",
    val address: String = "",
    val port: Int = 0,
    val uri: String,
    val local: Boolean = false,
    val relay: Boolean = false,
)

@Serializable
data class PlexResponse(
    @SerialName("MediaContainer") val container: MediaContainer = MediaContainer(),
)

@Serializable
data class MediaContainer(
    val size: Int = 0,
    val totalSize: Int? = null,
    val machineIdentifier: String? = null,
    @SerialName("Directory") val directories: List<PlexDirectory> = emptyList(),
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
    @SerialName("Hub") val hubs: List<PlexHub> = emptyList(),
    val playQueueID: Long? = null,
)

@Serializable
data class PlexHub(
    val type: String = "",
    val title: String = "",
    val hubIdentifier: String = "",
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
)

@Serializable
data class PlexDirectory(
    val key: String,
    val title: String,
    val type: String = "",
) {
    val isMusic: Boolean get() = type == TYPE_MUSIC_SECTION

    companion object {
        const val TYPE_MUSIC_SECTION = "artist"
    }
}

@Serializable
data class PlexMetadata(
    val ratingKey: String = "",
    val key: String? = null,
    val type: String = "",
    val title: String = "",
    val parentRatingKey: String? = null,
    val parentTitle: String? = null,
    val grandparentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val originalTitle: String? = null,
    val index: Int? = null,
    val parentIndex: Int? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val leafCount: Int? = null,
    val userRating: Float? = null,
    val ratingCount: Long? = null,
    val viewCount: Int? = null,
    val viewedLeafCount: Int? = null,
    @SerialName("Stations") val stations: PlexStations? = null,
    val normalizationGain: Float? = null,
    val thumb: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    val composite: String? = null,
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
) {
    val artwork: String? get() = thumb ?: composite ?: parentThumb ?: grandparentThumb

    val partKey: String? get() = media.firstOrNull()?.parts?.firstOrNull()?.key

    val stars: Float? get() = userRating?.takeIf { it > 0f }?.div(2f)

    private val audioStream: PlexStream?
        get() = media.firstOrNull()?.parts?.firstOrNull()?.streams?.firstOrNull { it.streamType == 2 }

    val trackGain: Float? get() = normalizationGain ?: audioStream?.gain

    val albumGain: Float? get() = audioStream?.albumGain ?: trackGain

    val trackArtist: String? get() = originalTitle ?: grandparentTitle
}

@Serializable
data class PlexMedia(
    val audioCodec: String? = null,
    val container: String? = null,
    @SerialName("Part") val parts: List<PlexPart> = emptyList(),
)

@Serializable
data class PlexPart(
    val key: String,
    val duration: Long? = null,
    @SerialName("Stream") val streams: List<PlexStream> = emptyList(),
)

@Serializable
data class PlexStream(
    val streamType: Int = 0,
    val gain: Float? = null,
    val albumGain: Float? = null,
    val peak: Float? = null,
    val albumPeak: Float? = null,
)

@Serializable
data class PlexStations(
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
)

package ovh.battistella.sillon.data.activity

import ovh.battistella.sillon.data.jellyfin.JellyfinSession
import ovh.battistella.sillon.data.jellyfin.TICKS_PER_MS
import ovh.battistella.sillon.data.jellyfin.toMetadata
import ovh.battistella.sillon.data.plex.PlexMetadata

enum class ListenState { PLAYING, PAUSED, BUFFERING }

enum class ListenKind { MUSIC, VIDEO, OTHER }

data class ActiveListen(
    val id: String,
    val listenerKey: String,
    val user: String,
    val device: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val state: ListenState,
    val positionMs: Long,
    val durationMs: Long,
    val kind: ListenKind,
    val transcoding: Boolean,
    val mine: Boolean,
) {
    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

sealed interface ActivityState {
    data object Loading : ActivityState
    data object NotAllowed : ActivityState
    data object Unreachable : ActivityState
    data class Live(val listens: List<ActiveListen>) : ActivityState
}

object ServerActivity {
    const val PLEX_OWNER_ID = "1"

    fun fromPlex(sessions: List<PlexMetadata>, clientId: String): List<ActiveListen> =
        sessions.mapNotNull { fromPlex(it, clientId) }.sorted()

    private fun fromPlex(item: PlexMetadata, clientId: String): ActiveListen? {
        val player = item.player
        val userId = item.user?.id?.content.orEmpty()
        val device = player?.machineIdentifier.orEmpty()
        val id = item.session?.id?.takeIf { it.isNotBlank() } ?: item.sessionKey?.takeIf { it.isNotBlank() } ?: return null
        return ActiveListen(
            id = id,
            listenerKey = "$userId@${device.ifBlank { id }}",
            user = item.user?.title.orEmpty(),
            device = deviceLabel(player?.title, player?.product),
            title = title(item),
            subtitle = subtitle(item),
            artwork = artwork(item),
            state = when (player?.state) {
                "paused" -> ListenState.PAUSED
                "buffering" -> ListenState.BUFFERING
                else -> ListenState.PLAYING
            },
            positionMs = item.viewOffset ?: 0,
            durationMs = item.duration ?: 0,
            kind = kind(item.type),
            transcoding = item.transcodeSession != null,
            mine = userId == PLEX_OWNER_ID || (device.isNotBlank() && device == clientId),
        )
    }

    fun fromJellyfin(sessions: List<JellyfinSession>, userId: String?, deviceId: String): List<ActiveListen> =
        sessions.mapNotNull { session ->
            val item = session.nowPlaying?.toMetadata() ?: return@mapNotNull null
            val device = session.deviceId.orEmpty()
            ActiveListen(
                id = session.id,
                listenerKey = "${session.userId.orEmpty()}@${device.ifBlank { session.id }}",
                user = session.userName.orEmpty(),
                device = deviceLabel(session.deviceName, session.client),
                title = title(item),
                subtitle = subtitle(item),
                artwork = artwork(item),
                state = if (session.playState.isPaused) ListenState.PAUSED else ListenState.PLAYING,
                positionMs = (session.playState.positionTicks ?: 0) / TICKS_PER_MS,
                durationMs = item.duration ?: 0,
                kind = kind(item.type),
                transcoding = session.transcoding != null,
                mine = (userId != null && session.userId == userId) || (device.isNotBlank() && device == deviceId),
            )
        }.sorted()

    private fun List<ActiveListen>.sorted(): List<ActiveListen> =
        sortedWith(compareBy<ActiveListen> { it.mine }.thenBy { it.user.lowercase() }.thenBy { it.device.lowercase() }.thenBy { it.id })

    private fun deviceLabel(name: String?, product: String?): String {
        val parts = listOfNotNull(name?.trim()?.takeIf { it.isNotEmpty() }, product?.trim()?.takeIf { it.isNotEmpty() })
        return parts.distinctBy { it.lowercase() }.joinToString(" · ")
    }

    private fun kind(type: String): ListenKind = when (type) {
        "track" -> ListenKind.MUSIC
        "movie", "episode", "clip", "video", "musicvideo", "trailer" -> ListenKind.VIDEO
        else -> ListenKind.OTHER
    }

    private fun title(item: PlexMetadata): String = item.title

    private fun subtitle(item: PlexMetadata): String? = when (kind(item.type)) {
        ListenKind.MUSIC -> listOfNotNull(item.trackArtist, item.parentTitle)
        ListenKind.VIDEO -> listOfNotNull(item.grandparentTitle, item.parentTitle.takeIf { item.type == "episode" })
        ListenKind.OTHER -> listOfNotNull(item.grandparentTitle)
    }.filter { it.isNotBlank() }.distinct().joinToString(" — ").takeIf { it.isNotEmpty() }

    private fun artwork(item: PlexMetadata): String? = when (kind(item.type)) {
        ListenKind.MUSIC -> item.parentThumb ?: item.thumb ?: item.grandparentThumb
        else -> item.grandparentThumb ?: item.parentThumb ?: item.thumb
    }
}

package ovh.battistella.sillon.data.jellyfin

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.fetch
import ovh.battistella.sillon.together.ClockSample
import java.util.concurrent.TimeUnit

class SyncPlayApi(
    private val client: OkHttpClient,
    private val device: PlexDevice,
) {
    private val socketClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(SOCKET_PING_S, TimeUnit.SECONDS)
        .build()

    suspend fun groups(server: ServerEndpoint, deviceId: String): List<SyncPlayGroupInfo> =
        PlexJson.decodeFromString(ListSerializer(SyncPlayGroupInfo.serializer()), get(server, deviceId, "SyncPlay/List"))

    suspend fun create(server: ServerEndpoint, deviceId: String, name: String) =
        post(server, deviceId, "SyncPlay/New") { put("GroupName", name) }

    suspend fun join(server: ServerEndpoint, deviceId: String, groupId: String) =
        post(server, deviceId, "SyncPlay/Join") { put("GroupId", groupId) }

    suspend fun leave(server: ServerEndpoint, deviceId: String) = post(server, deviceId, "SyncPlay/Leave")

    suspend fun setNewQueue(server: ServerEndpoint, deviceId: String, itemIds: List<String>, index: Int, positionMs: Long) =
        post(server, deviceId, "SyncPlay/SetNewQueue") {
            putJsonArray("PlayingQueue") { itemIds.forEach { add(it) } }
            put("PlayingItemPosition", index)
            put("StartPositionTicks", msToTicks(positionMs))
        }

    suspend fun queue(server: ServerEndpoint, deviceId: String, itemIds: List<String>) =
        post(server, deviceId, "SyncPlay/Queue") {
            putJsonArray("ItemIds") { itemIds.forEach { add(it) } }
            put("Mode", "Queue")
        }

    suspend fun setPlaylistItem(server: ServerEndpoint, deviceId: String, playlistItemId: String) =
        post(server, deviceId, "SyncPlay/SetPlaylistItem") { put("PlaylistItemId", playlistItemId) }

    suspend fun unpause(server: ServerEndpoint, deviceId: String) = post(server, deviceId, "SyncPlay/Unpause")

    suspend fun pause(server: ServerEndpoint, deviceId: String) = post(server, deviceId, "SyncPlay/Pause")

    suspend fun seek(server: ServerEndpoint, deviceId: String, positionMs: Long) =
        post(server, deviceId, "SyncPlay/Seek") { put("PositionTicks", msToTicks(positionMs)) }

    suspend fun nextItem(server: ServerEndpoint, deviceId: String, playlistItemId: String) =
        post(server, deviceId, "SyncPlay/NextItem") { put("PlaylistItemId", playlistItemId) }

    suspend fun previousItem(server: ServerEndpoint, deviceId: String, playlistItemId: String) =
        post(server, deviceId, "SyncPlay/PreviousItem") { put("PlaylistItemId", playlistItemId) }

    suspend fun setShuffle(server: ServerEndpoint, deviceId: String, shuffle: Boolean) =
        post(server, deviceId, "SyncPlay/SetShuffleMode") { put("Mode", if (shuffle) "Shuffle" else "Sorted") }

    suspend fun setRepeat(server: ServerEndpoint, deviceId: String, mode: String) =
        post(server, deviceId, "SyncPlay/SetRepeatMode") { put("Mode", mode) }

    suspend fun reportReadiness(
        server: ServerEndpoint,
        deviceId: String,
        ready: Boolean,
        serverNowMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
        playlistItemId: String,
    ) = post(server, deviceId, if (ready) "SyncPlay/Ready" else "SyncPlay/Buffering") {
        put("When", formatServerDate(serverNowMs))
        put("PositionTicks", msToTicks(positionMs))
        put("IsPlaying", isPlaying)
        put("PlaylistItemId", playlistItemId)
    }

    suspend fun ping(server: ServerEndpoint, deviceId: String, pingMs: Long) =
        post(server, deviceId, "SyncPlay/Ping") { put("Ping", pingMs) }

    suspend fun clockSample(server: ServerEndpoint, deviceId: String, localClock: () -> Long): ClockSample? {
        val sentAt = localClock()
        val body = get(server, deviceId, "GetUtcTime")
        val receivedAt = localClock()
        val time = PlexJson.decodeFromString(UtcTime.serializer(), body)
        val serverReceivedAt = parseServerDate(time.requestReceptionTime) ?: return null
        val serverSentAt = parseServerDate(time.responseTransmissionTime) ?: return null
        return ClockSample(sentAt, serverReceivedAt, serverSentAt, receivedAt)
    }

    fun openSocket(server: ServerEndpoint, deviceId: String, listener: WebSocketListener): WebSocket {
        val url = url(server, "socket").newBuilder()
            .addQueryParameter("api_key", server.token)
            .addQueryParameter("deviceId", deviceId)
            .build()
        return socketClient.newWebSocket(Request.Builder().url(url).headers(deviceId, server.token).build(), listener)
    }

    private suspend fun get(server: ServerEndpoint, deviceId: String, path: String): String =
        client.fetch(Request.Builder().url(url(server, path)).headers(deviceId, server.token).build())

    private suspend fun post(
        server: ServerEndpoint,
        deviceId: String,
        path: String,
        body: JsonObjectBuilder.() -> Unit = {},
    ) {
        val json: JsonObject = buildJsonObject(body)
        client.fetch(
            Request.Builder().url(url(server, path)).headers(deviceId, server.token)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build(),
        )
    }

    private fun Request.Builder.headers(deviceId: String, token: String): Request.Builder = apply {
        val auth = buildString {
            append("MediaBrowser Client=\"").append(device.product).append('"')
            append(", Device=\"").append(device.deviceName.replace("\"", "")).append('"')
            append(", DeviceId=\"").append(deviceId).append('"')
            append(", Version=\"").append(device.version).append('"')
            append(", Token=\"").append(token).append('"')
        }
        header("Accept", "application/json")
        header("Authorization", auth)
        header("X-Emby-Authorization", auth)
    }

    private fun url(server: ServerEndpoint, path: String): HttpUrl =
        server.uri.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path).build()

    private companion object {
        const val SOCKET_PING_S = 10L
    }
}

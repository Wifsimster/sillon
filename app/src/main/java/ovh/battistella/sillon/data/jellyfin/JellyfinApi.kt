package ovh.battistella.sillon.data.jellyfin

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.PlexDirectory
import ovh.battistella.sillon.data.plex.PlexHub
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.fetch

/**
 * Thin client over the Jellyfin API, returning the app's models. Stateless like
 * [ovh.battistella.sillon.data.plex.PlexApi]: the caller passes the device id
 * and the server (address, token, user) on every call.
 *
 * Every call was checked against a Jellyfin 10.10+ server (the public demo).
 */
class JellyfinApi(
    private val client: OkHttpClient,
    private val device: PlexDevice,
) {

    // ---------------------------------------------------------------------
    // Sign-in
    // ---------------------------------------------------------------------

    /** Name and id of the server at [baseUrl]; also checks it is a Jellyfin server. */
    suspend fun publicInfo(baseUrl: String): JellyfinPublicInfo =
        PlexJson.decodeFromString(JellyfinPublicInfo.serializer(), get(url(baseUrl, "System/Info/Public"), deviceId = ""))

    suspend fun authenticate(baseUrl: String, deviceId: String, username: String, password: String): JellyfinAuthResult {
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }
        return PlexJson.decodeFromString(
            JellyfinAuthResult.serializer(),
            post(url(baseUrl, "Users/AuthenticateByName"), deviceId, token = null, body.toString()),
        )
    }

    /** Start a Quick Connect sign-in: the user enters the code in another Jellyfin app. */
    suspend fun quickConnectInitiate(baseUrl: String, deviceId: String): JellyfinQuickConnect =
        PlexJson.decodeFromString(
            JellyfinQuickConnect.serializer(),
            post(url(baseUrl, "QuickConnect/Initiate"), deviceId, token = null, "{}"),
        )

    suspend fun quickConnectState(baseUrl: String, deviceId: String, secret: String): JellyfinQuickConnect =
        PlexJson.decodeFromString(
            JellyfinQuickConnect.serializer(),
            get(url(baseUrl, "QuickConnect/Connect").newBuilder().addQueryParameter("secret", secret).build(), deviceId),
        )

    suspend fun quickConnectAuthenticate(baseUrl: String, deviceId: String, secret: String): JellyfinAuthResult {
        val body = buildJsonObject { put("Secret", secret) }
        return PlexJson.decodeFromString(
            JellyfinAuthResult.serializer(),
            post(url(baseUrl, "Users/AuthenticateWithQuickConnect"), deviceId, token = null, body.toString()),
        )
    }

    // ---------------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------------

    /** The user's libraries; music ones map to music sections. */
    suspend fun sections(server: ServerEndpoint, deviceId: String): List<PlexDirectory> =
        items(server, deviceId, "Users/${server.userId}/Views").map { it.toDirectory() }

    suspend fun artists(server: ServerEndpoint, deviceId: String, sectionKey: String): List<PlexMetadata> =
        items(server, deviceId, "Artists/AlbumArtists", "parentId" to sectionKey, "sortBy" to "SortName").map { it.toMetadata() }

    suspend fun albums(server: ServerEndpoint, deviceId: String, sectionKey: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "parentId" to sectionKey, "includeItemTypes" to "MusicAlbum", "recursive" to "true",
            "sortBy" to "SortName", "fields" to "ChildCount",
        ).map { it.toMetadata() }

    /** An artist's albums, newest first. */
    suspend fun albumsOf(server: ServerEndpoint, deviceId: String, artistId: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "albumArtistIds" to artistId, "includeItemTypes" to "MusicAlbum", "recursive" to "true",
            "sortBy" to "ProductionYear,SortName", "sortOrder" to "Descending", "fields" to "ChildCount",
        ).map { it.toMetadata() }

    /** An album's tracks in disc and track order. */
    suspend fun tracksOf(server: ServerEndpoint, deviceId: String, albumId: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "parentId" to albumId, "includeItemTypes" to "Audio",
            "sortBy" to "ParentIndexNumber,IndexNumber,SortName",
        ).map { it.toMetadata() }

    suspend fun item(server: ServerEndpoint, deviceId: String, id: String): PlexMetadata? {
        val body = get(serverUrl(server, "Users/${server.userId}/Items/$id"), deviceId, server.token)
        return PlexJson.decodeFromString(JellyfinItem.serializer(), body).toMetadata()
    }

    /** Audio playlists. */
    suspend fun playlists(server: ServerEndpoint, deviceId: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "includeItemTypes" to "Playlist", "recursive" to "true", "sortBy" to "SortName", "fields" to "ChildCount",
        ).filter { it.mediaType == null || it.mediaType == "Audio" }.map { it.toMetadata() }

    suspend fun playlistItems(server: ServerEndpoint, deviceId: String, playlistId: String): List<PlexMetadata> =
        items(server, deviceId, "Playlists/$playlistId/Items").map { it.toMetadata() }

    /** Artists, albums and tracks of a music library matching [query], grouped by type like Plex hubs. */
    suspend fun search(server: ServerEndpoint, deviceId: String, sectionKey: String, query: String): List<PlexHub> {
        val found = items(
            server, deviceId, "Items",
            "parentId" to sectionKey, "searchTerm" to query, "recursive" to "true",
            "includeItemTypes" to "MusicArtist,MusicAlbum,Audio", "limit" to SEARCH_LIMIT.toString(),
        ).map { it.toMetadata() }
        return listOf("artist", "album", "track").mapNotNull { type ->
            found.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { PlexHub(type = type, metadata = it) }
        }
    }

    /** Favourite tracks of a music library: Jellyfin's equivalent of 5-star ratings. */
    suspend fun favouriteTracks(server: ServerEndpoint, deviceId: String, sectionKey: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "parentId" to sectionKey, "includeItemTypes" to "Audio", "recursive" to "true",
            "filters" to "IsFavorite", "sortBy" to "SortName",
        ).map { it.toMetadata() }

    // ---------------------------------------------------------------------
    // Media
    // ---------------------------------------------------------------------

    /** Square cover art resized by the server, for a `jellyfin-item:` reference. */
    fun artworkUrl(server: ServerEndpoint, thumb: String?, sizePx: Int): String? {
        val id = thumb?.takeIf { it.startsWith(JELLYFIN_IMAGE) }?.removePrefix(JELLYFIN_IMAGE) ?: return null
        return serverUrl(server, "Items/$id/Images/Primary").newBuilder()
            .addQueryParameter("fillWidth", sizePx.toString())
            .addQueryParameter("fillHeight", sizePx.toString())
            .addQueryParameter("quality", "90")
            .build()
            .toString()
    }

    /** The original file at [partKey] (see [streamPath]), with the token the player needs. */
    fun streamUrl(server: ServerEndpoint, partKey: String): String =
        withKey("${server.uri.trimEnd('/')}$partKey".toHttpUrl(), server.token).toString()

    /**
     * The track transcoded to AAC at [kbps] as an HLS stream. The server copies
     * the query (token included) into every segment URL.
     */
    fun transcodeUrl(server: ServerEndpoint, deviceId: String, id: String, kbps: Int, session: String): String =
        withKey(
            serverUrl(server, "Audio/$id/main.m3u8").newBuilder()
                .addQueryParameter("audioCodec", "aac")
                .addQueryParameter("audioBitRate", (kbps * 1000).toString())
                .addQueryParameter("maxStreamingBitrate", (kbps * 1000).toString())
                .addQueryParameter("segmentContainer", "ts")
                .addQueryParameter("mediaSourceId", id)
                .addQueryParameter("deviceId", deviceId)
                .addQueryParameter("playSessionId", session)
                .build(),
            server.token,
        ).toString()

    /**
     * Report playback: [event] is `Playing` (a track starts), `Playing/Progress`
     * or `Playing/Stopped`. Drives "now playing", the play count and the
     * resume position on the server.
     */
    suspend fun reportPlayback(
        server: ServerEndpoint,
        deviceId: String,
        event: String,
        itemId: String,
        positionMs: Long,
        paused: Boolean,
    ) {
        val body = buildJsonObject {
            put("ItemId", itemId)
            put("PositionTicks", positionMs.coerceAtLeast(0) * TICKS_PER_MS)
            put("IsPaused", paused)
            put("CanSeek", true)
            put("PlayMethod", "DirectPlay")
        }
        post(serverUrl(server, "Sessions/$event"), deviceId, server.token, body.toString())
    }

    // ---------------------------------------------------------------------
    // Plumbing
    // ---------------------------------------------------------------------

    private suspend fun items(
        server: ServerEndpoint,
        deviceId: String,
        path: String,
        vararg query: Pair<String, String>,
    ): List<JellyfinItem> {
        val url = serverUrl(server, path).newBuilder().apply {
            server.userId?.let { addQueryParameter("userId", it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return PlexJson.decodeFromString(JellyfinItems.serializer(), get(url, deviceId, server.token)).items
    }

    private suspend fun get(url: HttpUrl, deviceId: String, token: String? = null): String =
        client.fetch(Request.Builder().url(url).headers(deviceId, token).build())

    private suspend fun post(url: HttpUrl, deviceId: String, token: String?, json: String): String =
        client.fetch(
            Request.Builder().url(url).headers(deviceId, token)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build(),
        )

    private fun Request.Builder.headers(deviceId: String, token: String?): Request.Builder = apply {
        val auth = buildString {
            append("MediaBrowser Client=\"").append(device.product).append('"')
            append(", Device=\"").append(device.deviceName.replace("\"", "")).append('"')
            append(", DeviceId=\"").append(deviceId).append('"')
            append(", Version=\"").append(device.version).append('"')
            if (token != null) append(", Token=\"").append(token).append('"')
        }
        header("Accept", "application/json")
        // Jellyfin 10.10+ reads Authorization; older servers only X-Emby-Authorization.
        header("Authorization", auth)
        header("X-Emby-Authorization", auth)
    }

    private fun url(baseUrl: String, path: String): HttpUrl =
        baseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path).build()

    private fun serverUrl(server: ServerEndpoint, path: String): HttpUrl = url(server.uri, path)

    /** Media URLs are fetched by the player, which can't add headers: the token goes in the query. */
    private fun withKey(url: HttpUrl, token: String): HttpUrl =
        url.newBuilder()
            .addQueryParameter("ApiKey", token) // Jellyfin 10.10+
            .addQueryParameter("api_key", token) // older servers
            .build()

    private companion object {
        const val SEARCH_LIMIT = 60
    }
}

/** Turn what the user typed ("music.example.com", "192.168.1.10:8096/") into a base URL. */
fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

package ovh.battistella.sillon.data.jellyfin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.PlexHub
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.fetch

class JellyfinApi(
    private val client: OkHttpClient,
    private val device: PlexDevice,
) {
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

    suspend fun albumsOf(server: ServerEndpoint, deviceId: String, artistId: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "albumArtistIds" to artistId, "includeItemTypes" to "MusicAlbum", "recursive" to "true",
            "sortBy" to "ProductionYear,SortName", "sortOrder" to "Descending", "fields" to "ChildCount",
        ).map { it.toMetadata() }

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

    suspend fun itemsByIds(server: ServerEndpoint, deviceId: String, ids: List<String>): List<PlexMetadata> =
        ids.chunked(BATCH_SIZE).flatMap { chunk ->
            items(server, deviceId, "Items", "ids" to chunk.joinToString(",")).map { it.toMetadata() }
        }

    suspend fun playlists(server: ServerEndpoint, deviceId: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "includeItemTypes" to "Playlist", "recursive" to "true", "sortBy" to "SortName", "fields" to "ChildCount",
        ).filter { it.mediaType == null || it.mediaType == "Audio" }.map { it.toMetadata() }

    suspend fun playlistItems(server: ServerEndpoint, deviceId: String, playlistId: String): List<PlexMetadata> =
        items(server, deviceId, "Playlists/$playlistId/Items").map { it.toMetadata() }

    suspend fun createPlaylist(server: ServerEndpoint, deviceId: String, name: String, ids: List<String>) {
        val body = buildJsonObject {
            put("Name", name)
            put("Ids", JsonArray(ids.map(::JsonPrimitive)))
            server.userId?.let { put("UserId", it) }
            put("MediaType", "Audio")
        }
        post(serverUrl(server, "Playlists"), deviceId, server.token, body.toString())
    }

    suspend fun addToPlaylist(server: ServerEndpoint, deviceId: String, playlistId: String, ids: List<String>) {
        val url = serverUrl(server, "Playlists/$playlistId/Items").newBuilder()
            .addQueryParameter("ids", ids.joinToString(","))
            .apply { server.userId?.let { addQueryParameter("userId", it) } }
            .build()
        post(url, deviceId, server.token, "")
    }

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

    suspend fun favouriteTracks(server: ServerEndpoint, deviceId: String, sectionKey: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "parentId" to sectionKey, "includeItemTypes" to "Audio", "recursive" to "true",
            "filters" to "IsFavorite", "sortBy" to "SortName",
        ).map { it.toMetadata() }

    /**
     * Jellyfin has no stars: 5 stars is a favourite, 1 star a dislike, and clearing removes both.
     * [stars] 2 to 4 aren't sent (see [ovh.battistella.sillon.data.rating.JellyfinStars]).
     */
    suspend fun rate(server: ServerEndpoint, deviceId: String, id: String, stars: Int?) {
        val favourite = userUrl(server, "UserFavoriteItems/$id")
        val like = userUrl(server, "UserItems/$id/Rating")
        when (stars) {
            5 -> {
                send("POST", favourite, deviceId, server.token)
                send("DELETE", like, deviceId, server.token)
            }
            1 -> {
                send("DELETE", favourite, deviceId, server.token)
                send("POST", like.newBuilder().addQueryParameter("likes", "false").build(), deviceId, server.token)
            }
            null -> {
                send("DELETE", favourite, deviceId, server.token)
                send("DELETE", like, deviceId, server.token)
            }
            else -> throw IllegalArgumentException("Jellyfin keeps 1 or 5 stars, not $stars")
        }
    }

    suspend fun playedTracks(server: ServerEndpoint, deviceId: String, sectionKey: String): List<PlexMetadata> {
        val tracks = LinkedHashMap<String, PlexMetadata>()
        var start = 0
        while (true) {
            val page = page(
                server, deviceId, "Items",
                "parentId" to sectionKey, "includeItemTypes" to "Audio", "recursive" to "true",
                "filters" to "IsPlayed", "sortBy" to "SortName", "enableImages" to "false",
                "enableUserData" to "true", "startIndex" to start.toString(), "limit" to PAGE_SIZE.toString(),
            )
            val before = tracks.size
            page.items.forEach { tracks[it.id] = it.toMetadata() }
            start += page.items.size
            val done = page.items.size < PAGE_SIZE || (page.total > 0 && start >= page.total)
            if (done || tracks.size == before) return tracks.values.toList()
        }
    }

    suspend fun instantMix(server: ServerEndpoint, deviceId: String, itemId: String): List<PlexMetadata> =
        items(server, deviceId, "Items/$itemId/InstantMix", "limit" to RADIO_TRACKS.toString()).map { it.toMetadata() }

    suspend fun randomTracks(server: ServerEndpoint, deviceId: String, sectionKey: String): List<PlexMetadata> =
        items(
            server, deviceId, "Items",
            "parentId" to sectionKey, "includeItemTypes" to "Audio", "recursive" to "true",
            "sortBy" to "Random", "limit" to RADIO_TRACKS.toString(),
        ).map { it.toMetadata() }

    suspend fun itemsPage(
        server: ServerEndpoint,
        deviceId: String,
        query: List<Pair<String, String>>,
    ): Pair<List<PlexMetadata>, Int> {
        val page = page(server, deviceId, "Items", *query.toTypedArray())
        return page.items.map { it.toMetadata() } to page.total
    }

    /** The track's lyrics (Jellyfin 10.9 and later), or null when it has none. */
    suspend fun lyrics(server: ServerEndpoint, deviceId: String, id: String): String? = try {
        get(serverUrl(server, "Audio/$id/Lyrics"), deviceId, server.token)
    } catch (e: PlexHttpException) {
        if (e.code == HTTP_NOT_FOUND) null else throw e
    }

    fun artworkUrl(server: ServerEndpoint, thumb: String?, sizePx: Int): String? {
        val id = thumb?.takeIf { it.startsWith(JELLYFIN_IMAGE) }?.removePrefix(JELLYFIN_IMAGE) ?: return null
        return serverUrl(server, "Items/$id/Images/Primary").newBuilder()
            .addQueryParameter("fillWidth", sizePx.toString())
            .addQueryParameter("fillHeight", sizePx.toString())
            .addQueryParameter("quality", "90")
            .build()
            .toString()
    }

    fun streamUrl(server: ServerEndpoint, partKey: String): String =
        withKey("${server.uri.trimEnd('/')}$partKey".toHttpUrl(), server.token).toString()

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

    private suspend fun items(
        server: ServerEndpoint,
        deviceId: String,
        path: String,
        vararg query: Pair<String, String>,
    ): List<JellyfinItem> = page(server, deviceId, path, *query).items

    private suspend fun page(
        server: ServerEndpoint,
        deviceId: String,
        path: String,
        vararg query: Pair<String, String>,
    ): JellyfinItems {
        val url = serverUrl(server, path).newBuilder().apply {
            server.userId?.let { addQueryParameter("userId", it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return PlexJson.decodeFromString(JellyfinItems.serializer(), get(url, deviceId, server.token))
    }

    private suspend fun get(url: HttpUrl, deviceId: String, token: String? = null): String =
        client.fetch(Request.Builder().url(url).headers(deviceId, token).build())

    private suspend fun post(url: HttpUrl, deviceId: String, token: String?, json: String): String =
        client.fetch(
            Request.Builder().url(url).headers(deviceId, token)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build(),
        )

    private suspend fun send(method: String, url: HttpUrl, deviceId: String, token: String?): String =
        client.fetch(
            Request.Builder().url(url).headers(deviceId, token)
                .method(method, if (method == "DELETE") null else "".toRequestBody("application/json".toMediaType()))
                .build(),
        )

    private fun userUrl(server: ServerEndpoint, path: String): HttpUrl =
        serverUrl(server, path).newBuilder().apply { server.userId?.let { addQueryParameter("userId", it) } }.build()

    private fun Request.Builder.headers(deviceId: String, token: String?): Request.Builder = apply {
        val auth = buildString {
            append("MediaBrowser Client=\"").append(device.product).append('"')
            append(", Device=\"").append(device.deviceName.replace("\"", "")).append('"')
            append(", DeviceId=\"").append(deviceId).append('"')
            append(", Version=\"").append(device.version).append('"')
            if (token != null) append(", Token=\"").append(token).append('"')
        }
        header("Accept", "application/json")
        header("Authorization", auth)
        header("X-Emby-Authorization", auth)
    }

    private fun url(baseUrl: String, path: String): HttpUrl =
        baseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path).build()

    private fun serverUrl(server: ServerEndpoint, path: String): HttpUrl = url(server.uri, path)

    private fun withKey(url: HttpUrl, token: String): HttpUrl =
        url.newBuilder()
            .addQueryParameter("ApiKey", token)
            .addQueryParameter("api_key", token)
            .build()

    private companion object {
        const val SEARCH_LIMIT = 60
        const val RADIO_TRACKS = 100
        const val BATCH_SIZE = 100
        const val PAGE_SIZE = 2_000
        const val HTTP_NOT_FOUND = 404
    }
}

fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

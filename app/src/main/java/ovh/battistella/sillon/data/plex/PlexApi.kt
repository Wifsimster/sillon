package ovh.battistella.sillon.data.plex

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val PlexJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

data class PlexDevice(
    val product: String,
    val version: String,
    val platformVersion: String,
    val deviceName: String,
)

enum class ServerKind { PLEX, JELLYFIN }

data class ServerEndpoint(
    val uri: String,
    val token: String,
    val kind: ServerKind = ServerKind.PLEX,
    val userId: String? = null,
)

enum class TimelineState(val wire: String) {
    Playing("playing"),
    Paused("paused"),
    Buffering("buffering"),
    Stopped("stopped"),
}

class PlexHttpException(val code: Int, message: String) : IOException("HTTP $code: $message") {
    val isUnauthorized: Boolean get() = code == 401
}

class PlexApi(
    private val client: OkHttpClient,
    private val device: PlexDevice,
    private val plexTv: HttpUrl = PLEX_TV.toHttpUrl(),
) {
    private val probeClient: OkHttpClient = client.newBuilder()
        .callTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .connectTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    suspend fun createPin(clientId: String, strong: Boolean): PlexPin {
        val request = Request.Builder()
            .url(
                plexTv.newBuilder()
                    .addPathSegments("api/v2/pins")
                    .apply { if (strong) addQueryParameter("strong", "true") }
                    .build(),
            )
            .headers(clientId)
            .post(FormBody.Builder().build())
            .build()
        return PlexJson.decodeFromString(PlexPin.serializer(), client.fetch(request))
    }

    suspend fun checkPin(clientId: String, pin: PlexPin): PlexPin {
        val request = Request.Builder()
            .url(
                plexTv.newBuilder()
                    .addPathSegments("api/v2/pins")
                    .addPathSegment(pin.id.toString())
                    .addQueryParameter("code", pin.code)
                    .build(),
            )
            .headers(clientId)
            .build()
        return PlexJson.decodeFromString(PlexPin.serializer(), client.fetch(request))
    }

    fun authUrl(clientId: String, pin: PlexPin): String =
        "https://app.plex.tv/auth#?" + listOf(
            "clientID" to clientId,
            "code" to pin.code,
            "context[device][product]" to device.product,
        ).joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

    suspend fun resources(clientId: String, userToken: String): List<PlexResource> {
        val request = Request.Builder()
            .url(
                plexTv.newBuilder()
                    .addPathSegments("api/v2/resources")
                    .addQueryParameter("includeHttps", "1")
                    .addQueryParameter("includeRelay", "1")
                    .build(),
            )
            .headers(clientId, userToken)
            .build()
        return PlexJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(PlexResource.serializer()),
            client.fetch(request),
        )
    }

    suspend fun probe(clientId: String, uri: String, token: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(uri.toHttpUrl().newBuilder().addPathSegment("identity").build())
            .headers(clientId, token)
            .build()
        probeClient.fetch(request)
        true
    }.getOrDefault(false)

    suspend fun sections(clientId: String, server: ServerEndpoint): List<PlexDirectory> =
        get(clientId, server, "library/sections").directories

    suspend fun artists(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(
            clientId, server, "library/sections/$sectionKey/all",
            "type" to TYPE_ARTIST, "sort" to "titleSort",
        ).metadata

    suspend fun albums(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(
            clientId, server, "library/sections/$sectionKey/all",
            "type" to TYPE_ALBUM, "sort" to "titleSort",
        ).metadata

    suspend fun fiveStarTracks(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(
            clientId, server, "library/sections/$sectionKey/all",
            "type" to TYPE_TRACK, "userRating" to "10", "sort" to "titleSort",
        ).metadata.filter { (it.userRating ?: 0f) >= FIVE_STARS }

    suspend fun sectionItems(
        clientId: String,
        server: ServerEndpoint,
        sectionKey: String,
        query: List<Pair<String, String>>,
    ): MediaContainer = get(clientId, server, "library/sections/$sectionKey/all", *query.toTypedArray())

    suspend fun stations(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(clientId, server, "hubs/sections/$sectionKey", "includeStations" to "1", "count" to "20")
            .hubs.filter { it.hubIdentifier.startsWith("music.stations") }
            .flatMap { it.metadata }

    suspend fun sonicallySimilar(
        clientId: String,
        server: ServerEndpoint,
        trackKey: String,
        limit: Int = RADIO_TRACKS,
    ): List<PlexMetadata> =
        get(
            clientId, server, "library/metadata/$trackKey/nearest",
            "limit" to limit.toString(), "maxDistance" to SONIC_MAX_DISTANCE,
        ).metadata

    suspend fun mixes(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(clientId, server, "hubs/sections/$sectionKey", "includeStations" to "1", "count" to "20")
            .hubs.filter { it.hubIdentifier.contains("mix", ignoreCase = true) }
            .flatMap { it.metadata }
            .filter { !it.key.isNullOrBlank() }

    suspend fun artistNamed(clientId: String, server: ServerEndpoint, sectionKey: String, name: String): PlexMetadata? =
        get(clientId, server, "library/sections/$sectionKey/all", "type" to TYPE_ARTIST, "title" to name)
            .metadata.firstOrNull { it.title.equals(name, ignoreCase = true) }

    suspend fun artistTracks(clientId: String, server: ServerEndpoint, artistKey: String): List<PlexMetadata> =
        get(clientId, server, "library/metadata/$artistKey/allLeaves").metadata

    suspend fun artistStation(clientId: String, server: ServerEndpoint, artistKey: String): String? =
        get(clientId, server, "library/metadata/$artistKey", "includeStations" to "1")
            .metadata.firstOrNull()?.stations?.metadata?.firstOrNull()?.key

    suspend fun stationTracks(
        clientId: String,
        server: ServerEndpoint,
        machineId: String,
        stationPath: String,
        count: Int = RADIO_TRACKS,
    ): List<PlexMetadata> {
        val url = server.uri.toHttpUrl().newBuilder()
            .addPathSegment("playQueues")
            .addQueryParameter("type", "audio")
            .addQueryParameter("uri", "server://$machineId/com.plexapp.plugins.library$stationPath")
            .addQueryParameter("repeat", "0")
            .addQueryParameter("shuffle", "0")
            .build()
        val created = PlexJson.decodeFromString(
            PlexResponse.serializer(),
            client.fetch(Request.Builder().url(url).headers(clientId, server.token).post(FormBody.Builder().build()).build()),
        ).container
        val id = created.playQueueID ?: return created.metadata
        return get(clientId, server, "playQueues/$id", "window" to count.toString()).metadata
            .ifEmpty { created.metadata }
    }

    suspend fun playlists(clientId: String, server: ServerEndpoint): List<PlexMetadata> =
        get(clientId, server, "playlists", "playlistType" to "audio").metadata

    suspend fun playlistItems(clientId: String, server: ServerEndpoint, ratingKey: String): List<PlexMetadata> =
        get(clientId, server, "playlists/$ratingKey/items").metadata

    suspend fun createPlaylist(clientId: String, server: ServerEndpoint, machineId: String, title: String, itemKey: String) {
        send(
            clientId, server, "POST", "playlists",
            "type" to "audio", "title" to title, "smart" to "0", "uri" to libraryUri(machineId, itemKey),
        )
    }

    suspend fun addToPlaylist(clientId: String, server: ServerEndpoint, machineId: String, playlistKey: String, itemKey: String) {
        send(clientId, server, "PUT", "playlists/$playlistKey/items", "uri" to libraryUri(machineId, itemKey))
    }

    /** Rates a track on the 0–10 scale (stars × 2); 0 clears the rating, which Plex takes as -1. */
    suspend fun rate(clientId: String, server: ServerEndpoint, ratingKey: String, rating: Int) {
        send(
            clientId, server, "PUT", ":/rate",
            "key" to ratingKey, "identifier" to LIBRARY_IDENTIFIER,
            "rating" to if (rating > 0) rating.coerceAtMost(10).toString() else "-1",
        )
    }

    private fun libraryUri(machineId: String, itemKey: String) =
        "server://$machineId/com.plexapp.plugins.library/library/metadata/$itemKey"

    suspend fun search(clientId: String, server: ServerEndpoint, sectionKey: String, query: String): List<PlexHub> =
        get(
            clientId, server, "hubs/search",
            "query" to query, "sectionId" to sectionKey, "limit" to SEARCH_LIMIT.toString(),
        ).hubs

    suspend fun children(clientId: String, server: ServerEndpoint, ratingKey: String): List<PlexMetadata> =
        get(clientId, server, "library/metadata/$ratingKey/children").metadata

    suspend fun metadataBatch(clientId: String, server: ServerEndpoint, ratingKeys: List<String>): List<PlexMetadata> =
        ratingKeys.chunked(BATCH_SIZE).flatMap { keys ->
            get(clientId, server, "library/metadata/${keys.joinToString(",")}").metadata
        }

    suspend fun metadata(clientId: String, server: ServerEndpoint, ratingKey: String): PlexMetadata? =
        get(clientId, server, "library/metadata/$ratingKey").metadata.firstOrNull()

    /** The raw lyrics of a track (LRC, plain text or Plex's lines), or null when the server has none. */
    suspend fun lyrics(clientId: String, server: ServerEndpoint, trackKey: String): String? {
        val track = get(clientId, server, "library/metadata/$trackKey", "includeLyrics" to "1").metadata.firstOrNull()
        val key = track?.lyricsKey ?: return null
        val url = "${server.uri.trimEnd('/')}/${key.trimStart('/')}".toHttpUrl()
        return client.fetch(Request.Builder().url(url).headers(clientId, server.token).build())
    }

    suspend fun timeline(
        clientId: String,
        server: ServerEndpoint,
        ratingKey: String,
        state: TimelineState,
        timeMs: Long,
        durationMs: Long,
        continuing: Boolean = false,
    ) {
        val url = server.uri.toHttpUrl().newBuilder()
            .addPathSegments(":/timeline")
            .addQueryParameter("ratingKey", ratingKey)
            .addQueryParameter("key", "/library/metadata/$ratingKey")
            .addQueryParameter("state", state.wire)
            .addQueryParameter("time", timeMs.coerceAtLeast(0).toString())
            .addQueryParameter("duration", durationMs.coerceAtLeast(0).toString())
            .apply { if (state == TimelineState.Stopped) addQueryParameter("continuing", if (continuing) "1" else "0") }
            .build()
        val request = Request.Builder()
            .url(url)
            .headers(clientId, server.token)
            .post(FormBody.Builder().build())
            .build()
        client.fetch(request)
    }

    fun artworkUrl(server: ServerEndpoint, thumb: String?, sizePx: Int): String? {
        if (thumb.isNullOrBlank()) return null
        return server.uri.toHttpUrl().newBuilder()
            .addPathSegments("photo/:/transcode")
            .addQueryParameter("width", sizePx.toString())
            .addQueryParameter("height", sizePx.toString())
            .addQueryParameter("minSize", "1")
            .addQueryParameter("upscale", "1")
            .addQueryParameter("url", thumb)
            .addQueryParameter("X-Plex-Token", server.token)
            .build()
            .toString()
    }

    fun transcodeUrl(server: ServerEndpoint, clientId: String, ratingKey: String, kbps: Int, session: String): String =
        server.uri.toHttpUrl().newBuilder()
            .addPathSegments("music/:/transcode/universal/start.m3u8")
            .addQueryParameter("path", "/library/metadata/$ratingKey")
            .addQueryParameter("mediaIndex", "0")
            .addQueryParameter("partIndex", "0")
            .addQueryParameter("protocol", "hls")
            .addQueryParameter("directPlay", "0")
            .addQueryParameter("directStream", "0")
            .addQueryParameter("musicBitrate", kbps.toString())
            .addQueryParameter("session", session)
            .addQueryParameter("X-Plex-Session-Identifier", session)
            .addQueryParameter("X-Plex-Client-Identifier", clientId)
            .addQueryParameter("X-Plex-Product", device.product)
            .addQueryParameter("X-Plex-Version", device.version)
            .addQueryParameter("X-Plex-Platform", "Android")
            .addQueryParameter("X-Plex-Device", "Android")
            .addQueryParameter(
                "X-Plex-Client-Profile-Extra",
                "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac)" +
                    "+add-limitation(scope=musicCodec&scopeName=aac&type=upperBound&name=audio.bitrate&value=$kbps)",
            )
            .addQueryParameter("X-Plex-Token", server.token)
            .build()
            .toString()

    /**
     * A transient (delegation) token: a stand-in for the account token in URLs handed to another
     * device, here a Sonos speaker. Plex expires it on its own and on a server restart.
     */
    suspend fun transientToken(clientId: String, server: ServerEndpoint): String? =
        get(clientId, server, "security/token", "type" to "delegation", "scope" to "all").token?.takeIf { it.isNotBlank() }

    /** A plain-HTTP MP3 transcode (no HLS) for a Sonos speaker, which fetches it itself. */
    fun sonosTranscodeUrl(server: ServerEndpoint, clientId: String, ratingKey: String, kbps: Int, session: String): String =
        server.uri.toHttpUrl().newBuilder()
            .addPathSegments("music/:/transcode/universal/start.mp3")
            .addQueryParameter("path", "/library/metadata/$ratingKey")
            .addQueryParameter("mediaIndex", "0")
            .addQueryParameter("partIndex", "0")
            .addQueryParameter("protocol", "http")
            .addQueryParameter("directPlay", "0")
            .addQueryParameter("directStream", "0")
            .addQueryParameter("musicBitrate", kbps.toString())
            .addQueryParameter("session", session)
            .addQueryParameter("X-Plex-Session-Identifier", session)
            .addQueryParameter("X-Plex-Client-Identifier", clientId)
            .addQueryParameter("X-Plex-Product", device.product)
            .addQueryParameter("X-Plex-Version", device.version)
            .addQueryParameter("X-Plex-Platform", "Android")
            .addQueryParameter("X-Plex-Device", "Android")
            .addQueryParameter(
                "X-Plex-Client-Profile-Extra",
                "add-transcode-target(type=musicProfile&context=streaming&protocol=http&container=mp3&audioCodec=mp3)" +
                    "+add-limitation(scope=musicCodec&scopeName=mp3&type=upperBound&name=audio.bitrate&value=$kbps)",
            )
            .addQueryParameter("X-Plex-Token", server.token)
            .build()
            .toString()

    fun streamUrl(server: ServerEndpoint, partKey: String): String =
        server.uri.toHttpUrl().newBuilder()
            .addPathSegments(partKey.trimStart('/'))
            .addQueryParameter("X-Plex-Token", server.token)
            .build()
            .toString()

    private suspend fun get(
        clientId: String,
        server: ServerEndpoint,
        path: String,
        vararg query: Pair<String, String>,
    ): MediaContainer {
        val url = server.uri.toHttpUrl().newBuilder().addPathSegments(path).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(url).headers(clientId, server.token).build()
        return PlexJson.decodeFromString(PlexResponse.serializer(), client.fetch(request)).container
    }

    private suspend fun send(
        clientId: String,
        server: ServerEndpoint,
        method: String,
        path: String,
        vararg query: Pair<String, String>,
    ) {
        val url = server.uri.toHttpUrl().newBuilder().addPathSegments(path).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        client.fetch(Request.Builder().url(url).headers(clientId, server.token).method(method, FormBody.Builder().build()).build())
    }

    private fun Request.Builder.headers(clientId: String, token: String? = null): Request.Builder = apply {
        header("Accept", "application/json")
        header("X-Plex-Product", device.product)
        header("X-Plex-Version", device.version)
        header("X-Plex-Client-Identifier", clientId)
        header("X-Plex-Platform", "Android")
        header("X-Plex-Platform-Version", device.platformVersion)
        header("X-Plex-Device", "Android")
        header("X-Plex-Device-Name", device.deviceName)
        if (token != null) header("X-Plex-Token", token)
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        const val PLEX_TV = "https://plex.tv/"
        private const val LIBRARY_IDENTIFIER = "com.plexapp.plugins.library"
        private const val TYPE_ARTIST = "8"
        private const val TYPE_ALBUM = "9"
        private const val TYPE_TRACK = "10"
        private const val FIVE_STARS = 9.5f
        private const val SEARCH_LIMIT = 20
        private const val BATCH_SIZE = 50
        const val RADIO_TRACKS = 100
        private const val SONIC_MAX_DISTANCE = "0.25"
        private const val PROBE_TIMEOUT_S = 4L
    }
}

fun streamEndpoint(streamUrl: String): ServerEndpoint? {
    val url = streamUrl.toHttpUrlOrNull() ?: return null
    val token = url.queryParameter("X-Plex-Token") ?: return null
    val base = url.newBuilder().encodedPath("/").query(null).fragment(null).build()
    return ServerEndpoint(base.toString().trimEnd('/'), token)
}

internal suspend fun OkHttpClient.fetch(request: Request): String =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = runCatching { it.body?.string().orEmpty() }
                    when {
                        body.isFailure -> cont.resumeWithException(body.exceptionOrNull()!!)
                        !it.isSuccessful -> cont.resumeWithException(PlexHttpException(it.code, it.message))
                        else -> cont.resume(body.getOrThrow())
                    }
                }
            }
        })
    }

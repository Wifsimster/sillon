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

/** Lenient decoder shared by every Plex call: unknown fields and nulls are tolerated. */
val PlexJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/** How this app introduces itself to Plex (shown under Settings → Devices on plex.tv). */
data class PlexDevice(
    val product: String,
    val version: String,
    val platformVersion: String,
    val deviceName: String,
)

/** The kind of media server Sillon talks to. */
enum class ServerKind { PLEX, JELLYFIN }

/**
 * A chosen server connection plus the token that unlocks it. [userId] is the
 * Jellyfin user the token belongs to (Jellyfin scopes library calls by user).
 */
data class ServerEndpoint(
    val uri: String,
    val token: String,
    val kind: ServerKind = ServerKind.PLEX,
    val userId: String? = null,
)

/** Playback states understood by `/:/timeline`. */
enum class TimelineState(val wire: String) {
    Playing("playing"),
    Paused("paused"),
    Buffering("buffering"),
    Stopped("stopped"),
}

/** A non-2xx answer from Plex. 401 means the token was revoked or never valid. */
class PlexHttpException(val code: Int, message: String) : IOException("HTTP $code: $message") {
    val isUnauthorized: Boolean get() = code == 401
}

/**
 * Thin client over plex.tv and Plex Media Server. Stateless: the caller passes
 * the client identifier and tokens on every call, so this class never decides
 * which account or server is current.
 */
class PlexApi(
    private val client: OkHttpClient,
    private val device: PlexDevice,
    private val plexTv: HttpUrl = PLEX_TV.toHttpUrl(),
) {

    /** Short-fused client for probing connections: an unreachable LAN address must fail fast. */
    private val probeClient: OkHttpClient = client.newBuilder()
        .callTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .connectTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    // ---------------------------------------------------------------------
    // plex.tv: sign-in and server discovery
    // ---------------------------------------------------------------------

    /**
     * Create a sign-in PIN. Plex has two kinds, one per way of approving it:
     * a [strong] one (25 characters) for the browser page at [authUrl], and a
     * short one (4 characters) to type at plex.tv/link. The browser page
     * rejects a short PIN with "unable to respond to the request".
     */
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

    /** Poll a PIN; its [PlexPin.authToken] appears once the user approved it. */
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

    /** Browser URL where the signed-in user approves a strong [pin] for this device. */
    fun authUrl(clientId: String, pin: PlexPin): String =
        "https://app.plex.tv/auth#?" + listOf(
            "clientID" to clientId,
            "code" to pin.code,
            "context[device][product]" to device.product,
        ).joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

    /** Servers (and other devices) the account owns or has been shared. */
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

    // ---------------------------------------------------------------------
    // Plex Media Server: library
    // ---------------------------------------------------------------------

    /** True when [uri] answers as a Plex server with [token] within the probe timeout. */
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

    /** All artists of a music section, sorted the way Plex sorts them (ignoring "The"). */
    suspend fun artists(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(
            clientId, server, "library/sections/$sectionKey/all",
            "type" to TYPE_ARTIST, "sort" to "titleSort",
        ).metadata

    /** All albums of a music section, by title. */
    suspend fun albums(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(
            clientId, server, "library/sections/$sectionKey/all",
            "type" to TYPE_ALBUM, "sort" to "titleSort",
        ).metadata

    /**
     * Tracks of a music section the user rated 5 stars. The server filters on
     * `userRating=10`; the result is filtered again here so a server that
     * ignored the filter can never turn this into "every track".
     */
    suspend fun fiveStarTracks(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(
            clientId, server, "library/sections/$sectionKey/all",
            "type" to TYPE_TRACK, "userRating" to "10", "sort" to "titleSort",
        ).metadata.filter { (it.userRating ?: 0f) >= FIVE_STARS }

    /**
     * The library's radios (Library Radio, Deep Cuts, Time Travel…): the
     * station hub of the music section. Each item's key is a station path.
     */
    suspend fun stations(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(clientId, server, "hubs/sections/$sectionKey", "includeStations" to "1", "count" to "20")
            .hubs.filter { it.hubIdentifier.startsWith("music.stations") }
            .flatMap { it.metadata }

    /**
     * Tracks that sound like [trackKey], closest first: Plex's sonic analysis
     * (Plex Pass). Empty until the library has been analysed.
     */
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

    /**
     * The mixes Plex builds from the sonic analysis ("Mixes for you"): hubs of
     * the music section whose identifier names a mix. Each item plays like a
     * station, through its key.
     */
    suspend fun mixes(clientId: String, server: ServerEndpoint, sectionKey: String): List<PlexMetadata> =
        get(clientId, server, "hubs/sections/$sectionKey", "includeStations" to "1", "count" to "20")
            .hubs.filter { it.hubIdentifier.contains("mix", ignoreCase = true) }
            .flatMap { it.metadata }
            .filter { !it.key.isNullOrBlank() }

    /** Every track of an artist, across albums. */
    suspend fun artistTracks(clientId: String, server: ServerEndpoint, artistKey: String): List<PlexMetadata> =
        get(clientId, server, "library/metadata/$artistKey/allLeaves").metadata

    /** The station path of an artist's radio, if the server offers one. */
    suspend fun artistStation(clientId: String, server: ServerEndpoint, artistKey: String): String? =
        get(clientId, server, "library/metadata/$artistKey", "includeStations" to "1")
            .metadata.firstOrNull()?.stations?.metadata?.firstOrNull()?.key

    /**
     * Tracks of a radio. Plex plays stations through a play queue it fills as
     * it goes: create one for the station, then read a window of [count]
     * tracks from it (the creation answer only holds the first ~20).
     */
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

    /** Audio playlists on the server (Plex playlists are server-wide, not per library). */
    suspend fun playlists(clientId: String, server: ServerEndpoint): List<PlexMetadata> =
        get(clientId, server, "playlists", "playlistType" to "audio").metadata

    /** Tracks of a playlist, in playlist order. */
    suspend fun playlistItems(clientId: String, server: ServerEndpoint, ratingKey: String): List<PlexMetadata> =
        get(clientId, server, "playlists/$ratingKey/items").metadata

    /** Artists, albums and tracks of a music section matching [query], grouped by type. */
    suspend fun search(clientId: String, server: ServerEndpoint, sectionKey: String, query: String): List<PlexHub> =
        get(
            clientId, server, "hubs/search",
            "query" to query, "sectionId" to sectionKey, "limit" to SEARCH_LIMIT.toString(),
        ).hubs

    /** Direct children of an item: an artist's albums, or an album's tracks. */
    suspend fun children(clientId: String, server: ServerEndpoint, ratingKey: String): List<PlexMetadata> =
        get(clientId, server, "library/metadata/$ratingKey/children").metadata

    /** A single item (artist, album or track) by key. */
    /**
     * Full metadata of several items in one request (Plex accepts a comma
     * separated list of keys). Unlike listings, it includes the audio streams
     * and their loudness gains.
     */
    suspend fun metadataBatch(clientId: String, server: ServerEndpoint, ratingKeys: List<String>): List<PlexMetadata> =
        ratingKeys.chunked(BATCH_SIZE).flatMap { keys ->
            get(clientId, server, "library/metadata/${keys.joinToString(",")}").metadata
        }

    suspend fun metadata(clientId: String, server: ServerEndpoint, ratingKey: String): PlexMetadata? =
        get(clientId, server, "library/metadata/$ratingKey").metadata.firstOrNull()

    /**
     * Report playback of a track. Plex expects this on every state change and
     * every ~10 s while playing; it drives the server's "Now playing" dashboard,
     * the play history and the play count (the server marks a track played once
     * a report puts it near its end).
     *
     * [continuing] tells the server, on a `stopped` report, that another track
     * follows straight away, so it doesn't end the listening session.
     */
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

    /**
     * Square cover art resized on the server, so a 3000 px scan isn't downloaded
     * to fill a 56 dp thumbnail. The token travels in the query string because
     * the image loader fetches the URL as-is.
     */
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

    /**
     * The track transcoded by the server to AAC at [kbps], as an HLS stream
     * (seekable, with a known duration). [session] identifies the transcode on
     * the server; a new one per track.
     */
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
            // Ask for AAC in MPEG-TS whatever profile the server picks for "Android".
            .addQueryParameter(
                "X-Plex-Client-Profile-Extra",
                "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac)" +
                    "+add-limitation(scope=musicCodec&scopeName=aac&type=upperBound&name=audio.bitrate&value=$kbps)",
            )
            .addQueryParameter("X-Plex-Token", server.token)
            .build()
            .toString()

    /** Original file of a track, played as-is (direct play, no transcoding). */
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
        private const val TYPE_ARTIST = "8"
        private const val TYPE_ALBUM = "9"
        private const val TYPE_TRACK = "10"
        private const val FIVE_STARS = 9.5f
        private const val SEARCH_LIMIT = 20
        private const val BATCH_SIZE = 50
        const val RADIO_TRACKS = 100
        /** How far "similar" may stretch (0 = identical); Plexamp's default range. */
        private const val SONIC_MAX_DISTANCE = "0.25"
        private const val PROBE_TIMEOUT_S = 4L
    }
}

/**
 * The server and token a stream URL from [PlexApi.streamUrl] points at. Lets the
 * player report a track to the server it actually came from, even if the user
 * has switched servers since it was queued.
 */
fun streamEndpoint(streamUrl: String): ServerEndpoint? {
    val url = streamUrl.toHttpUrlOrNull() ?: return null
    val token = url.queryParameter("X-Plex-Token") ?: return null
    val base = url.newBuilder().encodedPath("/").query(null).fragment(null).build()
    return ServerEndpoint(base.toString().trimEnd('/'), token)
}

/** Execute a call without blocking a thread, cancelling it with the coroutine. */
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

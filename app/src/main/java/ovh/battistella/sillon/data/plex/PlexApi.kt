package ovh.battistella.sillon.data.plex

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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

/** A chosen server connection plus the token that unlocks it. */
data class ServerEndpoint(
    val uri: String,
    val token: String,
)

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
     * Create a short (4-character) PIN. The user approves it either in the
     * browser via [authUrl] or by typing it at plex.tv/link.
     */
    suspend fun createPin(clientId: String): PlexPin {
        val request = Request.Builder()
            .url(plexTv.newBuilder().addPathSegments("api/v2/pins").build())
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

    /** Browser URL where the signed-in user approves [pin] for this device. */
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

    /** Direct children of an item: an artist's albums, or an album's tracks. */
    suspend fun children(clientId: String, server: ServerEndpoint, ratingKey: String): List<PlexMetadata> =
        get(clientId, server, "library/metadata/$ratingKey/children").metadata

    /** A single item (artist, album or track) by key. */
    suspend fun metadata(clientId: String, server: ServerEndpoint, ratingKey: String): PlexMetadata? =
        get(clientId, server, "library/metadata/$ratingKey").metadata.firstOrNull()

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
        private const val PROBE_TIMEOUT_S = 4L
    }
}

/** Execute a call without blocking a thread, cancelling it with the coroutine. */
private suspend fun OkHttpClient.fetch(request: Request): String =
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

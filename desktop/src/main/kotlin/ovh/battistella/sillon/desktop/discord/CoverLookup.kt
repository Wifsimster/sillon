package ovh.battistella.sillon.desktop.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class CoverLookup(
    client: OkHttpClient,
    private val base: HttpUrl = DEFAULT_BASE.toHttpUrl(),
) {
    private val client = client.newBuilder().callTimeout(TIMEOUT_S, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val found = object : LinkedHashMap<String, String?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > CACHE_SIZE
    }

    suspend fun cover(artist: String?, album: String?): String? {
        val term = listOfNotNull(artist?.trim(), album?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (term.isEmpty() || album.isNullOrBlank()) return null
        val key = term.lowercase()
        synchronized(found) { if (found.containsKey(key)) return found[key] }
        val url = withContext(Dispatchers.IO) { runCatching { search(term) } }
        url.onSuccess { synchronized(found) { found[key] = it } }
        return url.getOrNull()
    }

    private fun search(term: String): String? {
        val url = base.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("term", term)
            .addQueryParameter("entity", "album")
            .addQueryParameter("media", "music")
            .addQueryParameter("limit", "1")
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Cover search failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val art = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("artworkUrl100")?.jsonPrimitive?.content
                ?: return null
            return art.replace("100x100bb", "512x512bb").takeIf { it.startsWith("https://") }
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://itunes.apple.com"
        private const val TIMEOUT_S = 6L
        private const val CACHE_SIZE = 200
    }
}

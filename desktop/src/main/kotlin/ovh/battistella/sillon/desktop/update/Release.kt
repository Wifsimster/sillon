package ovh.battistella.sillon.desktop.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ovh.battistella.sillon.data.plex.fetch

object Versions {
    fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() && it.size <= 4 }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun isRelease(version: String): Boolean = parse(version)?.any { it > 0 } == true && !version.contains('-')
}

data class ChangelogSection(val title: String?, val items: List<String>)

object Changelog {
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val commitRef = Regex("""\s*\(\[?[0-9a-f]{7,40}]?(\([^)]*\))?\)""")
    private val issueRef = Regex("""\s*\(#\d+\)""")
    private val closes = Regex(""",?\s*closes\s+#\d+""", RegexOption.IGNORE_CASE)

    fun parse(notes: String): List<ChangelogSection> {
        val sections = mutableListOf<ChangelogSection>()
        var title: String? = null
        var items = mutableListOf<String>()
        fun flush() {
            if (items.isNotEmpty()) sections += ChangelogSection(title, items)
            items = mutableListOf()
        }
        for (raw in notes.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("### ") -> {
                    flush()
                    title = clean(line.removePrefix("### "))
                }
                line.startsWith("#") -> Unit
                line.startsWith("* ") || line.startsWith("- ") -> clean(line.drop(2)).takeIf { it.isNotEmpty() }?.let { items += it }
                raw.startsWith(" ") && items.isNotEmpty() -> items[items.lastIndex] = items.last() + " " + clean(line)
                else -> clean(line).takeIf { it.isNotEmpty() }?.let { items += it }
            }
        }
        flush()
        return sections
    }

    private fun clean(text: String): String =
        text.replace(commitRef, "")
            .replace(link, "$1")
            .replace(commitRef, "")
            .replace(issueRef, "")
            .replace(closes, "")
            .replace("**", "")
            .replace("`", "")
            .trim()
            .trimEnd(',')
}

data class Release(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val installerUrl: String?,
    val checksumUrl: String?,
    val installerSize: Long,
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tag: String = "",
    val body: String? = null,
    @SerialName("html_url") val pageUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

class UpdateFeed(
    private val client: OkHttpClient,
    private val base: HttpUrl = DEFAULT.toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Release? = release("releases/latest")

    suspend fun tagged(version: String): Release? = release("releases/tags/v${version.removePrefix("v")}")

    private suspend fun release(path: String): Release? {
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments(path).build())
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Sillon")
            .build()
        val found = json.decodeFromString(GithubRelease.serializer(), client.fetch(request))
        if (found.draft || found.prerelease || Versions.parse(found.tag) == null) return null
        val installer = found.assets.firstOrNull { it.name == INSTALLER }
        return Release(
            version = found.tag.removePrefix("v"),
            notes = found.body.orEmpty(),
            pageUrl = found.pageUrl,
            installerUrl = installer?.url?.takeIf { it.startsWith("https://") || base.scheme == "http" },
            checksumUrl = found.assets.firstOrNull { it.name == CHECKSUMS }?.url,
            installerSize = installer?.size ?: 0,
        )
    }

    companion object {
        const val DEFAULT = "https://api.github.com/repos/Wifsimster/sillon/"
        const val INSTALLER = "Sillon-windows-x64.msi"
        const val CHECKSUMS = "SHA256SUMS-windows.txt"
    }
}

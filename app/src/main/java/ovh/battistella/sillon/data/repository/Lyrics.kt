package ovh.battistella.sillon.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Turns what a server returns for a track's lyrics into the lines of the song, without timings. */
object Lyrics {
    private val timeTag = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
    private val wordTag = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val metaTag = Regex("""^\[[a-zA-Z#]+:.*]$""")
    private val xmlLine = Regex("""<Line\b[^>]*>(.*?)</Line>""", RegexOption.DOT_MATCHES_ALL)
    private val xmlText = Regex("""\btext="([^"]*)"""")

    fun parse(body: String?): List<String> {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        // LRC also starts with "[": only text that parses as JSON is read as JSON.
        val json = if (text.startsWith("{") || text.startsWith("[")) runCatching { Json.parseToJsonElement(text) }.getOrNull() else null
        val lines = when {
            json != null -> fromJson(json)
            text.startsWith("{") -> emptyList()
            text.startsWith("<") -> fromXml(text)
            else -> fromLrc(text)
        }
        return lines.map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .fold(mutableListOf<String>()) { acc, line -> acc.apply { if (lastOrNull() != line) add(line) } }
    }

    private fun fromLrc(text: String): List<String> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (metaTag.matches(line) && !timeTag.containsMatchIn(line)) return@mapNotNull null
        line.replace(timeTag, "").replace(wordTag, "")
    }

    private fun fromXml(text: String): List<String> = xmlLine.findAll(text).map { line ->
        xmlText.findAll(line.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
    }.toList()

    private fun fromJson(root: JsonElement): List<String> {
        // Jellyfin: { "Lyrics": [ { "Text": "…" } ] }. Plex: { "MediaContainer": { "Lyrics": [ { "Line": [ { "Span": [ { "text": "…" } ] } ] } ] } }.
        val lines = mutableListOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val line = element["Line"]
                    val spans = element["Span"]
                    val own = (element["Text"] ?: element["text"]) as? JsonPrimitive
                    when {
                        line is JsonArray -> line.forEach { walk(it) }
                        spans is JsonArray -> lines += spans.joinToString("") { span ->
                            ((span as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
                        }
                        own != null && own.isString -> lines += own.content
                        else -> element.values.forEach { walk(it) }
                    }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(root)
        return lines.flatMap { if (timeTag.containsMatchIn(it) || it.contains('\n')) fromLrc(it) else listOf(it) }
    }

    private fun unescape(s: String) = s
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

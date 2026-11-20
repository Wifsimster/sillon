package ovh.battistella.sillon.desktop.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Listening(
    val key: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val playing: Boolean,
    val startedAtMs: Long?,
    val durationMs: Long,
    val coverUrl: String?,
)

data class PresenceLabels(val playing: String, val paused: String)

object Presence {
    const val LISTENING = 2
    const val SHOW_STATE = 1
    private const val MIN_TEXT = 2
    private const val MAX_TEXT = 128
    private const val MAX_URL = 256

    fun activity(now: Listening, labels: PresenceLabels): JsonObject = buildJsonObject {
        put("type", LISTENING)
        put("status_display_type", SHOW_STATE)
        put("details", text(now.title) ?: "Sillon")
        text(now.artist)?.let { put("state", it) }
        put("assets", buildJsonObject {
            now.coverUrl?.takeIf { it.startsWith("https://") && it.length <= MAX_URL }?.let { put("large_image", it) }
            text(now.album)?.let { put("large_text", it) }
            put("small_image", if (now.playing) "play" else "pause")
            put("small_text", if (now.playing) labels.playing else labels.paused)
        })
        val start = now.startedAtMs
        if (now.playing && start != null) {
            put("timestamps", buildJsonObject {
                put("start", start)
                if (now.durationMs > 0) put("end", start + now.durationMs)
            })
        }
    }

    fun text(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val fitted = if (trimmed.length > MAX_TEXT) trimmed.take(MAX_TEXT - 1).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }.trimEnd() + "…" else trimmed
        return fitted.padEnd(MIN_TEXT, '⠀')
    }
}

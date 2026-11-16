package ovh.battistella.sillon.playback

import java.io.File

data class PlayableTrack(
    val ratingKey: String,
    val streamUrl: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val gainDb: Float? = null,
    val albumKey: String? = null,
    val cacheKey: String? = null,
    val userRating: Float? = null,
)

object TrackGain {
    fun volumeFor(gainDb: Float?): Float =
        if (gainDb == null) 1f else Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(MIN_VOLUME, 1f)

    private const val MIN_VOLUME = 0.1f
}

object StreamUrls {
    fun isTranscode(url: String): Boolean =
        url.contains("/transcode/universal/") || url.substringBefore('?').endsWith(".m3u8")

    fun ofFile(file: File): String = "file://" + encodePath(file.absolutePath.replace(File.separatorChar, '/').let { if (it.startsWith("/")) it else "/$it" })

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*/"

    private fun encodePath(path: String): String {
        val out = StringBuilder(path.length)
        for (byte in path.toByteArray(Charsets.UTF_8)) {
            val c = (byte.toInt() and 0xFF).toChar()
            if (c in UNRESERVED) out.append(c) else out.append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
        return out.toString()
    }
}

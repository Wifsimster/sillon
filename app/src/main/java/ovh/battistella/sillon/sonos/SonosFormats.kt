package ovh.battistella.sillon.sonos

import ovh.battistella.sillon.data.plex.PlexMetadata

/** What a file is made of, as the server describes it. Any field may be unknown. */
data class AudioFormat(
    val codec: String?,
    val container: String?,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrateKbps: Int? = null,
)

/** How a track reaches a Sonos speaker. */
sealed interface SonosStream {
    /** The original file, as it is on the server. */
    data class Direct(val mimeType: String) : SonosStream

    /** A server transcode to MP3 320 kbps, one plain HTTP file (no HLS). */
    data object Transcode : SonosStream {
        const val MIME_TYPE = "audio/mpeg"
        const val KBPS = 320
    }
}

/**
 * Which files a Sonos speaker plays as they are (Sonos S2, music library and streams):
 * MP3, AAC and Ogg Vorbis up to 320 kbps; FLAC and ALAC up to 24 bit; WAV and AIFF at 16 bit;
 * nothing above 48 kHz. Everything else (Opus, WMA, DSD, APE, hi-res…) is transcoded by the server.
 * Sources: support.sonos.com "Supported audio formats for Sonos music library",
 * docs.sonos.com "Supported audio formats".
 */
object SonosFormats {
    const val MAX_SAMPLE_RATE = 48_000
    const val MAX_LOSSY_KBPS = 320

    /** Servers report a VBR file's average, often a little above its nominal 320. */
    private const val LOSSY_SLACK_KBPS = 32
    private const val MAX_LOSSLESS_BITS = 24
    private const val MAX_PCM_BITS = 16

    fun plan(format: AudioFormat): SonosStream {
        val codec = normalise(format.codec) ?: normalise(format.container) ?: return SonosStream.Transcode
        if (format.sampleRate != null && format.sampleRate > MAX_SAMPLE_RATE) return SonosStream.Transcode
        return when (codec) {
            "mp3", "aac", "vorbis" -> {
                if (format.bitrateKbps != null && format.bitrateKbps > MAX_LOSSY_KBPS + LOSSY_SLACK_KBPS) SonosStream.Transcode else direct(codec)
            }
            // A lossless file of unknown rate may be hi-res: let the server bring it down.
            "flac", "alac" -> lossless(codec, format, MAX_LOSSLESS_BITS)
            "wav", "aiff" -> lossless(codec, format, MAX_PCM_BITS)
            else -> SonosStream.Transcode
        }
    }

    private fun lossless(codec: String, format: AudioFormat, maxBits: Int): SonosStream = when {
        format.sampleRate == null -> SonosStream.Transcode
        format.bitDepth != null && format.bitDepth > maxBits -> SonosStream.Transcode
        else -> direct(codec)
    }

    private fun direct(codec: String) = SonosStream.Direct(
        when (codec) {
            "mp3" -> "audio/mpeg"
            "aac", "alac" -> "audio/mp4"
            "vorbis" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "audio/aiff"
        },
    )

    /**
     * One name per codec, whatever Plex or Jellyfin call it; null for an unknown one. Containers
     * that may hold several codecs (m4a: AAC or ALAC; ogg: Vorbis or Opus) are not guessed.
     */
    private fun normalise(name: String?): String? = when (name?.trim()?.lowercase()?.substringBefore(',')) {
        "mp3", "mpeg" -> "mp3"
        "aac", "mp4a", "he-aac", "aac_latm" -> "aac"
        "alac" -> "alac"
        "flac" -> "flac"
        "vorbis" -> "vorbis"
        "wav", "pcm", "pcm_s16le", "pcm_s24le", "wave" -> "wav"
        "aiff", "aif", "pcm_s16be", "pcm_s24be" -> "aiff"
        else -> null
    }
}

/** The format of a track's first file, as Plex (or Jellyfin, mapped the same way) describes it. */
fun PlexMetadata.audioFormat(): AudioFormat {
    val media = media.firstOrNull()
    val audio = media?.parts?.firstOrNull()?.streams?.firstOrNull { it.streamType == AUDIO_STREAM }
    return AudioFormat(
        codec = audio?.codec ?: media?.audioCodec,
        container = media?.container,
        sampleRate = audio?.samplingRate,
        bitDepth = audio?.bitDepth,
        bitrateKbps = audio?.bitrate ?: media?.bitrate,
    )
}

private const val AUDIO_STREAM = 2

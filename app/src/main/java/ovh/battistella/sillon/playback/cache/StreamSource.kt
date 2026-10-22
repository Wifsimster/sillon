package ovh.battistella.sillon.playback.cache

/** Where a track is played from. */
enum class StreamSource {
    /** The explicit download: a file that stays until the user removes it. */
    DOWNLOAD,

    /** The original file, already whole in the listening cache: no network at all. */
    CACHE,

    /** A transcode already whole in the listening cache: no network at all. */
    CACHED_TRANSCODE,

    /** The original file from the server, kept in the listening cache as it plays. */
    ORIGINAL,

    /** A server transcode (HLS, AAC), kept in the listening cache as it plays. */
    TRANSCODE,
    ;

    /** Played from the original file, so it carries the original's cache key. */
    val original: Boolean get() = this == CACHE || this == ORIGINAL

    /** Played from a transcode. */
    val transcode: Boolean get() = this == CACHED_TRANSCODE || this == TRANSCODE

    companion object {
        /**
         * The best copy at hand: the download, then the original in the cache, then a cached
         * transcode at least as good as what the network asks for ([wantedKbps], null for the
         * original), then the server. Offline, any cached copy beats nothing.
         *
         * A cached original wins over a transcode even on mobile data: no data, better sound.
         * On Wi-Fi with only a transcode cached, the original streams, and replaces it.
         */
        fun choose(
            downloaded: Boolean,
            originalCached: Boolean,
            cachedTranscodeKbps: Int?,
            wantedKbps: Int?,
            online: Boolean = true,
        ): StreamSource = when {
            downloaded -> DOWNLOAD
            originalCached -> CACHE
            cachedTranscodeKbps != null && (!online || (wantedKbps != null && cachedTranscodeKbps >= wantedKbps)) -> CACHED_TRANSCODE
            wantedKbps == null -> ORIGINAL
            else -> TRANSCODE
        }
    }
}

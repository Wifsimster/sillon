package ovh.battistella.sillon.playback.cache

/** Where a track is played from. */
enum class StreamSource {
    /** The explicit download: a file that stays until the user removes it. */
    DOWNLOAD,

    /** The original file, already whole in the listening cache: no network at all. */
    CACHE,

    /** The original file from the server, kept in the listening cache as it plays. */
    ORIGINAL,

    /** A server transcode (HLS, AAC): played but never cached. */
    TRANSCODE,
    ;

    /** The original file is played, so it carries a cache key. */
    val cached: Boolean get() = this == CACHE || this == ORIGINAL

    companion object {
        /**
         * Download first, then the cache, then the network. A transcode is never cached: its
         * segments belong to one server session, and a 128 kbps copy must not stand in for the
         * original. A whole original in the cache wins over a transcode, even on mobile data,
         * since it costs no data and sounds better.
         */
        fun choose(downloaded: Boolean, originalCached: Boolean, transcodeKbps: Int?): StreamSource = when {
            downloaded -> DOWNLOAD
            originalCached -> CACHE
            transcodeKbps == null -> ORIGINAL
            else -> TRANSCODE
        }
    }
}

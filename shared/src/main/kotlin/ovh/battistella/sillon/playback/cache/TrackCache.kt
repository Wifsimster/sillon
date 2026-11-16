package ovh.battistella.sillon.playback.cache

data class CachedVersions(val original: Boolean, val transcodeKbps: Int?) {
    companion object {
        val NONE = CachedVersions(original = false, transcodeKbps = null)
    }
}

interface TrackCache {
    suspend fun versions(serverId: String, trackKey: String, kbpsChoices: List<Int>): CachedVersions

    fun streamTranscode(session: String, serverId: String, trackKey: String, kbps: Int)

    suspend fun replayTranscode(session: String, serverId: String, trackKey: String, kbps: Int)

    suspend fun clear(sweepAgain: Boolean = false)
}

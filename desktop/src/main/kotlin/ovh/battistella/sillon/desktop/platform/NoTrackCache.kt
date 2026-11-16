package ovh.battistella.sillon.desktop.platform

import ovh.battistella.sillon.playback.cache.CachedVersions
import ovh.battistella.sillon.playback.cache.TrackCache

class NoTrackCache : TrackCache {
    override suspend fun versions(serverId: String, trackKey: String, kbpsChoices: List<Int>): CachedVersions = CachedVersions.NONE

    override fun streamTranscode(session: String, serverId: String, trackKey: String, kbps: Int) = Unit

    override suspend fun replayTranscode(session: String, serverId: String, trackKey: String, kbps: Int) = Unit

    override suspend fun clear(sweepAgain: Boolean) = Unit
}

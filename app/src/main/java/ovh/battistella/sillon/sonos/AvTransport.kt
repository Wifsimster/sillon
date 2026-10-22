package ovh.battistella.sillon.sonos

/**
 * The AVTransport and GroupRenderingControl actions Sillon sends, always to the group's
 * coordinator. Track numbers on the wire start at 1; indices in Sillon start at 0.
 */
object AvTransport {
    /** How many tracks one AddMultipleURIsToQueue carries; Sonos refuses more than 16. */
    const val BATCH = 16

    private val INSTANCE = "InstanceID" to "0"

    fun removeAllTracks() = av("RemoveAllTracksFromQueue")

    /**
     * Adds [tracks] to the queue, the first one at [firstIndex] (0-based), or at the end when null.
     * URIs and metadata travel space-separated, as the Sonos app sends them.
     */
    fun addTracks(tracks: List<SonosTrack>, firstIndex: Int?): SoapCall {
        require(tracks.size in 1..BATCH) { "1 to $BATCH tracks per call" }
        return av(
            "AddMultipleURIsToQueue",
            "UpdateID" to "0",
            "NumberOfURIs" to tracks.size.toString(),
            "EnqueuedURIs" to tracks.joinToString(" ") { it.uri },
            "EnqueuedURIsMetaData" to tracks.joinToString(" ") { it.didl },
            "ContainerURI" to "",
            "ContainerMetaData" to "",
            "DesiredFirstTrackNumberEnqueued" to (firstIndex?.plus(1) ?: 0).toString(),
            "EnqueueAsNext" to "0",
        )
    }

    fun removeTracks(fromIndex: Int, count: Int) = av(
        "RemoveTrackRangeFromQueue",
        "UpdateID" to "0",
        "StartingIndex" to (fromIndex + 1).toString(),
        "NumberOfTracks" to count.toString(),
    )

    /**
     * Moves tracks [fromIndex, toIndex) so the first lands at [newIndex] once moved (Media3's
     * meaning); Sonos wants the position to insert before, counted before the move.
     */
    fun moveTracks(fromIndex: Int, toIndex: Int, newIndex: Int): SoapCall {
        val count = toIndex - fromIndex
        val insertBefore = if (newIndex > fromIndex) newIndex + count else newIndex
        return av(
            "ReorderTracksInQueue",
            "StartingIndex" to (fromIndex + 1).toString(),
            "NumberOfTracks" to count.toString(),
            "InsertBefore" to (insertBefore + 1).toString(),
            "UpdateID" to "0",
        )
    }

    /** Points the group at its own queue: `x-rincon-queue:RINCON_…#0`. */
    fun playFromQueue(coordinatorUuid: String) = av(
        "SetAVTransportURI",
        "CurrentURI" to queueUri(coordinatorUuid),
        "CurrentURIMetaData" to "",
    )

    fun queueUri(coordinatorUuid: String) = "x-rincon-queue:$coordinatorUuid#0"

    fun seekToTrack(index: Int) = av("Seek", "Unit" to "TRACK_NR", "Target" to (index + 1).toString())

    fun seekTo(positionMs: Long) = av("Seek", "Unit" to "REL_TIME", "Target" to SonosTime.format(positionMs))

    fun play() = av("Play", "Speed" to "1")

    fun pause() = av("Pause")

    fun next() = av("Next")

    fun previous() = av("Previous")

    fun setPlayMode(shuffle: Boolean, repeatMode: Int) = av("SetPlayMode", "NewPlayMode" to PlayMode.of(shuffle, repeatMode))

    fun transportInfo() = av("GetTransportInfo")

    fun positionInfo() = av("GetPositionInfo")

    fun mediaInfo() = av("GetMediaInfo")

    fun transportSettings() = av("GetTransportSettings")

    fun groupVolume() = SoapCall(SonosService.GROUP_RENDERING, "GetGroupVolume", listOf(INSTANCE))

    fun setGroupVolume(volume: Int) = SoapCall(
        SonosService.GROUP_RENDERING,
        "SetGroupVolume",
        listOf(INSTANCE, "DesiredVolume" to volume.coerceIn(0, 100).toString()),
    )

    fun setGroupMute(muted: Boolean) = SoapCall(
        SonosService.GROUP_RENDERING,
        "SetGroupMute",
        listOf(INSTANCE, "DesiredMute" to if (muted) "1" else "0"),
    )

    fun zoneGroupState() = SoapCall(SonosService.ZONE_GROUP_TOPOLOGY, "GetZoneGroupState")

    private fun av(action: String, vararg args: Pair<String, String>) =
        SoapCall(SonosService.AV_TRANSPORT, action, listOf(INSTANCE) + args)
}

/** Sonos times: `H:MM:SS`, hours not padded. */
object SonosTime {
    fun format(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        return "%d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
    }

    /** Milliseconds, or null for `NOT_IMPLEMENTED`, an empty value or anything else. */
    fun parse(value: String?): Long? {
        val parts = value?.trim()?.split(':')?.takeIf { it.size == 3 } ?: return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].substringBefore('.').toLongOrNull() ?: return null
        if (h < 0 || m !in 0..59 || s !in 0..59) return null
        return ((h * 3600) + (m * 60) + s) * 1000
    }
}

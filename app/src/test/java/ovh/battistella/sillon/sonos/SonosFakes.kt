package ovh.battistella.sillon.sonos

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** A Sonos coordinator answering AVTransport like a real one, and recording every action. */
class FakeSpeaker : SoapCaller {
    val calls = mutableListOf<SoapCall>()
    val queue = mutableListOf<String>()
    var transport = "STOPPED"
    var index = 0
    var positionMs = 0L
    var mediaUri = ""

    /** False: Play leaves the speaker TRANSITIONING (it can't reach the server). */
    var starts = true

    /** False: a Pause isn't reflected yet in the next polls. */
    var pauseShows = true

    fun count(action: String) = calls.count { it.action == action }

    override suspend fun call(baseUrl: String, call: SoapCall): Map<String, String> {
        calls += call
        val args = call.args.toMap()
        return when (call.action) {
            "GetTransportInfo" -> mapOf("CurrentTransportState" to transport)
            "GetPositionInfo" -> mapOf(
                "Track" to if (queue.isEmpty()) "0" else "${index + 1}",
                "RelTime" to SonosTime.format(positionMs),
                "TrackDuration" to "0:03:00",
                "TrackURI" to queue.getOrNull(index).orEmpty(),
            )
            "GetMediaInfo" -> mapOf("CurrentURI" to mediaUri)
            "GetTransportSettings" -> mapOf("PlayMode" to "NORMAL")
            "GetGroupVolume" -> mapOf("CurrentVolume" to "12")
            "RemoveAllTracksFromQueue" -> emptyMap<String, String>().also { queue.clear() }
            "AddMultipleURIsToQueue" -> emptyMap<String, String>().also {
                val uris = args.getValue("EnqueuedURIs").split(' ')
                val at = args.getValue("DesiredFirstTrackNumberEnqueued").toInt()
                if (at == 0) queue.addAll(uris) else queue.addAll(at - 1, uris)
            }
            "SetAVTransportURI" -> emptyMap<String, String>().also { mediaUri = args.getValue("CurrentURI") }
            "Seek" -> emptyMap<String, String>().also {
                if (args["Unit"] == "TRACK_NR") {
                    index = args.getValue("Target").toInt() - 1
                    positionMs = 0
                } else {
                    positionMs = SonosTime.parse(args["Target"]) ?: 0
                }
            }
            "Play" -> emptyMap<String, String>().also { transport = if (starts) "PLAYING" else "TRANSITIONING" }
            "Pause" -> emptyMap<String, String>().also { if (pauseShows) transport = "PAUSED_PLAYBACK" }
            "Next" -> emptyMap<String, String>().also {
                index++
                positionMs = 0
            }
            else -> emptyMap()
        }
    }
}

/** The phone's player, reduced to its queue, position and play/pause. */
@OptIn(UnstableApi::class)
class FakePhone : SimpleBasePlayer(Looper.getMainLooper()) {
    var items: List<MediaItem> = emptyList()
    var index = 0
    var positionMs = 0L
    var playing = false

    override fun getState(): State {
        val state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items.mapIndexed { i, item -> MediaItemData.Builder("$i:${item.mediaId}").setMediaItem(item).build() })
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (items.isEmpty()) Player.STATE_IDLE else Player.STATE_READY)
        if (items.isNotEmpty()) state.setCurrentMediaItemIndex(index).setContentPositionMs(positionMs)
        return state.build()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        positionMs = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET) index = mediaItemIndex
        this.positionMs = if (positionMs == C.TIME_UNSET) 0 else positionMs
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playing = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
}

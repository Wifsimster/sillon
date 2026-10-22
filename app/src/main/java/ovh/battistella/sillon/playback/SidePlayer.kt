package ovh.battistella.sillon.playback

import androidx.annotation.OptIn
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
class SidePlayer(
    base: Player,
    private val exo: ExoPlayer,
    private val grouped: () -> Boolean,
    private val sides: SideSession,
    private val describe: (SideEnd) -> Pair<String, String>,
    private val onEndedChanged: (SideEnd?) -> Unit,
) : ForwardingPlayer(base) {
    private val listeners = mutableListOf<Player.Listener>()
    private var queue: List<String> = emptyList()
    private var shown: SideEnd? = null

    private val machine get() = sides.machine

    init {
        exo.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(EVENT_TIMELINE_CHANGED)) {
                    queue = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
                }
                if (events.containsAny(
                        EVENT_TIMELINE_CHANGED,
                        EVENT_MEDIA_ITEM_TRANSITION,
                        EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        EVENT_POSITION_DISCONTINUITY,
                    )
                ) {
                    sync()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                when {
                    !playWhenReady && reason == PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM ->
                        update { machine.onPausedAtEndOfItem() }
                    playWhenReady && machine.ended != null && sides.state.value.active -> flip()
                }
            }
        })
    }

    fun sync() = update {
        machine.onQueue(queue, exo.currentMediaItemIndex, exo.shuffleModeEnabled)
    }

    fun flip() {
        var target: Int? = null
        update { target = machine.flip() }
        target?.let {
            exo.seekTo(it, 0L)
            exo.play()
        }
    }

    private fun update(change: () -> Unit) {
        val inGroup = grouped()
        machine.grouped = inGroup
        change()
        // While listening together, ExoGroupPlayer owns pauseAtEndOfMediaItems.
        if (!inGroup) exo.pauseAtEndOfMediaItems = machine.pauseAtEnd
        sides.publish()
        val after = sides.state.value.ended
        if (after != shown) {
            shown = after
            val metadata = mediaMetadata
            val events = Player.Events(FlagSet.Builder().add(EVENT_MEDIA_METADATA_CHANGED).build())
            listeners.toList().forEach {
                it.onMediaMetadataChanged(metadata)
                it.onEvents(this, events)
            }
            onEndedChanged(after)
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val end = sides.state.value.ended ?: return base
        val (title, subtitle) = describe(end)
        return base.buildUpon().setTitle(title).setArtist(subtitle).setSubtitle(subtitle).build()
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }
}

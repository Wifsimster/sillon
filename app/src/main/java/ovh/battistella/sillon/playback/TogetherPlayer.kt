package ovh.battistella.sillon.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import ovh.battistella.sillon.together.GroupPlayer
import ovh.battistella.sillon.together.ListenTogether
import ovh.battistella.sillon.together.UserIntent

@OptIn(UnstableApi::class)
class TogetherPlayer(
    player: ExoPlayer,
    private val together: ListenTogether,
) : ForwardingPlayer(player) {
    private fun group(intent: () -> UserIntent): Boolean = together.state.value.isActive && together.intercept(intent())

    override fun play() {
        if (!group { UserIntent.Play }) super.play()
    }

    override fun pause() {
        if (!group { UserIntent.Pause }) super.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (!group { if (playWhenReady) UserIntent.Play else UserIntent.Pause }) super.setPlayWhenReady(playWhenReady)
    }

    override fun seekTo(positionMs: Long) {
        if (!group { UserIntent.Seek(positionMs) }) super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (!group { UserIntent.SeekToItem(mediaItemIndex, positionMs.orZero()) }) super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToDefaultPosition() {
        if (!group { UserIntent.Seek(0) }) super.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (!group { UserIntent.SeekToItem(mediaItemIndex, 0) }) super.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekToNext() {
        if (!group { UserIntent.Next }) super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (!group { UserIntent.Next }) super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        if (!group { UserIntent.Previous(currentPosition) }) super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (!group { UserIntent.Previous(0) }) super.seekToPreviousMediaItem()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!group { newQueue(mediaItems, 0, 0) }) super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        if (!group { newQueue(mediaItems, 0, 0) }) super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!group { newQueue(mediaItems, startIndex, startPositionMs) }) super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        if (!group { newQueue(listOf(mediaItem), 0, 0) }) super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (!group { newQueue(listOf(mediaItem), 0, startPositionMs) }) super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        if (!group { newQueue(listOf(mediaItem), 0, 0) }) super.setMediaItem(mediaItem, resetPosition)
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!group { UserIntent.Append(mediaItems.ids()) }) super.addMediaItems(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        if (!group { UserIntent.Append(mediaItems.ids()) }) super.addMediaItems(index, mediaItems)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        if (!together.state.value.isActive) super.removeMediaItems(fromIndex, toIndex)
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (!group { UserIntent.Shuffle(shuffleModeEnabled) }) super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setRepeatMode(repeatMode: Int) {
        val mode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> "RepeatOne"
            Player.REPEAT_MODE_ALL -> "RepeatAll"
            else -> "RepeatNone"
        }
        if (!group { UserIntent.Repeat(mode) }) super.setRepeatMode(repeatMode)
    }

    override fun stop() {
        if (!group { UserIntent.Pause }) super.stop()
    }

    private fun newQueue(items: List<MediaItem>, index: Int, positionMs: Long) =
        UserIntent.NewQueue(items.ids(), index.coerceAtLeast(0), positionMs.orZero())

    private fun List<MediaItem>.ids() = map { it.mediaId }.filter { it.isNotBlank() }

    private fun Long.orZero() = if (this == C.TIME_UNSET || this < 0) 0L else this
}

@OptIn(UnstableApi::class)
class ExoGroupPlayer(private val player: ExoPlayer) : GroupPlayer {
    private var listener: GroupPlayer.Listener? = null

    private val events = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> listener?.onReadyChanged(true)
                Player.STATE_BUFFERING -> listener?.onReadyChanged(false)
                Player.STATE_ENDED -> listener?.onTrackEnded()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) listener?.onTrackEnded()
        }
    }

    init {
        player.addListener(events)
    }

    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val isPlaying: Boolean get() = player.isPlaying
    override val isReady: Boolean get() = player.playbackState == Player.STATE_READY
    override val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override val currentIndex: Int get() = player.currentMediaItemIndex
    override val loadedIds: List<String> get() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    override fun currentQueue(): Triple<List<String>, Int, Long>? {
        val ids = loadedIds
        if (ids.isEmpty() || ids.any { it.isBlank() }) return null
        return Triple(ids, player.currentMediaItemIndex.coerceIn(0, ids.lastIndex), positionMs)
    }

    override fun enterGroupMode() {
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.pauseAtEndOfMediaItems = true
    }

    override fun exitGroupMode() {
        player.pauseAtEndOfMediaItems = false
        setSpeed(1f)
    }

    override fun load(tracks: List<PlayableTrack>, index: Int, positionMs: Long) {
        player.playWhenReady = false
        player.setMediaItems(tracks.map(MediaItems::from), index, positionMs)
        player.prepare()
    }

    override fun jumpTo(index: Int, positionMs: Long) {
        player.playWhenReady = false
        player.seekTo(index, positionMs)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    override fun seek(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))

    override fun play() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun pause() = player.pause()

    override fun setSpeed(rate: Float) {
        if (player.playbackParameters.speed != rate) player.playbackParameters = PlaybackParameters(rate)
    }

    override fun setListener(listener: GroupPlayer.Listener?) {
        this.listener = listener
    }
}

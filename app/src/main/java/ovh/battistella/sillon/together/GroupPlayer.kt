package ovh.battistella.sillon.together

import ovh.battistella.sillon.playback.PlayableTrack

interface GroupPlayer {
    val positionMs: Long
    val isPlaying: Boolean
    val isReady: Boolean
    val isBuffering: Boolean
    val currentIndex: Int
    val loadedIds: List<String>

    fun currentQueue(): Triple<List<String>, Int, Long>?

    fun enterGroupMode()
    fun exitGroupMode()

    fun load(tracks: List<PlayableTrack>, index: Int, positionMs: Long)
    fun jumpTo(index: Int, positionMs: Long)
    fun seek(positionMs: Long)
    fun play()
    fun pause()
    fun setSpeed(rate: Float)

    fun setListener(listener: Listener?)

    interface Listener {
        fun onReadyChanged(ready: Boolean)

        fun onTrackEnded()
    }
}

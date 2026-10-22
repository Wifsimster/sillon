package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.Side
import ovh.battistella.sillon.ui.components.Load
import ovh.battistella.sillon.ui.components.toFailed

data class DownloadStatus(val total: Int = 0, val done: Int = 0, val active: Int = 0, val failed: Int = 0) {
    val isComplete: Boolean get() = total > 0 && done == total
    val isDownloading: Boolean get() = active > 0
    val isStarted: Boolean get() = done + active + failed > 0

    companion object {
        fun of(keys: List<String>, downloads: List<DownloadEntity>): DownloadStatus {
            val byKey = downloads.associateBy { it.ratingKey }
            val states = keys.mapNotNull { byKey[it]?.state }
            return DownloadStatus(
                total = keys.size,
                done = states.count { it == DownloadState.DONE },
                active = states.count { it == DownloadState.QUEUED || it == DownloadState.DOWNLOADING },
                failed = states.count { it == DownloadState.FAILED },
            )
        }
    }
}

data class TrackCollection(val ui: AlbumUi, val tracks: List<PlexMetadata>, val fallbackArtworkUrl: String?)

abstract class TrackCollectionViewModel(
    private val repository: PlexRepository,
    private val playback: PlaybackConnection,
    private val downloads: DownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow<Load<AlbumUi>>(Load.Loading)
    val state = _state.asStateFlow()

    val nowPlaying = playback.state
    val ratings = playback.ratings
    val skipOneStar = playback.skipOneStar

    private val _downloadStatus = MutableStateFlow(DownloadStatus())
    val downloadStatus = _downloadStatus.asStateFlow()

    private val tracks = MutableStateFlow<List<PlexMetadata>>(emptyList())
    private var playable: List<PlayableTrack> = emptyList()

    protected abstract suspend fun fetch(): TrackCollection

    protected open suspend fun sidesForPlayback(): List<Side> = emptyList()

    protected fun start() {
        load()
        viewModelScope.launch {
            val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return@launch
            combine(tracks, downloads.observe(serverId)) { list, rows ->
                DownloadStatus.of(list.map { it.ratingKey }, rows)
            }.collect { _downloadStatus.value = it }
        }
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val collection = fetch()
                tracks.value = collection.tracks
                playable = repository.playable(collection.tracks, collection.fallbackArtworkUrl)
                Load.Ready(collection.ui)
            }.getOrElse { it.toFailed() }
        }
    }

    fun play(trackKey: String?) {
        val start = playable.indexOfFirst { it.ratingKey == trackKey }.coerceAtLeast(0)
        viewModelScope.launch {
            val fresh = runCatching { repository.playable(tracks.value) }.getOrDefault(playable)
            playback.play(fresh.ifEmpty { playable }, start, albumSides = sidesForPlayback(), pickStart = trackKey == null)
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            val fresh = runCatching { repository.playable(tracks.value) }.getOrDefault(playable)
            playback.play(fresh.ifEmpty { playable }, shuffle = true)
        }
    }

    fun download() {
        viewModelScope.launch { runCatching { downloads.download(tracks.value) } }
    }

    fun removeDownload() {
        viewModelScope.launch {
            runCatching { downloads.delete(repository.currentServerId(), tracks.value.map { it.ratingKey }) }
        }
    }
}

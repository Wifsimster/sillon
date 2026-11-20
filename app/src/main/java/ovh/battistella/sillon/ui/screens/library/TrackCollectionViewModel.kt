package ovh.battistella.sillon.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.sort.TrackOrder
import ovh.battistella.sillon.data.sort.decodeTrackOrder
import ovh.battistella.sillon.data.sort.sortedBy
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
    private val settings: SettingsStore? = null,
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

    /** Names this list in the saved sorts; null turns sorting off (e.g. the downloads view). */
    protected open val sortList: String? = null

    private val _order = MutableStateFlow<TrackOrder?>(null)

    /** The sort picked for this list, remembered between visits. */
    val order = _order.asStateFlow()

    fun setOrder(order: TrackOrder?) {
        _order.value = order
        val list = sortList ?: return
        viewModelScope.launch { settings?.setTrackSort(list, order?.encode()) }
    }

    /** Track keys in the order shown, or null when the list is not sorted. */
    private fun shownOrder(): List<String>? {
        val order = _order.value ?: return null
        val ui = (_state.value as? Load.Ready)?.value ?: return null
        val book = playback.ratings.value.book
        return ui.tracks
            .map { it.copy(stars = book.ratingOf(it.key, it.stars?.times(2f))?.div(2f)) }
            .sortedBy(order)
            .map { it.key }
    }

    private fun List<PlayableTrack>.inOrder(keys: List<String>?): List<PlayableTrack> {
        if (keys == null) return this
        val rank = HashMap<String, Int>(keys.size)
        keys.forEachIndexed { i, key -> rank.putIfAbsent(key, i) }
        return sortedBy { rank[it.ratingKey] ?: Int.MAX_VALUE }
    }

    protected open suspend fun sidesForPlayback(): List<Side> = emptyList()

    protected fun start() {
        val list = sortList
        if (list != null && settings != null) {
            viewModelScope.launch { _order.value = decodeTrackOrder(settings.trackSort(list).first()) }
        }
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

    /** Plays from [trackKey] (or the top), in the order shown on screen when the list is sorted. */
    fun play(trackKey: String?) {
        val keys = shownOrder()
        viewModelScope.launch {
            val fresh = runCatching { repository.playable(tracks.value) }.getOrDefault(playable)
            val list = fresh.ifEmpty { playable }.inOrder(keys)
            val start = list.indexOfFirst { it.ratingKey == trackKey }.coerceAtLeast(0)
            // Sides follow the album's own order, so they only apply while the list is not sorted.
            val sides = if (keys == null) sidesForPlayback() else emptyList()
            playback.play(list, start, albumSides = sides, pickStart = trackKey == null)
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

    /** What "Play next" and "Add to queue" take: the track touched, or the whole list when [trackKey] is null. */
    suspend fun queueable(trackKey: String?): List<PlayableTrack> {
        val fresh = runCatching { repository.playable(tracks.value) }.getOrDefault(playable).ifEmpty { playable }
        return if (trackKey == null) fresh else fresh.filter { it.ratingKey == trackKey }
    }
}

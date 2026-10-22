package ovh.battistella.sillon.ui.screens.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.battistella.sillon.data.rating.withoutOneStarDownloads
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.repository.LibraryShuffle
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.settings.FiveStarsCache
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlaybackConnection
import javax.inject.Inject

enum class ListenNotice { SHUFFLE_FROM_DOWNLOADS, FIVE_STARS_FROM_DOWNLOADS, NO_FIVE_STARS, FAILED }

@HiltViewModel
class ListenViewModel @Inject constructor(
    private val repository: PlexRepository,
    private val downloads: DownloadManager,
    private val playback: PlaybackConnection,
    private val settings: SettingsStore,
    private val fiveStarsCache: FiveStarsCache,
    network: NetworkMonitor,
) : ViewModel() {
    val online: StateFlow<Boolean> = network.online.stateIn(viewModelScope, SharingStarted.Eagerly, network.isOnline())

    val isJellyfin: StateFlow<Boolean> = repository.isJellyfin.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val fiveStarKeys = MutableStateFlow<Set<String>>(emptySet())

    /** What Shuffle and 5 stars can fall back on without a connection. */
    val offline: StateFlow<OfflineMusic> = combine(rows, fiveStarKeys) { all, keys -> OfflineMusic.of(all, keys) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, OfflineMusic())

    private val _starting = MutableStateFlow<ListenTile?>(null)
    val starting = _starting.asStateFlow()

    private val _notice = MutableStateFlow<ListenNotice?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return@launch
            launch { downloads.observe(serverId).collect { rows.value = it } }
            launch { fiveStarsCache.observe(serverId).collect { fiveStarKeys.value = it?.keys.orEmpty() } }
            val cached = fiveStarsCache.observe(serverId).first()
            if (online.value && FiveStarsCache.isStale(cached, System.currentTimeMillis())) {
                runCatching { repository.fiveStarTracks() }.getOrNull()?.let { tracks ->
                    fiveStarsCache.save(serverId, tracks.map { it.ratingKey })
                }
            }
        }
    }

    fun shuffle() = start(ListenTile.SHUFFLE) {
        val fromServer = if (online.value) {
            serverOrNull { repository.playable(repository.libraryShuffleTracks()).takeIf { it.isNotEmpty() } }
        } else {
            null
        }
        if (fromServer != null) {
            playback.play(fromServer)
            return@start null
        }
        if (playDownloads { true }) ListenNotice.SHUFFLE_FROM_DOWNLOADS else ListenNotice.FAILED
    }

    fun fiveStars() = start(ListenTile.FIVE_STARS) {
        val fromServer = if (online.value) {
            serverOrNull {
                val tracks = repository.fiveStarTracks()
                runCatching { fiveStarsCache.save(repository.currentServerId(), tracks.map { it.ratingKey }) }
                repository.playable(tracks)
            }
        } else {
            null
        }
        when {
            fromServer == null -> {
                val keys = fiveStarKeys.value
                if (playDownloads { it.ratingKey in keys }) ListenNotice.FIVE_STARS_FROM_DOWNLOADS else ListenNotice.FAILED
            }
            fromServer.isEmpty() -> ListenNotice.NO_FIVE_STARS
            else -> {
                playback.play(fromServer, shuffle = true)
                null
            }
        }
    }

    /** Plays the downloaded tracks that pass [keep], in a new order; false when there are none. */
    private suspend fun playDownloads(keep: (DownloadEntity) -> Boolean): Boolean {
        val mode = settings.loudness.first()
        val ratings = repository.ratingBook()
        val local = withContext(Dispatchers.IO) {
            LibraryShuffle.offline(ratings.withoutOneStarDownloads(rows.value.filter(keep)))
        }.map { it.toPlayable(mode) }
        if (local.isEmpty()) return false
        playback.play(local)
        return true
    }

    private suspend fun <T> serverOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun start(tile: ListenTile, block: suspend () -> ListenNotice?) {
        if (_starting.value != null) return
        _starting.value = tile
        _notice.value = null
        viewModelScope.launch {
            _notice.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ListenNotice.FAILED
            }
            _starting.value = null
        }
    }
}

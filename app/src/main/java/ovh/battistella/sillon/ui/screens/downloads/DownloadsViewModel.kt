package ovh.battistella.sillon.ui.screens.downloads

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.ui.navigation.Routes
import ovh.battistella.sillon.ui.screens.library.AlbumUi
import ovh.battistella.sillon.ui.screens.library.DownloadStatus
import ovh.battistella.sillon.ui.screens.library.TrackUi
import java.io.File
import javax.inject.Inject

/** One downloaded (or downloading) album in the Downloads tab. */
data class DownloadedAlbum(
    val albumKey: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val status: DownloadStatus,
    val sizeBytes: Long,
)

/** Key under which tracks without an album are grouped. */
private const val NO_ALBUM = "_"

private fun artworkUri(rows: List<DownloadEntity>): String? =
    rows.firstNotNullOfOrNull { it.artworkPath }?.let { Uri.fromFile(File(it)).toString() }

/**
 * The Downloads tab: everything on the phone, grouped by album. Reads only the
 * local database, so it works without any connection.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: PlexRepository,
    private val downloads: DownloadManager,
    settings: SettingsStore,
) : ViewModel() {

    private val _albums = MutableStateFlow<List<DownloadedAlbum>?>(null)
    /** Null while loading. */
    val albums = _albums.asStateFlow()

    val wifiOnly: StateFlow<Boolean> = settings.downloadOnWifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            val serverId = runCatching { repository.currentServerId() }.getOrNull()
            if (serverId == null) {
                _albums.value = emptyList()
                return@launch
            }
            downloads.observe(serverId).collect { rows ->
                _albums.value = rows.groupBy { it.albumKey ?: NO_ALBUM }.map { (key, tracks) ->
                    DownloadedAlbum(
                        albumKey = key,
                        title = tracks.first().album.orEmpty(),
                        artist = tracks.first().albumArtist,
                        artworkUri = artworkUri(tracks),
                        status = DownloadStatus.of(tracks.map { it.ratingKey }, tracks),
                        sizeBytes = tracks.sumOf { it.sizeBytes },
                    )
                }
            }
        }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { downloads.setWifiOnly(value) }
    }

    fun retryFailed() {
        viewModelScope.launch { downloads.retryFailed() }
    }
}

/** A downloaded album, played from the phone. */
@HiltViewModel
class DownloadedAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlexRepository,
    private val downloads: DownloadManager,
    private val playback: PlaybackConnection,
    private val settings: SettingsStore,
) : ViewModel() {

    private val albumKey: String = checkNotNull(savedStateHandle[Routes.ARG_KEY])

    private val _state = MutableStateFlow<AlbumUi?>(null)
    val state = _state.asStateFlow()

    val nowPlaying = playback.state

    private var rows: List<DownloadEntity> = emptyList()
    private var serverId: String? = null

    init {
        viewModelScope.launch {
            val id = runCatching { repository.currentServerId() }.getOrNull() ?: return@launch
            serverId = id
            downloads.observe(id).collect { all ->
                rows = all.filter { (it.albumKey ?: NO_ALBUM) == albumKey }
                val first = rows.firstOrNull()
                _state.value = AlbumUi(
                    title = first?.album.orEmpty(),
                    artist = first?.albumArtist,
                    year = null,
                    artworkUrl = artworkUri(rows),
                    tracks = rows.map {
                        TrackUi(it.ratingKey, it.trackNumber, it.title, it.artist.takeIf { a -> a != it.albumArtist }, it.durationMs)
                    },
                )
            }
        }
    }

    private suspend fun playable(): List<ovh.battistella.sillon.playback.PlayableTrack> {
        val mode = settings.loudness.first()
        return rows.filter { it.isPlayableOffline() }.map { it.toPlayable(mode) }
    }

    fun play(trackKey: String?) {
        viewModelScope.launch {
            val tracks = playable()
            playback.play(tracks, tracks.indexOfFirst { it.ratingKey == trackKey }.coerceAtLeast(0))
        }
    }

    fun shuffle() {
        viewModelScope.launch { playback.play(playable(), shuffle = true) }
    }

    /** Remove the whole album from the phone. */
    fun remove() {
        val id = serverId ?: return
        viewModelScope.launch { downloads.delete(id, rows.map { it.ratingKey }) }
    }

    /** Tracks still downloading or failed can't be played yet. */
    fun isReady(key: String): Boolean = rows.firstOrNull { it.ratingKey == key }?.state == DownloadState.DONE
}

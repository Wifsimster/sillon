package ovh.battistella.sillon.playback.mystery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.together.ListenTogether
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class MysteryProblem { NO_MUSIC, NOTHING_TO_DRAW, NOTHING_DOWNLOADED, LISTENING_TOGETHER, FAILED }

enum class KeepResult { KEPT, ALREADY_KEPT, OFFLINE, DENIED, FAILED }

const val SURPRISES_PLAYLIST = "Surprises"

private val Context.mysteryDataStore: DataStore<Preferences> by preferencesDataStore(name = "mystery")

private val ANY_ALBUM = booleanPreferencesKey("any_album")

private const val MAX_DRAWS = 5

@Singleton
class MysteryLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlexRepository,
    private val downloads: DownloadDao,
    private val settings: SettingsStore,
    private val playback: PlaybackConnection,
    private val session: MysterySession,
    private val art: SleeveArt,
    private val together: ListenTogether,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _starting = MutableStateFlow(false)
    val starting = _starting.asStateFlow()

    private val _problem = MutableStateFlow<MysteryProblem?>(null)
    val problem = _problem.asStateFlow()

    private val _keep = MutableStateFlow<KeepResult?>(null)
    val keep = _keep.asStateFlow()

    private val _keeping = MutableStateFlow(false)
    val keeping = _keeping.asStateFlow()

    val anyAlbum: StateFlow<Boolean> = context.mysteryDataStore.data.map { it[ANY_ALBUM] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun setAnyAlbum(value: Boolean) {
        scope.launch { context.mysteryDataStore.edit { it[ANY_ALBUM] = value } }
    }

    fun start() {
        if (_starting.value) return
        if (together.state.value.isActive) {
            _problem.value = MysteryProblem.LISTENING_TOGETHER
            return
        }
        _starting.value = true
        _problem.value = null
        val previous = session.state.value?.albumKey
        scope.launch {
            runCatching { draw(anyAlbum.value, setOfNotNull(previous)) }
                .onSuccess { mystery ->
                    val labels = labels()
                    _keep.value = null
                    session.start(mystery)
                    playback.playItems(mystery.tracks.map { MysteryMask.mask(it, labels) })
                }
                .onFailure { _problem.value = (it as? Problem)?.kind ?: MysteryProblem.FAILED }
            _starting.value = false
        }
    }

    fun keep() {
        val mystery = session.state.value ?: return
        if (_keeping.value) return
        if (mystery.offline) {
            _keep.value = KeepResult.OFFLINE
            return
        }
        _keeping.value = true
        scope.launch {
            _keep.value = runCatching { repository.addAlbumToPlaylist(mystery.albumKey, SURPRISES_PLAYLIST) }.fold(
                onSuccess = { if (it) KeepResult.KEPT else KeepResult.ALREADY_KEPT },
                onFailure = {
                    when {
                        it is PlexHttpException && it.code in setOf(401, 403) -> KeepResult.DENIED
                        it is IOException && it !is PlexHttpException -> KeepResult.OFFLINE
                        else -> KeepResult.FAILED
                    }
                },
            )
            _keeping.value = false
        }
    }

    private class Problem(val kind: MysteryProblem) : Exception(kind.name)

    private suspend fun labels(): SleeveLabels = withContext(Dispatchers.Default) {
        SleeveLabels(
            track = { side, number -> context.getString(R.string.mystery_track_label, side, number) },
            album = context.getString(R.string.mystery_album),
            artworkUri = withContext(Dispatchers.IO) { art.uri },
            artworkData = art.png,
        )
    }

    private suspend fun draw(anyAlbum: Boolean, exclude: Set<String>): Mystery {
        if (!isOnline()) return drawOffline(anyAlbum, exclude)
        return try {
            drawOnline(anyAlbum, exclude)
        } catch (e: IOException) {
            if (e is PlexHttpException) throw e
            drawOffline(anyAlbum, exclude)
        }
    }

    private suspend fun drawOnline(anyAlbum: Boolean, exclude: Set<String>): Mystery {
        val section = repository.currentMusicSection() ?: throw Problem(MysteryProblem.NO_MUSIC)
        val albums = repository.albums(section.key).map { it.toAlbumCandidate() }
        val skipped = exclude.toMutableSet()
        repeat(MAX_DRAWS) {
            val album = MysteryDraw.pick(albums, anyAlbum, exclude = skipped) ?: throw Problem(MysteryProblem.NOTHING_TO_DRAW)
            val tracks = repository.tracksOf(album.key)
                .filter { it.type.isEmpty() || it.type == "track" }
                .sortedWith(compareBy({ it.parentIndex ?: 1 }, { it.index ?: Int.MAX_VALUE }))
            val playable = repository.playable(tracks)
            if (playable.size >= MysteryDraw.MIN_TRACKS) {
                val byKey = tracks.associateBy { it.ratingKey }
                return Mystery(
                    albumKey = album.key,
                    tracks = MysteryMask.sides(
                        playable.map { it to TrackPosition(byKey[it.ratingKey]?.parentIndex, byKey[it.ratingKey]?.index) },
                    ),
                )
            }
            skipped += album.key
            if (albums.none { it.key !in skipped }) throw Problem(MysteryProblem.NOTHING_TO_DRAW)
        }
        throw Problem(MysteryProblem.NOTHING_TO_DRAW)
    }

    private suspend fun drawOffline(anyAlbum: Boolean, exclude: Set<String>): Mystery {
        val serverId = runCatching { repository.currentServerId() }.getOrNull()
            ?: throw Problem(MysteryProblem.NOTHING_DOWNLOADED)
        val mode = settings.loudness.first()
        val albums = withContext(Dispatchers.IO) {
            downloads.observe(serverId).first()
                .filter { it.albumKey != null && it.isPlayableOffline() }
                .groupBy { it.albumKey!! }
        }
        val candidates = albums.map { (key, rows) -> AlbumCandidate(key, rows.size, 0) }
        val album = MysteryDraw.pick(candidates, anyAlbum, exclude = exclude)
            ?: throw Problem(MysteryProblem.NOTHING_DOWNLOADED)
        val rows = albums.getValue(album.key).sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: Int.MAX_VALUE }))
        return Mystery(
            albumKey = album.key,
            tracks = MysteryMask.sides(rows.map { it.toPlayable(mode) to TrackPosition(it.discNumber, it.trackNumber) }),
            offline = true,
        )
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

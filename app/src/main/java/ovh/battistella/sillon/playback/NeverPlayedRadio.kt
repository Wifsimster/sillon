package ovh.battistella.sillon.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.rating.withoutOneStarDownloads
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.repository.NeverPlayedMode
import ovh.battistella.sillon.data.repository.NeverPlayedSession
import ovh.battistella.sillon.data.repository.PLAYER_ART_PX
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.pickOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.settings.CachedLibraryPlays
import ovh.battistella.sillon.data.settings.NeverPlayedCache
import ovh.battistella.sillon.data.settings.OfflineHistory
import ovh.battistella.sillon.data.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RadioStart {
    data class Ready(val tracks: List<PlayableTrack>, val offline: Boolean) : RadioStart
    data object Exhausted : RadioStart
    data object NoOfflineHistory : RadioStart
}

@Singleton
class NeverPlayedRadio @Inject constructor(
    private val repository: PlexRepository,
    private val downloads: DownloadDao,
    private val settings: SettingsStore,
    private val cache: NeverPlayedCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val session = NeverPlayedSession()
    private var serverId: String? = null

    @Volatile var mode: NeverPlayedMode? = null
        private set

    @Volatile private var offline = false

    val isActive: Boolean get() = mode != null

    fun owns(key: String): Boolean = session.owns(key)

    fun stop() {
        mode = null
    }

    suspend fun start(mode: NeverPlayedMode): RadioStart = lock.withLock {
        val server = repository.currentServerId()
        if (server != serverId) {
            session.reset()
            serverId = server
        }
        session.begin()
        this.mode = null
        val online = try {
            onlineBatch(mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        offline = online == null
        val tracks = online ?: offlineBatch(server, mode) ?: return@withLock RadioStart.NoOfflineHistory
        if (tracks.isEmpty()) return@withLock RadioStart.Exhausted
        this.mode = mode
        if (!offline) scope.launch { runCatching { refreshHistory(server) } }
        RadioStart.Ready(tracks, offline)
    }

    suspend fun more(): List<PlayableTrack> = lock.withLock {
        val mode = mode ?: return@withLock emptyList()
        val server = serverId ?: return@withLock emptyList()
        if (!offline) {
            val online = try {
                onlineBatch(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (online != null) return@withLock online
            offline = true
        }
        offlineBatch(server, mode).orEmpty()
    }

    fun onLeft(key: String, listenedMs: Long, finished: Boolean, skipped: Boolean) {
        val outcome = session.leave(key, listenedMs, skipped = skipped && !finished)
        val server = serverId
        if (finished && outcome == NeverPlayedSession.Outcome.PLAYED && server != null) {
            scope.launch { runCatching { cache.markPlayed(server, key) } }
        }
    }

    suspend fun cachedPlays(): CachedLibraryPlays? =
        cache.plays(repository.currentServerId(), sectionCacheKey())

    suspend fun refreshPlays(): CachedLibraryPlays? {
        val server = repository.currentServerId()
        val section = sectionCacheKey()
        val plays = repository.libraryPlays() ?: return cache.plays(server, section)
        val cached = CachedLibraryPlays(plays.neverPlayed, plays.total, System.currentTimeMillis())
        cache.savePlays(server, section, cached)
        scope.launch { runCatching { refreshHistory(server) } }
        return cached
    }

    private suspend fun sectionCacheKey(): String = repository.musicSectionKey.first() ?: DEFAULT_SECTION

    private suspend fun onlineBatch(mode: NeverPlayedMode): List<PlayableTrack> = when (mode) {
        NeverPlayedMode.TRACKS -> {
            var tracks = emptyList<PlayableTrack>()
            for (attempt in 0 until DRAWS) {
                val fresh = session.take(repository.neverPlayedTracks())
                if (fresh.isNotEmpty()) {
                    tracks = repository.playable(fresh)
                    if (tracks.isNotEmpty()) break
                }
            }
            tracks
        }
        NeverPlayedMode.ALBUMS -> {
            var tracks = emptyList<PlayableTrack>()
            draws@ for (attempt in 0 until DRAWS) {
                for (album in repository.neverPlayedAlbums()) {
                    if (session.isAlbumQueued(album.ratingKey)) continue
                    val albumTracks = repository.withoutOneStar(repository.tracksOf(album.ratingKey))
                        .filter { it.type.isEmpty() || it.type == "track" }
                        .sortedWith(compareBy({ it.parentIndex ?: 0 }, { it.index ?: 0 }))
                    val fresh = session.takeAlbum(album.ratingKey, albumTracks) ?: continue
                    val artwork = repository.artworkUrl(repository.endpoint(), album.artwork, PLAYER_ART_PX)
                    tracks = repository.playable(fresh, artwork)
                    if (tracks.isNotEmpty()) break@draws
                }
            }
            tracks
        }
    }

    private suspend fun offlineBatch(server: String, mode: NeverPlayedMode): List<PlayableTrack>? {
        val history = cache.history(server) ?: return null
        val loudness = settings.loudness.first()
        val ratings = repository.ratingBook()
        val downloaded = ratings.withoutOneStarDownloads(downloads.observe(server).first().filter { it.isPlayableOffline() })
        return pickOffline(downloaded, history.neverPlayed, session, mode).map { it.toPlayable(loudness) }
    }

    private suspend fun refreshHistory(server: String) {
        val keys = downloads.observe(server).first().filter { it.state == DownloadState.DONE }.map { it.ratingKey }
        val known = cache.history(server)
        val fresh = known != null && System.currentTimeMillis() - known.fetchedAt < HISTORY_TTL_MS && known.checked.containsAll(keys)
        if (fresh) return
        val counts = repository.playCounts(keys)
        cache.saveHistory(
            server,
            OfflineHistory(
                neverPlayed = counts.filterValues { it == 0 }.keys,
                checked = counts.keys,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val DRAWS = 3
        const val HISTORY_TTL_MS = 6 * 60 * 60_000L
        const val DEFAULT_SECTION = "default"
    }
}

package ovh.battistella.sillon.data.wear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SleeveWearRepository @Inject constructor(
    private val repository: PlexRepository,
    private val store: SleeveWearCache,
    settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = MutableStateFlow<CachedPlays?>(null)
    private val lock = Mutex()
    private var loading: Load? = null

    val levels: Flow<Map<String, Int>> = settings.sleeveWear.flatMapLatest { enabled ->
        if (!enabled) {
            flowOf(emptyMap())
        } else {
            repository.session
                .map { SleeveWearCache.ownerOf(it) }
                .distinctUntilChanged()
                .flatMapLatest { owner ->
                    if (owner == null) {
                        flowOf(emptyMap())
                    } else {
                        cache.onStart { ensureLoaded(owner) }
                            .map { c -> if (c?.owner == owner) SleeveWear.levels(c.albums) else emptyMap() }
                    }
                }
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** The album grid just loaded [albums]: count their plays, taking over a cold-cache load still running. */
    fun refresh(sectionKey: String, albums: List<PlexMetadata>) {
        load(full = false) {
            val owner = currentOwner() ?: return@load
            update(owner, sectionKey, albums)
        }
    }

    private suspend fun ensureLoaded(owner: String) {
        val section = repository.musicSectionKey.first()
        val fresh = lock.withLock {
            val current = cache.value
            if (current != null && current.owner == owner && (section == null || current.sectionKey == section)) {
                return@withLock true
            }
            val disk = store.read(owner)
            if (disk != null) cache.value = disk
            disk != null && (section == null || disk.sectionKey == section)
        }
        if (!fresh) {
            load(full = true) {
                val current = repository.currentMusicSection() ?: return@load
                update(owner, current.key, repository.albums(current.key))
            }
        }
    }

    /**
     * One load at a time. A full load (album list, then plays) is skipped while any load runs, and a
     * refresh from the album grid replaces a running full load, so a cold start fetches the library once.
     */
    private fun load(full: Boolean, block: suspend () -> Unit) = synchronized(this) {
        val running = loading?.takeIf { it.job.isActive }
        if (running != null && (full || !running.full)) return@synchronized
        running?.job?.cancel()
        loading = Load(scope.launch { runCatching { block() } }, full)
    }

    private suspend fun update(owner: String, sectionKey: String, albums: List<PlexMetadata>) {
        val plays = repository.albumPlays(sectionKey, albums)
        if (currentOwner() != owner) return
        val cached = CachedPlays(owner, sectionKey, plays)
        lock.withLock {
            cache.value = cached
            store.write(cached)
        }
    }

    private suspend fun currentOwner(): String? = SleeveWearCache.ownerOf(repository.session.first())

    private class Load(val job: Job, val full: Boolean)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

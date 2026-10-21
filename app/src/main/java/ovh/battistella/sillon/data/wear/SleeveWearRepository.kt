package ovh.battistella.sillon.data.wear

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.SettingsStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedPlays(val serverId: String, val sectionKey: String, val albums: Map<String, AlbumPlays>)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SleeveWearRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlexRepository,
    settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = MutableStateFlow<CachedPlays?>(null)
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val levels: Flow<Map<String, Int>> = settings.sleeveWear.flatMapLatest { enabled ->
        if (!enabled) {
            flowOf(emptyMap())
        } else {
            repository.session
                .map { (it as? Session.Ready)?.server?.machineId }
                .distinctUntilChanged()
                .flatMapLatest { serverId ->
                    if (serverId == null) {
                        flowOf(emptyMap())
                    } else {
                        cache.onStart { ensureLoaded(serverId) }
                            .map { c -> if (c?.serverId == serverId) SleeveWear.levels(c.albums) else emptyMap() }
                    }
                }
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    fun refresh(sectionKey: String, albums: List<PlexMetadata>) {
        scope.launch { runCatching { update(sectionKey, albums) } }
    }

    private suspend fun ensureLoaded(serverId: String) {
        val section = repository.musicSectionKey.first()
        val fresh = lock.withLock {
            val current = cache.value
            if (current != null && current.serverId == serverId && (section == null || current.sectionKey == section)) {
                return@withLock true
            }
            val disk = read(serverId)
            if (disk != null) cache.value = disk
            disk != null && (section == null || disk.sectionKey == section)
        }
        if (!fresh) {
            scope.launch {
                runCatching {
                    val current = repository.currentMusicSection() ?: return@runCatching
                    update(current.key, repository.albums(current.key))
                }
            }
        }
    }

    private suspend fun update(sectionKey: String, albums: List<PlexMetadata>) {
        val serverId = repository.currentServerId()
        val plays = repository.albumPlays(sectionKey, albums)
        val cached = CachedPlays(serverId, sectionKey, plays)
        lock.withLock {
            cache.value = cached
            write(cached)
        }
    }

    private suspend fun read(serverId: String): CachedPlays? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(CachedPlays.serializer(), file(serverId).readText()) }
            .getOrNull()
            ?.takeIf { it.serverId == serverId }
    }

    private suspend fun write(cached: CachedPlays) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(cached.serverId)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(json.encodeToString(CachedPlays.serializer(), cached))
            temp.renameTo(target)
        }
    }

    private fun file(serverId: String): File =
        File(File(context.noBackupFilesDir, "sleeve-wear"), serverId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

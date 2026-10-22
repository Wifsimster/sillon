package ovh.battistella.sillon.playback.cache

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ovh.battistella.sillon.data.settings.SettingsStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the user listens to, kept on the phone so the next listen skips the network. Apart from
 * the downloads: this one is bounded and evicts the least recently played first.
 */
@OptIn(UnstableApi::class)
@Singleton
class ListeningCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    private val dir = File(context.noBackupFilesDir, DIR)
    private val evictor = ListeningCacheEvictor { used -> CacheLimit.effective(chosenGb, used, freeBytes()) }
    private val cache: SimpleCache by lazy { SimpleCache(dir, evictor, StandaloneDatabaseProvider(context)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    @Volatile var enabled: Boolean = true
        private set

    @Volatile var chosenGb: Int = CacheLimit.DEFAULT_GB
        private set

    init {
        scope.launch {
            combine(settings.listeningCache, settings.listeningCacheGb, ::Pair).collect { (on, gb) ->
                enabled = on
                chosenGb = gb
                runCatching { if (on) applyLimit() else clear(sweepAgain = true) }
                    .onFailure { Log.w(TAG, "cache settings not applied", it) }
            }
        }
    }

    /** The player's data sources, with the original streams going through the cache. */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory {
        val cached by lazy {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return ListeningDataSource.Factory(upstream) { if (enabled) cached else null }
    }

    /** The track's original is in the cache from its first byte to its last. */
    suspend fun isWhole(key: String): Boolean = enabled && withContext(Dispatchers.IO) {
        // SimpleCache locks while it loads its index or clears: never on the main thread.
        runCatching { isWhole(cache, key) }.getOrDefault(false)
    }

    fun usedBytes(): Long = runCatching { cache.cacheSpace }.getOrDefault(0)

    /** Free space for the cache, counting what the system would clear from other apps' caches. */
    fun freeBytes(): Long {
        val path = dir.takeIf { it.exists() } ?: context.noBackupFilesDir
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(path)) }.getOrElse { path.usableSpace }
    }

    /** Re-reads the free space and trims the cache to what fits. */
    suspend fun applyLimit() = withContext(Dispatchers.IO) {
        lock.withLock { evictor.refresh(cache) }
    }

    /**
     * Empties the cache; the downloads stay. A track still being written can commit its last
     * few megabytes just after; with [sweepAgain] (cache turned off, server left) a second pass
     * a little later takes those too.
     */
    suspend fun clear(sweepAgain: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock { clearAll(cache) }
        if (sweepAgain) {
            scope.launch {
                delay(SWEEP_AGAIN_MS)
                runCatching { lock.withLock { clearAll(cache) } }
            }
        }
    }

    companion object {
        private const val TAG = "SillonCache"
        private const val DIR = "listening-cache"
        private const val SWEEP_AGAIN_MS = 30_000L

        fun isWhole(cache: Cache, key: String): Boolean {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            return length > 0 && cache.isCached(key, 0, length)
        }

        fun clearAll(cache: Cache) {
            cache.keys.toList().forEach(cache::removeResource)
        }
    }
}

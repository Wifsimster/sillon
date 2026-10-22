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
    private val evictor = ListeningCacheEvictor(CacheLimit.bytes(CacheLimit.DEFAULT_GB))
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
                runCatching { if (on) applyLimit() else clear() }
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
    fun isWhole(key: String): Boolean = enabled && runCatching { isWhole(cache, key) }.getOrDefault(false)

    fun usedBytes(): Long = runCatching { cache.cacheSpace }.getOrDefault(0)

    /** Free space for the cache, counting what the system would clear from other apps' caches. */
    fun freeBytes(): Long {
        val path = dir.takeIf { it.exists() } ?: context.noBackupFilesDir
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(path)) }.getOrElse { path.usableSpace }
    }

    /** Re-reads the free space and trims the cache to what fits. */
    suspend fun applyLimit() = withContext(Dispatchers.IO) {
        lock.withLock {
            evictor.resize(cache, CacheLimit.effective(chosenGb, cache.cacheSpace, freeBytes()))
        }
    }

    /** Empties the cache; the downloads stay. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock { clearAll(cache) }
    }

    companion object {
        private const val TAG = "SillonCache"
        private const val DIR = "listening-cache"

        fun isWhole(cache: Cache, key: String): Boolean {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            return length > 0 && cache.isCached(key, 0, length)
        }

        fun clearAll(cache: Cache) {
            cache.keys.toList().forEach(cache::removeResource)
        }
    }
}

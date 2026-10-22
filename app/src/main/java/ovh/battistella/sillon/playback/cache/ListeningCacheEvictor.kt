package ovh.battistella.sillon.playback.cache

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Least recently used eviction, like Media3's own evictor, with a limit that moves while the
 * cache is open: the size is a setting, and the free space on the phone shrinks and grows.
 * [limit] turns the bytes the cache holds into the most it may hold; it is read again before
 * every new file the cache writes, so a phone filling up holds the cache back mid-listen.
 */
@OptIn(UnstableApi::class)
class ListeningCacheEvictor(private val limit: (usedBytes: Long) -> Long) : CacheEvictor {
    private val spans = TreeSet<CacheSpan> { a, b ->
        if (a.lastTouchTimestamp != b.lastTouchTimestamp) a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp) else a.compareTo(b)
    }
    private var size = 0L

    @Volatile var maxBytes: Long = Long.MAX_VALUE
        private set

    /** Reads the limit again and evicts at once what no longer fits. */
    fun refresh(cache: Cache) {
        // SimpleCache calls the evictor under its own lock; take the same one.
        synchronized(cache) {
            maxBytes = limit(size)
            evict(cache, 0)
        }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        maxBytes = limit(size)
        evict(cache, if (length == C.LENGTH_UNSET.toLong()) 0 else length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        size += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans.remove(span)
        size -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, requiredSpace: Long) {
        while (size + requiredSpace > maxBytes && spans.isNotEmpty()) {
            cache.removeSpan(spans.first())
        }
    }
}

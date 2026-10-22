package ovh.battistella.sillon.playback.cache

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Least recently used eviction, like Media3's own evictor, with a limit that can change while
 * the cache is open: the size is a setting, and Media3 fixes its evictor's size at creation.
 */
@OptIn(UnstableApi::class)
class ListeningCacheEvictor(maxBytes: Long) : CacheEvictor {
    private val spans = TreeSet<CacheSpan> { a, b ->
        if (a.lastTouchTimestamp != b.lastTouchTimestamp) a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp) else a.compareTo(b)
    }
    private var size = 0L

    @Volatile var maxBytes: Long = maxBytes
        private set

    /** Applies a new limit and evicts at once what no longer fits. */
    fun resize(cache: Cache, bytes: Long) {
        // SimpleCache calls the evictor under its own lock; take the same one.
        synchronized(cache) {
            maxBytes = bytes
            evict(cache, 0)
        }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
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

package ovh.battistella.sillon.playback.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends a track's original stream and the transcodes the cache knows of through the listening
 * cache, and everything else (downloaded files, other media) straight to [upstream]. Only a
 * request carrying a [ListeningCacheKeys] key, or one [keyFor] names, goes to the cache, so a
 * download is never cached twice. [cached] returns null while the cache is off. [onClosed] hears
 * of every cached read that ends.
 */
@OptIn(UnstableApi::class)
class ListeningDataSource(
    private val upstream: DataSource,
    private val keyFor: (url: String) -> String? = { null },
    private val onClosed: (key: String) -> Unit = {},
    private val cached: () -> DataSource?,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var current: DataSource? = null
    private var currentKey: String? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val cache = cached()
        val key = when {
            cache == null -> null
            ListeningCacheKeys.isTrack(dataSpec.key) -> dataSpec.key
            else -> keyFor(dataSpec.uri.toString())
        }
        if (cache == null || key == null) {
            current = upstream
            return upstream.open(dataSpec)
        }
        listeners.forEach(cache::addTransferListener)
        current = cache
        currentKey = key
        return cache.open(if (key == dataSpec.key) dataSpec else dataSpec.buildUpon().setKey(key).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        val key = currentKey
        try {
            current?.close()
        } finally {
            current = null
            currentKey = null
        }
        key?.let(onClosed)
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val keyFor: (url: String) -> String?,
        private val onClosed: (key: String) -> Unit,
        private val cached: () -> DataSource.Factory?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ListeningDataSource(upstream.createDataSource(), keyFor, onClosed) { cached()?.createDataSource() }
    }
}

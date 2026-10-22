package ovh.battistella.sillon.playback.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends a track's original stream through the listening cache and everything else (downloaded
 * files, transcode playlists and segments, other media) straight to [upstream]. Only a request
 * carrying a [ListeningCacheKeys] key goes to the cache, so a download is never cached twice.
 * [cached] returns null while the cache is off.
 */
@OptIn(UnstableApi::class)
class ListeningDataSource(
    private val upstream: DataSource,
    private val cached: () -> DataSource?,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = (if (ListeningCacheKeys.isTrack(dataSpec.key)) cached() else null)
            ?.also { source -> listeners.forEach(source::addTransferListener) }
            ?: upstream
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val cached: () -> DataSource.Factory?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ListeningDataSource(upstream.createDataSource()) { cached()?.createDataSource() }
    }
}

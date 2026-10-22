package ovh.battistella.sillon.playback.cache

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListeningDataSourceTest {
    private class Recording : DataSource {
        var opened: DataSpec? = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = 0L.also { opened = dataSpec }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = opened?.uri
        override fun close() = Unit
    }

    private val network = Recording()
    private val cache = Recording()

    private fun source(cacheOn: Boolean = true) = ListeningDataSource(network) { cache.takeIf { cacheOn } }

    private fun spec(uri: String, key: String? = null) = DataSpec.Builder().setUri(Uri.parse(uri)).setKey(key).build()

    private val trackKey = ListeningCacheKeys.track("m1", "1")

    @Test
    fun `a downloaded file is read directly and never cached again`() {
        source().open(spec("file:///data/music/1.flac"))
        assertEquals("file:///data/music/1.flac", network.opened?.uri.toString())
        assertNull(cache.opened)
    }

    @Test
    fun `an original stream goes through the cache`() {
        val spec = spec("https://server/library/parts/1/file.flac?X-Plex-Token=t", trackKey)
        source().open(spec)
        assertSame(spec, cache.opened)
        assertNull(network.opened)
    }

    @Test
    fun `transcode segments go to the network`() {
        source().open(spec("https://server/video/:/transcode/universal/session/s/base/00001.ts"))
        assertNull(cache.opened)
    }

    @Test
    fun `with the cache off, everything goes to the network`() {
        source(cacheOn = false).open(spec("https://server/library/parts/1/file.flac", trackKey))
        assertNull(cache.opened)
        assertEquals(trackKey, network.opened?.key)
    }
}

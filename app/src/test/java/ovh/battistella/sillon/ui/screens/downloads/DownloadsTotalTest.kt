package ovh.battistella.sillon.ui.screens.downloads

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState

class DownloadsTotalTest {
    private fun row(key: String, albumKey: String?, sizeBytes: Long, state: DownloadState = DownloadState.DONE) = DownloadEntity(
        serverId = "s", ratingKey = key, title = key, artist = null, album = null, albumKey = albumKey,
        albumArtist = null, trackNumber = null, discNumber = null, durationMs = null,
        partKey = "/library/parts/$key/1/f.flac", thumb = null, state = state, sizeBytes = sizeBytes,
    )

    @Test
    fun `nothing downloaded`() {
        assertEquals(DownloadsTotal.NONE, DownloadsTotal.of(emptyList()))
    }

    @Test
    fun `counts albums, not tracks, and adds up their size`() {
        val total = DownloadsTotal.of(
            listOf(
                row("1", "a", 1_000),
                row("2", "a", 2_000),
                row("3", "b", 3_000),
                row("4", "b", 0, DownloadState.QUEUED),
            ),
        )
        assertEquals(DownloadsTotal(albums = 2, bytes = 6_000), total)
    }

    @Test
    fun `tracks without an album count as one album, as on the downloads screen`() {
        val total = DownloadsTotal.of(listOf(row("1", null, 10), row("2", null, 20), row("3", "a", 30)))
        assertEquals(DownloadsTotal(albums = 2, bytes = 60), total)
    }
}

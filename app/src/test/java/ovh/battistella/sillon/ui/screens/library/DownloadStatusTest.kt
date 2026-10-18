package ovh.battistella.sillon.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState

class DownloadStatusTest {
    private fun row(key: String, state: DownloadState) = DownloadEntity(
        serverId = "s", ratingKey = key, title = key, artist = null, album = null, albumKey = "a",
        albumArtist = null, trackNumber = null, discNumber = null, durationMs = null,
        partKey = "/library/parts/$key/1/f.flac", thumb = null, state = state,
    )

    @Test
    fun `counts the album's tracks by download state`() {
        val status = DownloadStatus.of(
            keys = listOf("1", "2", "3", "4"),
            downloads = listOf(
                row("1", DownloadState.DONE),
                row("2", DownloadState.DOWNLOADING),
                row("3", DownloadState.FAILED),
                row("99", DownloadState.DONE),
            ),
        )
        assertEquals(DownloadStatus(total = 4, done = 1, active = 1, failed = 1), status)
        assertTrue(status.isDownloading)
        assertFalse(status.isComplete)
    }

    @Test
    fun `complete once every track is downloaded`() {
        val status = DownloadStatus.of(listOf("1", "2"), listOf(row("1", DownloadState.DONE), row("2", DownloadState.DONE)))
        assertTrue(status.isComplete)
        assertFalse(status.isDownloading)
    }

    @Test
    fun `nothing downloaded`() {
        val status = DownloadStatus.of(listOf("1"), emptyList())
        assertFalse(status.isStarted)
        assertFalse(status.isComplete)
    }
}

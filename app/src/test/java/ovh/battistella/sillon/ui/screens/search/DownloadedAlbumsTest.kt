package ovh.battistella.sillon.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState

class DownloadedAlbumsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun row(key: String, album: String?, state: DownloadState = DownloadState.DONE, file: Boolean = true, art: Boolean = false) =
        DownloadEntity(
            serverId = "s",
            ratingKey = key,
            title = key,
            artist = null,
            album = album,
            albumKey = album,
            albumArtist = null,
            trackNumber = null,
            discNumber = null,
            durationMs = null,
            partKey = "/p/$key",
            thumb = null,
            state = state,
            filePath = if (file) folder.newFile("$key.flac").path else null,
            artworkPath = if (art) folder.newFile("$key.jpg").path else null,
        )

    @Test
    fun `only albums with a track playable offline count, with their saved cover`() {
        val albums = downloadedAlbums(
            listOf(
                row("t1", "a"),
                row("t2", "a", art = true),
                row("t3", "b", state = DownloadState.QUEUED, file = false),
                row("t4", null),
                row("t5", "c", file = false),
            ),
        )
        assertEquals(setOf("a"), albums.keys)
        assertEquals(true, albums["a"]?.endsWith("t2.jpg"))
    }
}

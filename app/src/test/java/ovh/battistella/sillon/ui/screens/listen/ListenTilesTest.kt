package ovh.battistella.sillon.ui.screens.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.repository.LibraryShuffle
import kotlin.random.Random

class ListenTilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `online, every tile plays from the server`() {
        ListenTile.entries.forEach { assertEquals(it.name, TileState.READY, ListenTiles.state(it, online = true, OfflineMusic())) }
    }

    @Test
    fun `offline with nothing downloaded, no tile can play`() {
        ListenTile.entries.forEach {
            val state = ListenTiles.state(it, online = false, OfflineMusic())
            assertEquals(it.name, TileState.NOTHING_DOWNLOADED, state)
            assertFalse(ListenTiles.enabled(state))
        }
    }

    @Test
    fun `offline, shuffle and 5 stars fall back to the downloads, the others handle it themselves`() {
        val some = OfflineMusic(tracks = 10, fiveStars = 2)
        assertEquals(TileState.FROM_DOWNLOADS, ListenTiles.state(ListenTile.SHUFFLE, online = false, some))
        assertEquals(TileState.FROM_DOWNLOADS, ListenTiles.state(ListenTile.FIVE_STARS, online = false, some))
        listOf(ListenTile.NEVER_PLAYED, ListenTile.NEVER_PLAYED_ALBUMS, ListenTile.MYSTERY)
            .forEach { assertEquals(it.name, TileState.READY, ListenTiles.state(it, online = false, some)) }
        ListenTile.entries.forEach { assertTrue(it.name, ListenTiles.enabled(ListenTiles.state(it, online = false, some))) }
    }

    @Test
    fun `offline with no 5-star track downloaded, 5 stars needs the server`() {
        val state = ListenTiles.state(ListenTile.FIVE_STARS, online = false, OfflineMusic(tracks = 10, fiveStars = 0))
        assertEquals(TileState.NEEDS_SERVER, state)
        assertFalse(ListenTiles.enabled(state))
    }

    @Test
    fun `counts only the finished downloads on disk, and the 5-star ones among them`() {
        val rows = listOf(
            row("1", onDisk = true),
            row("2", onDisk = true),
            row("3", onDisk = false),
            row("4", onDisk = true, state = DownloadState.DOWNLOADING),
        )
        assertEquals(OfflineMusic(tracks = 2, fiveStars = 1), OfflineMusic.of(rows, setOf("2", "3", "4")))
    }

    @Test
    fun `asks Plex for 100 random tracks of the whole library`() {
        val query = LibraryShuffle.plexTracks().toMap()
        assertEquals("10", query["type"])
        assertEquals("random", query["sort"])
        assertEquals("0", query["X-Plex-Container-Start"])
        assertEquals("100", query["X-Plex-Container-Size"])
    }

    @Test
    fun `the offline shuffle keeps every playable download, in a new order`() {
        val rows = (1..20).map { row("$it", onDisk = true) } + row("missing", onDisk = false)
        val shuffled = LibraryShuffle.offline(rows, Random(7))
        assertEquals((1..20).map { "$it" }.toSet(), shuffled.map { it.ratingKey }.toSet())
        assertEquals(20, shuffled.size)
        assertTrue(shuffled.map { it.ratingKey } != (1..20).map { "$it" })
    }

    private fun row(key: String, onDisk: Boolean, state: DownloadState = DownloadState.DONE) = DownloadEntity(
        serverId = "s", ratingKey = key, title = "Song $key", artist = null, album = null, albumKey = null,
        albumArtist = null, trackNumber = null, discNumber = null, durationMs = null, partKey = "/p/$key",
        thumb = null, state = state,
        filePath = if (onDisk) folder.newFile("$key.flac").path else folder.root.resolve("gone-$key.flac").path,
    )
}

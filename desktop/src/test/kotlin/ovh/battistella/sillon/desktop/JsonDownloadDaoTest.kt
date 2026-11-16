package ovh.battistella.sillon.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.desktop.platform.JsonDownloadDao
import java.io.File

class JsonDownloadDaoTest {
    @get:Rule val folder = TemporaryFolder()

    private fun row(key: String, album: String = "album", track: Int? = null, server: String = "s", addedAt: Long = 0) = DownloadEntity(
        serverId = server,
        ratingKey = key,
        title = "Title $key",
        artist = "Artist",
        album = album,
        albumKey = album,
        albumArtist = "Artist",
        trackNumber = track,
        discNumber = 1,
        durationMs = 1000,
        partKey = "/part/$key.flac",
        thumb = null,
        state = DownloadState.QUEUED,
        addedAt = addedAt,
    )

    private fun dao() = JsonDownloadDao(File(folder.root, "downloads.json"))

    @Test
    fun `rows survive a restart`() = runTest {
        val first = dao()
        first.insert(listOf(row("a", track = 2), row("b", track = 1)))
        first.complete("s", "a", "/music/a.flac", 42, "/art/a.jpg")
        val again = dao()
        assertEquals(listOf("b", "a"), again.observe("s").first().map { it.ratingKey })
        val a = again.get("s", listOf("a")).single()
        assertEquals(DownloadState.DONE, a.state)
        assertEquals(100, a.progress)
        assertEquals("/music/a.flac", a.filePath)
        assertEquals(42, a.sizeBytes)
        assertEquals("/art/a.jpg", a.artworkPath)
    }

    @Test
    fun `inserting a known track keeps the existing row`() = runTest {
        val dao = dao()
        dao.insert(listOf(row("a")))
        dao.complete("s", "a", "/music/a.flac", 42, null)
        dao.insert(listOf(row("a"), row("b")))
        assertEquals(DownloadState.DONE, dao.get("s", listOf("a")).single().state)
        assertEquals(2, dao.all().size)
    }

    @Test
    fun `next is the oldest waiting row and failed rows wait again after a retry`() = runTest {
        val dao = dao()
        dao.insert(listOf(row("late", addedAt = 20), row("early2", track = 2, addedAt = 10), row("early1", track = 1, addedAt = 10)))
        assertEquals("early1", dao.next()?.ratingKey)
        dao.setState("s", "early1", DownloadState.FAILED, 0)
        assertEquals("early2", dao.next()?.ratingKey)
        dao.complete("s", "early2", "/x", 1, null)
        dao.complete("s", "late", "/y", 1, null)
        assertNull(dao.next())
        dao.retryFailed()
        assertEquals("early1", dao.next()?.ratingKey)
    }

    @Test
    fun `servers and albums stay apart and deletes are per server`() = runTest {
        val dao = dao()
        dao.insert(listOf(row("a", album = "one", track = 2), row("b", album = "one", track = 1), row("c", album = "two"), row("a", server = "other")))
        assertEquals(listOf("b", "a"), dao.album("s", "one").map { it.ratingKey })
        assertEquals(3, dao.observe("s").first().size)
        dao.delete("s", listOf("a", "c"))
        assertEquals(listOf("b"), dao.observe("s").first().map { it.ratingKey })
        assertEquals(listOf("a"), dao.observe("other").first().map { it.ratingKey })
    }

    @Test
    fun `a damaged file starts empty`() = runTest {
        File(folder.root, "downloads.json").writeText("{ not json")
        assertEquals(emptyList<DownloadEntity>(), dao().all())
    }
}

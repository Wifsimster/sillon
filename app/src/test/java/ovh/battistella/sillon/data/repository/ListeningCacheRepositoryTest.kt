package ovh.battistella.sillon.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexMedia
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.PlexPart
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import ovh.battistella.sillon.data.settings.LoudnessMode
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.settings.StreamQuality
import ovh.battistella.sillon.playback.cache.CachedVersions
import ovh.battistella.sillon.playback.cache.ListeningCache
import ovh.battistella.sillon.playback.cache.ListeningCacheKeys
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class ListeningCacheRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = SelectedServer("m1", "Home", ServerEndpoint("https://server", "secret-token"))
    private val sessionStore = mockk<SessionStore>(relaxed = true) {
        every { session } returns flowOf(Session.Ready("user-token", server))
        coEvery { clientId() } returns "client"
    }
    private val downloads = mockk<DownloadDao>(relaxed = true)
    private val settings = mockk<SettingsStore>(relaxed = true) {
        every { loudness } returns flowOf(LoudnessMode.OFF)
        every { wifiQuality } returns flowOf(StreamQuality.ORIGINAL)
        every { mobileQuality } returns flowOf(StreamQuality.KBPS_128)
    }
    private val network = mockk<NetworkMonitor>(relaxed = true) {
        every { isOnline() } returns true
    }
    private val api = mockk<PlexApi>(relaxed = true) {
        every { streamUrl(any(), any()) } answers { "https://server${secondArg<String>()}?X-Plex-Token=secret-token" }
        every { transcodeUrl(any(), any(), any(), any(), any()) } returns "https://server/music/:/transcode/universal/start.m3u8"
    }
    private val cache = mockk<ListeningCache>(relaxed = true) {
        coEvery { versions(any(), any(), any()) } returns CachedVersions.NONE
    }

    private val repository = PlexRepository(api, sessionStore, downloads, settings, network, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), cache)

    private fun track(key: String) =
        PlexMetadata(ratingKey = key, title = key, media = listOf(PlexMedia(parts = listOf(PlexPart("/library/parts/$key/file.flac")))))

    private fun key(track: String) = ListeningCacheKeys.track("m1", track)

    @Test
    fun `download, then cache, then network`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        val file = folder.newFile("1.flac")
        coEvery { downloads.get("m1", any()) } returns listOf(
            DownloadEntity(
                serverId = "m1", ratingKey = "1", title = "1", artist = null, album = null, albumKey = null,
                albumArtist = null, trackNumber = null, discNumber = null, durationMs = null,
                partKey = "/library/parts/1/file.flac", thumb = null, state = DownloadState.DONE, filePath = file.path,
            ),
        )
        every { network.isMetered() } returns true
        coEvery { cache.versions("m1", "2", any()) } returns CachedVersions(original = true, transcodeKbps = null)

        val (downloaded, cached, streamed) = repository.playable(listOf(track("1"), track("2"), track("3")))

        assertTrue(downloaded.streamUrl.startsWith("file:"))
        assertNull(downloaded.cacheKey)
        // On mobile data the cached original beats the 128 kbps transcode.
        assertEquals(key("2"), cached.cacheKey)
        assertTrue(cached.streamUrl.contains("/library/parts/2/"))
        // Not cached: the transcode plays, and is kept as it plays.
        assertTrue(streamed.streamUrl.contains("transcode"))
        assertNull(streamed.cacheKey)
        verify { cache.streamTranscode(any(), "m1", "3", 128) }
    }

    @Test
    fun `on mobile data a cached transcode as good as asked plays from the cache`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        every { network.isMetered() } returns true
        coEvery { cache.versions("m1", "4", any()) } returns CachedVersions(original = false, transcodeKbps = 320)
        val played = repository.playable(listOf(track("4"))).single()
        assertTrue(played.streamUrl.contains("transcode"))
        coVerify { cache.replayTranscode(any(), "m1", "4", 320) }
        verify { api.transcodeUrl(any(), any(), "4", 320, any()) }
    }

    @Test
    fun `on Wi-Fi a cached transcode gives way to the original`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        every { network.isMetered() } returns false
        coEvery { cache.versions("m1", "5", any()) } returns CachedVersions(original = false, transcodeKbps = 320)
        val played = repository.playable(listOf(track("5"))).single()
        assertTrue(played.streamUrl.contains("/library/parts/5/"))
        assertEquals(key("5"), played.cacheKey)
    }

    @Test
    fun `offline a cached transcode plays whatever the setting`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        every { network.isMetered() } returns false
        every { network.isOnline() } returns false
        coEvery { cache.versions("m1", "6", any()) } returns CachedVersions(original = false, transcodeKbps = 128)
        repository.playable(listOf(track("6"))).single()
        coVerify { cache.replayTranscode(any(), "m1", "6", 128) }
    }

    @Test
    fun `an original stream on Wi-Fi is kept under a key without the token`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        every { network.isMetered() } returns false
        val played = repository.playable(listOf(track("3"))).single()
        assertEquals(key("3"), played.cacheKey)
        assertFalse(played.cacheKey!!.contains("secret"))
    }

    @Test
    fun `signing out empties the cache`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        repository.signOut()
        coVerify { cache.clear(sweepAgain = true) }
    }

    @Test
    fun `leaving the server empties the cache`() = runTest(timeout = SLOW_FIRST_MOCKS) {
        repository.changeServer()
        coVerify { sessionStore.clearServer() }
        coVerify { cache.clear(sweepAgain = true) }
    }

    private companion object {
        /** The first test of the class pays for MockK and Robolectric warming up; a busy CI runner can pass a minute. */
        val SLOW_FIRST_MOCKS = 5.minutes
    }
}

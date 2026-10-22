package ovh.battistella.sillon.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.PlexMedia
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.PlexPart
import ovh.battistella.sillon.data.plex.PlexStream
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.session.SessionStore
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class SonosAccessTest {
    private val account = "account-token"
    private val server = SelectedServer("m1", "Home", ServerEndpoint("http://192.168.1.5:32400", account))
    private val sessionStore = mockk<SessionStore>(relaxed = true) {
        every { session } returns flowOf(Session.Ready("user-token", server))
        coEvery { clientId() } returns "client"
    }

    // The real URL builders, the network calls mocked.
    private val real = PlexApi(mockk(relaxed = true), PlexDevice("Sillon", "1.0", "16", "Phone"))
    private val api = mockk<PlexApi>(relaxed = true) {
        every { streamUrl(any(), any()) } answers { real.streamUrl(firstArg(), secondArg()) }
        every { sonosTranscodeUrl(any(), any(), any(), any(), any()) } answers {
            real.sonosTranscodeUrl(firstArg(), secondArg(), thirdArg(), arg(3), arg(4))
        }
        every { artworkUrl(any(), any(), any()) } answers { real.artworkUrl(firstArg(), secondArg(), thirdArg()) }
    }

    private val repository = PlexRepository(
        api, sessionStore, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
    )

    private fun track(key: String, codec: String, rate: Int) = PlexMetadata(
        ratingKey = key,
        title = key,
        thumb = "/library/metadata/$key/thumb",
        media = listOf(
            PlexMedia(
                audioCodec = codec,
                container = codec,
                parts = listOf(PlexPart("/library/parts/$key/file.$codec", streams = listOf(PlexStream(2, codec = codec, samplingRate = rate, bitDepth = 24)))),
            ),
        ),
    )

    @Test
    fun `the speaker gets the transient token, never the account's`() = runTest(timeout = 5.minutes) {
        coEvery { api.transientToken("client", server.endpoint) } returns "transient-1"
        coEvery { api.metadataBatch(any(), any(), any()) } returns listOf(track("1", "flac", 44_100), track("2", "flac", 96_000))

        val access = repository.openSonosAccess()
        val tracks = repository.sonosTracks(listOf("1", "2"), access)

        assertEquals("transient-1", access.endpoint.token)
        assertEquals(setOf("1", "2"), tracks.keys)
        tracks.values.forEach { t ->
            assertTrue(t.uri, t.uri.contains("X-Plex-Token=transient-1"))
            assertFalse(t.uri, t.uri.contains(account))
            assertFalse(t.didl, t.didl.contains(account))
        }
        // The hi-res one is transcoded, the CD one is sent as it is.
        assertTrue(tracks.getValue("1").uri.contains("/library/parts/1/file.flac"))
        assertTrue(tracks.getValue("2").uri.contains("/transcode/universal/start.mp3"))
    }

    @Test
    fun `no transient token, no Sonos session`() = runTest(timeout = 5.minutes) {
        coEvery { api.transientToken(any(), any()) } returns null
        assertRefused()
        coEvery { api.transientToken(any(), any()) } throws java.io.IOException("401")
        assertRefused()
    }

    @Test
    fun `on Jellyfin the speaker gets a token of its own, revoked at the end`() = runTest(timeout = 5.minutes) {
        val jfServer = SelectedServer("j1", "Jelly", ServerEndpoint("http://192.168.1.6:8096", "phone-token", ServerKind.JELLYFIN, "user-1"))
        val store = mockk<SessionStore>(relaxed = true) {
            every { session } returns flowOf(Session.Ready("phone-token", jfServer))
            coEvery { clientId() } returns "client"
        }
        val realJf = JellyfinApi(mockk(relaxed = true), PlexDevice("Sillon", "1.0", "16", "Phone"))
        val jf = mockk<JellyfinApi>(relaxed = true) {
            coEvery { speakerToken(jfServer.endpoint, "client", "client-sonos") } returns "speaker-token"
            coEvery { itemsWithFormat(any(), any(), any()) } returns listOf(track("t1", "flac", 44_100).copy(media = listOf(PlexMedia(container = "flac", parts = listOf(PlexPart("/Audio/t1/stream.flac?static=true", streams = listOf(PlexStream(2, codec = "flac", samplingRate = 44_100, bitDepth = 16))))))))
            every { streamUrl(any(), any()) } answers { realJf.streamUrl(firstArg(), secondArg()) }
        }
        val repo = PlexRepository(
            api, store, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            jf, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        )

        val access = repo.openSonosAccess()
        val uri = repo.sonosTracks(listOf("t1"), access).getValue("t1").uri

        assertTrue(uri, uri.contains("api_key=speaker-token"))
        assertFalse(uri, uri.contains("phone-token"))
        access.close()
        coVerify { jf.signOut(jfServer.endpoint, "client-sonos", "speaker-token") }

        coEvery { jf.speakerToken(any(), any(), any()) } throws java.io.IOException("401")
        try {
            repo.openSonosAccess()
            fail("Sillon's own token must not reach the speaker")
        } catch (e: SonosAccessException) {
            assertEquals(SonosAccessException.Reason.JELLYFIN_TOKEN, e.reason)
        }
    }

    private suspend fun assertRefused() {
        try {
            repository.openSonosAccess()
            fail("the account token must not reach the speaker")
        } catch (e: SonosAccessException) {
            assertEquals(SonosAccessException.Reason.PLEX_TOKEN, e.reason)
        }
    }
}

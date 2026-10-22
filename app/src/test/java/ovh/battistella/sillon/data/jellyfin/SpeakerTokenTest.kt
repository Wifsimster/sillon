package ovh.battistella.sillon.data.jellyfin

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind

class SpeakerTokenTest {
    private val server = MockWebServer()
    private val api = JellyfinApi(OkHttpClient(), PlexDevice("Sillon", "1.0.0", "15", "Pixel"))
    private lateinit var endpoint: ServerEndpoint

    @Before
    fun setUp() {
        server.start()
        endpoint = ServerEndpoint(server.url("/jf").toString().trimEnd('/'), "phone-token", ServerKind.JELLYFIN, "user-1")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `the speaker signs in as its own device, approved with the phone's token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Secret": "s3cret", "Code": "123456", "Authenticated": false}"""))
        server.enqueue(MockResponse().setBody("true"))
        server.enqueue(MockResponse().setBody("""{"AccessToken": "speaker-token", "ServerId": "srv", "User": {"Id": "user-1"}}"""))

        val token = api.speakerToken(endpoint, "phone", "phone-sonos")

        assertEquals("speaker-token", token)
        val initiate = server.takeRequest()
        assertEquals("/jf/QuickConnect/Initiate", initiate.requestUrl!!.encodedPath)
        assertEquals("POST", initiate.method)
        initiate.getHeader("Authorization")!!.let {
            assertTrue(it, it.contains("DeviceId=\"phone-sonos\""))
            assertTrue(it, it.contains("Device=\"Sonos (Sillon)\""))
            assertTrue(it, !it.contains("Token="))
        }
        val authorize = server.takeRequest()
        assertEquals("/jf/QuickConnect/Authorize", authorize.requestUrl!!.encodedPath)
        assertEquals("123456", authorize.requestUrl!!.queryParameter("code"))
        assertEquals("user-1", authorize.requestUrl!!.queryParameter("userId"))
        authorize.getHeader("Authorization")!!.let {
            assertTrue(it, it.contains("Token=\"phone-token\""))
            assertTrue(it, it.contains("DeviceId=\"phone\""))
        }
        val signIn = server.takeRequest()
        assertEquals("/jf/Users/AuthenticateWithQuickConnect", signIn.requestUrl!!.encodedPath)
        assertTrue(signIn.body.readUtf8().contains("\"Secret\":\"s3cret\""))
        assertTrue(signIn.getHeader("Authorization")!!.contains("DeviceId=\"phone-sonos\""))
    }

    @Test
    fun `quick connect turned off gives no token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            api.speakerToken(endpoint, "phone", "phone-sonos")
            fail("expected an error")
        } catch (_: PlexHttpException) {
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `signing the speaker out revokes its token only`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api.signOut(endpoint, "phone-sonos", "speaker-token")
        val request = server.takeRequest()
        assertEquals("/jf/Sessions/Logout", request.requestUrl!!.encodedPath)
        assertTrue(request.getHeader("Authorization")!!.contains("Token=\"speaker-token\""))
    }
}

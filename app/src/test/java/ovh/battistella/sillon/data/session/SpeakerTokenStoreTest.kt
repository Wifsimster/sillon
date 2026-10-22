package ovh.battistella.sillon.data.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Sonos speaker's Jellyfin token stays in the real session store until it is revoked. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SpeakerTokenStoreTest {
    private val store = SessionStore(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `signing out of Sillon keeps the speaker token to revoke, and the client id`() = runTest {
        val clientId = store.clientId()
        store.rememberSpeakerToken("http://jellyfin.invalid:8096", "speaker-token")

        store.signOut()

        assertEquals("http://jellyfin.invalid:8096" to "speaker-token", store.speakerTokenLeft())
        assertEquals(clientId, store.clientId())
    }

    @Test
    fun `a revoked speaker token is forgotten`() = runTest {
        store.rememberSpeakerToken("http://jellyfin.invalid:8096", "speaker-token")

        store.forgetSpeakerToken()

        assertNull(store.speakerTokenLeft())
    }
}

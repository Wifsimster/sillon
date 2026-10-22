package ovh.battistella.sillon.data.rating

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.FiveStarsCache
import java.io.IOException

/** A rating shows at once, waits on the phone without a network, and leaves once back online. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RaterTest {
    private val store = RatingStore(ApplicationProvider.getApplicationContext())
    private val online = MutableStateFlow(false)
    private val network = mockk<NetworkMonitor> { every { online } returns this@RaterTest.online }
    private val fiveStars = mockk<FiveStarsCache>(relaxed = true)
    private var jellyfin = false
    private val sent = mutableListOf<Pair<String, Int>>()
    private var failure: Exception? = IOException("no network")
    private val repository = mockk<PlexRepository> {
        every { isJellyfin } answers { flowOf(jellyfin) }
        coEvery { currentServerId() } returns "m1"
        coEvery { sendRating(any(), any()) } answers {
            failure?.let { throw it }
            sent += firstArg<String>() to secondArg<Int>()
        }
    }

    @Before
    fun setUp() {
        runBlocking { store.clear() }
    }

    private suspend fun until(check: (RatingBook) -> Boolean) = withTimeout(5_000) {
        while (!check(store.book("m1"))) delay(10)
    }

    @Test
    fun `offline, the rating waits, then leaves with the network`() = runBlocking {
        val rater = Rater(repository, store, fiveStars, network)

        rater.rate("t1", 5)
        until { it.sendState("t1") == RatingSend.WAITING_FOR_NETWORK }
        assertEquals(5, store.book("m1").starsOf("t1", null))
        coVerify { fiveStars.mark("m1", "t1", true) }

        failure = null
        online.value = true
        until { it.pending.isEmpty() }

        assertEquals(listOf("t1" to 10), sent)
        assertEquals(null, store.book("m1").sendState("t1"))
        assertEquals(5, store.book("m1").starsOf("t1", null))
    }

    @Test
    fun `the queue survives a restart of the app`() = runBlocking {
        Rater(repository, store, fiveStars, network).rate("t1", 1)
        until { it.sendState("t1") == RatingSend.WAITING_FOR_NETWORK }

        val again = RatingStore(ApplicationProvider.getApplicationContext())
        assertEquals(mapOf("t1" to 2), again.book("m1").pending)
    }

    @Test
    fun `a refusal is shown and not retried on its own`() = runBlocking {
        failure = PlexHttpException(403, "Forbidden")
        val rater = Rater(repository, store, fiveStars, network)

        rater.rate("t1", 3)
        until { it.sendState("t1") == RatingSend.FAILED }
        failure = null
        rater.flush()

        assertTrue(sent.isEmpty())
        assertEquals(3, store.book("m1").starsOf("t1", null))
    }

    @Test
    fun `on Jellyfin, 2 to 4 stars are not taken`() = runBlocking {
        jellyfin = true
        failure = null
        val rater = Rater(repository, store, fiveStars, network)

        rater.rate("t1", 3)
        rater.rate("t2", 1)
        until { it.pending.isEmpty() && "t2" in it.known }

        assertEquals(listOf("t2" to 2), sent)
        assertEquals(null, store.book("m1").starsOf("t1", null))
    }
}

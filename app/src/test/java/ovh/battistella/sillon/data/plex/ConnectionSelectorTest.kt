package ovh.battistella.sillon.data.plex

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSelectorTest {

    private val relay = PlexConnection(uri = "https://relay.plex.direct:8443", relay = true)
    private val remote = PlexConnection(uri = "https://1-2-3-4.abc.plex.direct:32400")
    private val local = PlexConnection(uri = "https://192-168-1-10.abc.plex.direct:32400", local = true)
    private val cleartext = PlexConnection(uri = "http://192.168.1.10:32400", local = true)

    @Test
    fun `ranks LAN first, relay last, and drops cleartext`() {
        assertEquals(listOf(local, remote, relay), ConnectionSelector.rank(listOf(relay, cleartext, remote, local)))
    }

    @Test
    fun `picks the best connection that answers`() = runTest {
        val chosen = ConnectionSelector.select(listOf(relay, remote, local)) { it != local }
        assertEquals(remote, chosen)
    }

    @Test
    fun `prefers the LAN even when the internet answers first`() = runTest {
        val chosen = ConnectionSelector.select(listOf(remote, local)) {
            if (it == local) delay(1_000)
            true
        }
        assertEquals(local, chosen)
    }

    @Test
    fun `returns null when nothing answers`() = runTest {
        assertNull(ConnectionSelector.select(listOf(remote, local)) { false })
    }
}

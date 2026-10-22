package ovh.battistella.sillon.data.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.session.SelectedServer
import ovh.battistella.sillon.data.session.Session

class SleeveWearCacheTest {
    private fun plex(token: String, machine: String = "m1") =
        Session.Ready(token, SelectedServer(machine, "Home", ServerEndpoint("https://plex", "server-$token")))

    private fun jellyfin(user: String) = Session.Ready(
        "tok-$user",
        SelectedServer("jf1", "Jelly", ServerEndpoint("https://jf", "tok-$user", ServerKind.JELLYFIN, user)),
    )

    @Test
    fun `two accounts on the same server get separate caches`() {
        assertNotEquals(SleeveWearCache.ownerOf(plex("alice")), SleeveWearCache.ownerOf(plex("bob")))
        assertNotEquals(SleeveWearCache.ownerOf(jellyfin("u1")), SleeveWearCache.ownerOf(jellyfin("u2")))
    }

    @Test
    fun `one account on two servers gets separate caches`() {
        assertNotEquals(SleeveWearCache.ownerOf(plex("alice", "m1")), SleeveWearCache.ownerOf(plex("alice", "m2")))
    }

    @Test
    fun `the key is stable and never holds the token`() {
        val owner = SleeveWearCache.ownerOf(plex("secret-token"))!!
        assertEquals(owner, SleeveWearCache.ownerOf(plex("secret-token")))
        assertFalse(owner.contains("secret"))
        assertEquals("jf1-u1", SleeveWearCache.ownerOf(jellyfin("u1")))
    }

    @Test
    fun `no key without a ready session`() {
        assertNull(SleeveWearCache.ownerOf(Session.SignedOut))
        assertNull(SleeveWearCache.ownerOf(Session.NeedsServer("tok")))
    }
}

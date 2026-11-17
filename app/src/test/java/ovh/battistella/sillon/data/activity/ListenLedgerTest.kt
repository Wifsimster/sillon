package ovh.battistella.sillon.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenLedgerTest {
    private fun listen(user: String, state: ListenState = ListenState.PLAYING, mine: Boolean = false, title: String = "A") =
        ActiveListen(
            id = "s-$user", listenerKey = "$user@phone", user = user, device = "Phone", title = title, subtitle = null,
            artwork = null, state = state, positionMs = 0, durationMs = 1, kind = ListenKind.MUSIC, transcoding = false, mine = mine,
        )

    private val minute = 60_000L

    @Test
    fun `someone who starts listening is announced once`() {
        val first = ListenLedger.update(emptyMap(), "srv", listOf(listen("alice")), nowMs = 0)
        assertEquals(listOf("alice"), first.started.map { it.user })

        val next = ListenLedger.update(first.ledger, "srv", listOf(listen("alice", title = "B")), nowMs = 3 * minute)
        assertTrue(next.started.isEmpty())
    }

    @Test
    fun `a listener checked every quarter of an hour stays known`() {
        var ledger = ListenLedger.update(emptyMap(), "srv", listOf(listen("alice")), nowMs = 0).ledger
        for (check in 1..8) {
            val update = ListenLedger.update(ledger, "srv", listOf(listen("alice")), nowMs = check * 15 * minute)
            assertTrue(update.started.isEmpty())
            ledger = update.ledger
        }
    }

    @Test
    fun `a short gap between two tracks is not a new listen`() {
        val first = ListenLedger.update(emptyMap(), "srv", listOf(listen("alice")), nowMs = 0)
        val gap = ListenLedger.update(first.ledger, "srv", emptyList(), nowMs = minute)
        val back = ListenLedger.update(gap.ledger, "srv", listOf(listen("alice")), nowMs = 2 * minute)
        assertTrue(back.started.isEmpty())
    }

    @Test
    fun `coming back after a long silence is announced again`() {
        val first = ListenLedger.update(emptyMap(), "srv", listOf(listen("alice")), nowMs = 0)
        val later = ListenLedger.update(first.ledger, "srv", listOf(listen("alice")), nowMs = 31 * minute)
        assertEquals(listOf("alice"), later.started.map { it.user })
    }

    @Test
    fun `my own listens and paused ones are left out`() {
        val update = ListenLedger.update(
            emptyMap(), "srv",
            listOf(listen("me", mine = true), listen("ben", state = ListenState.PAUSED), listen("chloe", state = ListenState.BUFFERING)),
            nowMs = 0,
        )
        assertEquals(listOf("chloe"), update.started.map { it.user })

        val resumed = ListenLedger.update(update.ledger, "srv", listOf(listen("ben")), nowMs = minute)
        assertEquals(listOf("ben"), resumed.started.map { it.user })
    }

    @Test
    fun `the same listener on another server is someone new`() {
        val first = ListenLedger.update(emptyMap(), "srv-1", listOf(listen("alice")), nowMs = 0)
        val other = ListenLedger.update(first.ledger, "srv-2", listOf(listen("alice")), nowMs = minute)
        assertEquals(listOf("alice"), other.started.map { it.user })
    }

    @Test
    fun `the ledger survives being written down`() {
        val ledger = mapOf("srv|alice@phone one" to 12L, "srv|ben@tv" to 99L)
        assertEquals(ledger, ListenLedger.decode(ListenLedger.encode(ledger)))
        assertTrue(ListenLedger.decode(null).isEmpty())
        assertTrue(ListenLedger.decode("garbage\n\n12").isEmpty())
    }
}

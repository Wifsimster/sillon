package ovh.battistella.sillon.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlayMessagesTest {
    private fun update(type: String, data: String) =
        """{"MessageType":"SyncPlayGroupUpdate","MessageId":"m1","Data":{"GroupId":"g1","Type":"$type","Data":$data}}"""

    @Test
    fun `group joined`() {
        val event = SyncPlayMessages.parse(
            update("GroupJoined", """{"GroupId":"g1","GroupName":"Road trip","State":"Idle","Participants":["ana","ben"],"LastUpdatedAt":"2026-10-21T20:00:00.0000000Z"}"""),
        )
        assertEquals(SyncPlayEvent.Joined(SyncPlayGroupInfo("g1", "Road trip", "Idle", listOf("ana", "ben"))), event)
    }

    @Test
    fun `members and leaving`() {
        assertEquals(SyncPlayEvent.UserJoined("ana"), SyncPlayMessages.parse(update("UserJoined", "\"ana\"")))
        assertEquals(SyncPlayEvent.UserLeft("ben"), SyncPlayMessages.parse(update("UserLeft", "\"ben\"")))
        assertEquals(SyncPlayEvent.Left(SyncPlayEvent.Left.Why.Left), SyncPlayMessages.parse(update("GroupLeft", "\"g1\"")))
        assertEquals(SyncPlayEvent.Left(SyncPlayEvent.Left.Why.GroupGone), SyncPlayMessages.parse(update("GroupDoesNotExist", "\"\"")))
        assertEquals(
            SyncPlayEvent.Left(SyncPlayEvent.Left.Why.LibraryAccessDenied),
            SyncPlayMessages.parse(update("LibraryAccessDenied", "\"\"")),
        )
    }

    @Test
    fun `state update`() {
        assertEquals(
            SyncPlayEvent.StateChanged(GroupState.Waiting, "Buffer"),
            SyncPlayMessages.parse(update("StateUpdate", """{"State":"Waiting","Reason":"Buffer"}""")),
        )
    }

    @Test
    fun `play queue`() {
        val event = SyncPlayMessages.parse(
            update(
                "PlayQueue",
                """{"Reason":"NewPlaylist","LastUpdate":"2026-10-21T20:00:00Z","Playlist":[{"ItemId":"a1","PlaylistItemId":"p1"},{"ItemId":"a2","PlaylistItemId":"p2"}],"PlayingItemIndex":1,"StartPositionTicks":125000000,"IsPlaying":true,"ShuffleMode":"Sorted","RepeatMode":"RepeatAll"}""",
            ),
        ) as SyncPlayEvent.Queue
        val queue = event.queue
        assertEquals(listOf(SyncPlayQueueItem("a1", "p1"), SyncPlayQueueItem("a2", "p2")), queue.items)
        assertEquals(1, queue.playingIndex)
        assertEquals(12_500L, queue.startPositionMs)
        assertTrue(queue.isPlaying)
        assertEquals(false, queue.shuffle)
        assertEquals("RepeatAll", queue.repeatMode)
        assertEquals("p2", queue.current?.playlistItemId)
    }

    @Test
    fun `command with a server date down to 100 ns`() {
        val event = SyncPlayMessages.parse(
            """{"MessageType":"SyncPlayCommand","Data":{"GroupId":"g1","PlaylistItemId":"p1","When":"2026-10-21T20:00:01.5000000Z","PositionTicks":600000000,"Command":"Unpause","EmittedAt":"2026-10-21T20:00:00.9Z"}}""",
        )
        val expected = SyncCommand("g1", "p1", parseServerDate("2026-10-21T20:00:01.500Z")!!, 60_000, SyncCommandType.Unpause)
        assertEquals(SyncPlayEvent.Command(expected), event)
    }

    @Test
    fun `keep alive request`() {
        assertEquals(SyncPlayEvent.KeepAliveRequested(60), SyncPlayMessages.parse("""{"MessageType":"ForceKeepAlive","Data":60}"""))
    }

    @Test
    fun `other messages are not ours`() {
        assertNull(SyncPlayMessages.parse("""{"MessageType":"KeepAlive"}"""))
        assertNull(SyncPlayMessages.parse("""{"MessageType":"Play","Data":{}}"""))
        assertNull(SyncPlayMessages.parse("not json"))
        assertNull(SyncPlayMessages.parse(update("Unknown", "null")))
    }

    @Test
    fun `ticks and dates`() {
        assertEquals(1_000L, ticksToMs(10_000_000))
        assertEquals(10_000_000L, msToTicks(1_000))
        assertEquals(0L, msToTicks(-5))
        assertEquals(1_761_076_800_000L, parseServerDate("2025-10-21T20:00:00Z"))
        assertEquals(1_761_076_800_123L, parseServerDate("2025-10-21T20:00:00.1234567Z"))
        assertEquals(1_761_076_800_000L, parseServerDate("2025-10-21T20:00:00"))
        assertNull(parseServerDate("yesterday"))
        assertEquals("2025-10-21T20:00:00.123Z", formatServerDate(1_761_076_800_123L))
    }
}

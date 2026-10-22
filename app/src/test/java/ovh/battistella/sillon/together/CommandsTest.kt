package ovh.battistella.sillon.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.SyncCommand
import ovh.battistella.sillon.data.jellyfin.SyncCommandType
import ovh.battistella.sillon.data.jellyfin.SyncPlayQueueItem
import ovh.battistella.sillon.data.jellyfin.SyncQueue

class CommandsTest {
    private fun command(type: SyncCommandType, whenMs: Long, positionMs: Long? = 60_000, item: String = "p1") =
        SyncCommand("g1", item, whenMs, positionMs, type)

    @Test
    fun `unpause in the future waits for the moment`() {
        val action = CommandPlanner.plan(command(SyncCommandType.Unpause, whenMs = 10_500), "p1", serverNowMs = 10_000, currentPositionMs = 0)
        assertEquals(PlayerAction.PlayAt(positionMs = 60_000, delayMs = 500), action)
    }

    @Test
    fun `unpause heard late starts further in`() {
        val action = CommandPlanner.plan(command(SyncCommandType.Unpause, whenMs = 10_000), "p1", serverNowMs = 10_300, currentPositionMs = 0)
        assertEquals(PlayerAction.PlayAt(positionMs = 60_300, delayMs = 0), action)
    }

    @Test
    fun `pause waits for the moment then settles on the shared position`() {
        assertEquals(
            PlayerAction.PauseAt(60_000, 200),
            CommandPlanner.plan(command(SyncCommandType.Pause, whenMs = 10_200), "p1", 10_000, 0),
        )
        assertEquals(
            PlayerAction.PauseAt(60_000, 0),
            CommandPlanner.plan(command(SyncCommandType.Pause, whenMs = 9_000), "p1", 10_000, 0),
        )
    }

    @Test
    fun `seek pauses and reports ready`() {
        assertEquals(
            PlayerAction.SeekThenReady(60_000),
            CommandPlanner.plan(command(SyncCommandType.Seek, whenMs = 10_000), "p1", 10_000, 0),
        )
    }

    @Test
    fun `a command with no position keeps ours`() {
        assertEquals(
            PlayerAction.PauseAt(1_234, 0),
            CommandPlanner.plan(command(SyncCommandType.Pause, whenMs = 10_000, positionMs = null), "p1", 10_000, 1_234),
        )
    }

    @Test
    fun `stop applies whatever the track`() {
        assertEquals(PlayerAction.Stop, CommandPlanner.plan(command(SyncCommandType.Stop, 10_000, item = "p9"), "p1", 10_000, 0))
    }

    @Test
    fun `a command for another track is ignored`() {
        assertEquals(PlayerAction.Ignore, CommandPlanner.plan(command(SyncCommandType.Unpause, 10_000, item = "p2"), "p1", 10_000, 0))
        assertEquals(PlayerAction.Ignore, CommandPlanner.plan(command(SyncCommandType.Unpause, 10_000), null, 10_000, 0))
    }

    @Test
    fun `ids match with or without dashes`() {
        val dashed = command(SyncCommandType.Seek, 0, item = "0f8fad5b-d9cb-469f-a165-70867728950e")
        assertEquals(PlayerAction.SeekThenReady(60_000), CommandPlanner.plan(dashed, "0F8FAD5BD9CB469FA16570867728950E", 0, 0))
    }

    @Test
    fun `the timeline follows the last command`() {
        assertEquals(GroupTimeline(60_000, 10_000, playing = true), CommandPlanner.timelineAfter(command(SyncCommandType.Unpause, 10_000), 0))
        assertEquals(GroupTimeline(60_000, 10_000, playing = false), CommandPlanner.timelineAfter(command(SyncCommandType.Pause, 10_000), 0))
        assertEquals(GroupTimeline(60_000, 10_000, playing = false), CommandPlanner.timelineAfter(command(SyncCommandType.Seek, 10_000), 0))
        assertNull(CommandPlanner.timelineAfter(command(SyncCommandType.Stop, 10_000), 0))
    }

    private val queue = SyncQueue(
        items = listOf(SyncPlayQueueItem("a1", "p1"), SyncPlayQueueItem("a2", "p2"), SyncPlayQueueItem("a3", "p3")),
        playingIndex = 1,
        startPositionMs = 0,
        isPlaying = true,
        reason = "NewPlaylist",
        shuffle = false,
        repeatMode = "RepeatNone",
    )

    @Test
    fun `play, pause and seek become group requests`() {
        assertEquals(GroupRequest.Unpause, GroupRequests.forIntent(UserIntent.Play, queue))
        assertEquals(GroupRequest.Pause, GroupRequests.forIntent(UserIntent.Pause, queue))
        assertEquals(GroupRequest.Seek(42_000), GroupRequests.forIntent(UserIntent.Seek(42_000), queue))
        assertEquals(GroupRequest.Seek(0), GroupRequests.forIntent(UserIntent.Seek(-5), queue))
    }

    @Test
    fun `with nothing in the group, play and seek have nothing to act on`() {
        assertNull(GroupRequests.forIntent(UserIntent.Play, null))
        assertNull(GroupRequests.forIntent(UserIntent.Seek(1), null))
        assertNull(GroupRequests.forIntent(UserIntent.Next, null))
    }

    @Test
    fun `skips name the current track, so the server acts once for everyone`() {
        assertEquals(GroupRequest.NextItem("p2"), GroupRequests.forIntent(UserIntent.Next, queue))
        assertEquals(GroupRequest.PreviousItem("p2"), GroupRequests.forIntent(UserIntent.Previous(1_000), queue))
    }

    @Test
    fun `previous restarts the track once into it, or on the first track`() {
        assertEquals(GroupRequest.Seek(0), GroupRequests.forIntent(UserIntent.Previous(10_000), queue))
        assertEquals(GroupRequest.Seek(0), GroupRequests.forIntent(UserIntent.Previous(0), queue.copy(playingIndex = 0)))
    }

    @Test
    fun `picking a track in the queue`() {
        assertEquals(GroupRequest.SetItem("p3"), GroupRequests.forIntent(UserIntent.SeekToItem(2, 0), queue))
        assertEquals(GroupRequest.Seek(5_000), GroupRequests.forIntent(UserIntent.SeekToItem(1, 5_000), queue))
        assertNull(GroupRequests.forIntent(UserIntent.SeekToItem(7, 0), queue))
    }

    @Test
    fun `playing an album replaces the group's queue`() {
        assertEquals(
            GroupRequest.SetQueue(listOf("x", "y"), 1, 0),
            GroupRequests.forIntent(UserIntent.NewQueue(listOf("x", "y"), 5, -1), null),
        )
        assertNull(GroupRequests.forIntent(UserIntent.NewQueue(emptyList(), 0, 0), queue))
        assertEquals(GroupRequest.Queue(listOf("z")), GroupRequests.forIntent(UserIntent.Append(listOf("z")), queue))
    }

    @Test
    fun `shuffle and repeat are the group's`() {
        assertEquals(GroupRequest.Shuffle(true), GroupRequests.forIntent(UserIntent.Shuffle(true), queue))
        assertEquals(GroupRequest.Repeat("RepeatAll"), GroupRequests.forIntent(UserIntent.Repeat("RepeatAll"), queue))
    }
}

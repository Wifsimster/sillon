package ovh.battistella.sillon.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.jellyfin.GroupState
import ovh.battistella.sillon.data.jellyfin.SyncCommand
import ovh.battistella.sillon.data.jellyfin.SyncCommandType
import ovh.battistella.sillon.data.jellyfin.SyncPlayEvent
import ovh.battistella.sillon.data.jellyfin.SyncPlayGroupInfo
import ovh.battistella.sillon.data.jellyfin.SyncPlayQueueItem
import ovh.battistella.sillon.data.jellyfin.SyncQueue

class TogetherMachineTest {
    private val group = SyncPlayGroupInfo("g1", "Road trip", "Idle", listOf("ana", "ben"))
    private val queue = SyncQueue(
        items = listOf(SyncPlayQueueItem("a1", "p1"), SyncPlayQueueItem("a2", "p2")),
        playingIndex = 0,
        startPositionMs = 0,
        isPlaying = true,
        reason = "NewPlaylist",
        shuffle = false,
        repeatMode = "RepeatNone",
    )

    private fun reduce(state: TogetherState, vararg events: SessionEvent): Step =
        events.fold(Step(state)) { step, event -> TogetherMachine.reduce(step.state, event) }

    private fun server(event: SyncPlayEvent) = SessionEvent.Server(event)

    private fun joined(): TogetherState.InGroup =
        reduce(TogetherState.Off, SessionEvent.Connect("Road trip"), server(SyncPlayEvent.Joined(group))).state as TogetherState.InGroup

    @Test
    fun `connecting then joined puts us in the group`() {
        val connecting = TogetherMachine.reduce(TogetherState.Off, SessionEvent.Connect("Road trip")).state
        assertEquals(TogetherState.Connecting("Road trip"), connecting)

        val inGroup = joined()
        assertEquals("g1", inGroup.groupId)
        assertEquals(listOf("ana", "ben"), inGroup.members)
        assertEquals(GroupState.Idle, inGroup.groupState)
        assertEquals(MyStatus.Loading, inGroup.me)
    }

    @Test
    fun `a second connect while active is ignored`() {
        val state = joined()
        assertEquals(state, TogetherMachine.reduce(state, SessionEvent.Connect("other")).state)
    }

    @Test
    fun `members come and go, duplicates kept apart`() {
        var state: TogetherState = joined()
        state = reduce(state, server(SyncPlayEvent.UserJoined("ana"))).state
        assertEquals(listOf("ana", "ben", "ana"), (state as TogetherState.InGroup).members)
        state = reduce(state, server(SyncPlayEvent.UserLeft("ana"))).state
        assertEquals(listOf("ben", "ana"), (state as TogetherState.InGroup).members)
        state = reduce(state, server(SyncPlayEvent.UserLeft("zoe"))).state
        assertEquals(listOf("ben", "ana"), (state as TogetherState.InGroup).members)
    }

    @Test
    fun `a queue update loads it`() {
        val step = reduce(joined(), server(SyncPlayEvent.Queue(queue)))
        val state = step.state as TogetherState.InGroup
        assertEquals(queue, state.queue)
        assertEquals("p1", state.currentPlaylistItemId)
        assertEquals(listOf(Effect.LoadQueue(queue)), step.effects)
    }

    @Test
    fun `an empty queue stops the music here too`() {
        val step = reduce(joined(), server(SyncPlayEvent.Queue(queue)), server(SyncPlayEvent.Queue(queue.copy(items = emptyList()))))
        assertEquals(null, (step.state as TogetherState.InGroup).queue)
        assertEquals(listOf(Effect.PauseLocal), step.effects)
    }

    @Test
    fun `group state updates follow the server`() {
        val state = reduce(joined(), server(SyncPlayEvent.StateChanged(GroupState.Waiting, "Buffer"))).state
        assertEquals(GroupState.Waiting, (state as TogetherState.InGroup).groupState)
    }

    @Test
    fun `commands for our group are run, others ignored`() {
        val ours = SyncCommand("g1", "p1", 1_000, 0, SyncCommandType.Unpause)
        assertEquals(listOf(Effect.Run(ours)), reduce(joined(), server(SyncPlayEvent.Command(ours))).effects)

        val theirs = ours.copy(groupId = "g2")
        assertTrue(reduce(joined(), server(SyncPlayEvent.Command(theirs))).effects.isEmpty())
    }

    @Test
    fun `commands before joining are ignored`() {
        val command = SyncCommand("g1", "p1", 1_000, 0, SyncCommandType.Unpause)
        val step = reduce(TogetherState.Connecting(null), server(SyncPlayEvent.Command(command)))
        assertEquals(TogetherState.Connecting(null), step.state)
        assertTrue(step.effects.isEmpty())
    }

    @Test
    fun `leaving gives the player back without stopping the music`() {
        val step = reduce(joined(), SessionEvent.Leave)
        assertEquals(TogetherState.Ended(EndReason.Left), step.state)
        assertEquals(listOf(Effect.ExitGroupMode), step.effects)
    }

    @Test
    fun `a short network loss is survived`() {
        val lost = reduce(joined(), SessionEvent.ConnectionLost).state as TogetherState.InGroup
        assertTrue(lost.reconnecting)
        val back = reduce(lost, SessionEvent.Reconnected).state as TogetherState.InGroup
        assertEquals(false, back.reconnecting)
    }

    @Test
    fun `a network that stays down ends the session cleanly and pauses`() {
        val step = reduce(joined(), SessionEvent.ConnectionLost, SessionEvent.GaveUp)
        assertEquals(TogetherState.Ended(EndReason.ConnectionLost), step.state)
        assertEquals(listOf(Effect.PauseLocal, Effect.ExitGroupMode), step.effects)
    }

    @Test
    fun `the server dropping us ends the session with its reason`() {
        val gone = reduce(joined(), server(SyncPlayEvent.Left(SyncPlayEvent.Left.Why.GroupGone)))
        assertEquals(TogetherState.Ended(EndReason.GroupGone), gone.state)
        assertEquals(listOf(Effect.PauseLocal, Effect.ExitGroupMode), gone.effects)

        val denied = reduce(TogetherState.Connecting(null), server(SyncPlayEvent.Left(SyncPlayEvent.Left.Why.LibraryAccessDenied)))
        assertEquals(TogetherState.Ended(EndReason.AccessDenied), denied.state)
    }

    @Test
    fun `not in group while still joining is not an end`() {
        val connecting = TogetherState.Connecting("Road trip")
        val step = reduce(connecting, server(SyncPlayEvent.Left(SyncPlayEvent.Left.Why.NotInGroup)))
        assertEquals(connecting, step.state)
        assertTrue(step.effects.isEmpty())

        val dropped = reduce(joined(), server(SyncPlayEvent.Left(SyncPlayEvent.Left.Why.NotInGroup)))
        assertEquals(TogetherState.Ended(EndReason.GroupGone), dropped.state)
    }

    @Test
    fun `failing to connect ends without touching the music`() {
        val step = reduce(TogetherState.Off, SessionEvent.Connect(null), SessionEvent.Failed(EndReason.AccessDenied))
        assertEquals(TogetherState.Ended(EndReason.AccessDenied), step.state)
        assertEquals(listOf(Effect.ExitGroupMode), step.effects)
    }

    @Test
    fun `ending twice does nothing more`() {
        val ended = reduce(joined(), SessionEvent.Leave).state
        val again = TogetherMachine.reduce(ended, SessionEvent.GaveUp)
        assertEquals(ended, again.state)
        assertTrue(again.effects.isEmpty())
    }

    @Test
    fun `dismissing the end message goes back to off`() {
        val ended = reduce(joined(), SessionEvent.Leave).state
        assertEquals(TogetherState.Off, TogetherMachine.reduce(ended, SessionEvent.Dismiss).state)
        val inGroup = joined()
        assertEquals(inGroup, TogetherMachine.reduce(inGroup, SessionEvent.Dismiss).state)
    }

    @Test
    fun `status and drift are tracked in the group only`() {
        val state = reduce(joined(), SessionEvent.Status(MyStatus.Playing), SessionEvent.Drift(12)).state as TogetherState.InGroup
        assertEquals(MyStatus.Playing, state.me)
        assertEquals(12L, state.driftMs)
        assertEquals(TogetherState.Off, TogetherMachine.reduce(TogetherState.Off, SessionEvent.Drift(3)).state)
    }

    @Test
    fun `rejoining after a reconnect starts the group state afresh`() {
        val withQueue = reduce(joined(), server(SyncPlayEvent.Queue(queue)), SessionEvent.ConnectionLost).state
        val rejoined = reduce(withQueue, server(SyncPlayEvent.Joined(group))).state as TogetherState.InGroup
        assertEquals(false, rejoined.reconnecting)
        assertEquals(null, rejoined.queue)
    }

    @Test
    fun `losing the socket before GroupJoined ends the attempt and gives the player back`() {
        val step = reduce(TogetherState.Off, SessionEvent.Connect("Road trip"), SessionEvent.ConnectionLost)
        assertEquals(TogetherState.Ended(EndReason.Failed), step.state)
        assertEquals(listOf(Effect.ExitGroupMode), step.effects)
    }

    @Test
    fun `no GroupJoined in time ends the attempt`() {
        val step = reduce(TogetherState.Off, SessionEvent.Connect("Road trip"), SessionEvent.TimedOut)
        assertEquals(TogetherState.Ended(EndReason.TimedOut), step.state)
        assertEquals(listOf(Effect.ExitGroupMode), step.effects)
    }

    @Test
    fun `a late timeout does not end a joined session`() {
        val state = joined()
        assertEquals(Step(state), TogetherMachine.reduce(state, SessionEvent.TimedOut))
    }

    @Test
    fun `cancelling while connecting goes back to the lobby`() {
        val step = reduce(TogetherState.Off, SessionEvent.Connect("Road trip"), SessionEvent.Cancel)
        assertEquals(TogetherState.Off, step.state)
        assertEquals(listOf(Effect.ExitGroupMode), step.effects)
        assertEquals(Step(joined()), TogetherMachine.reduce(joined(), SessionEvent.Cancel))
    }
}

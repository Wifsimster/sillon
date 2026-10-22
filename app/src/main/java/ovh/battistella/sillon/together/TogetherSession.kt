package ovh.battistella.sillon.together

import ovh.battistella.sillon.data.jellyfin.GroupState
import ovh.battistella.sillon.data.jellyfin.SyncCommand
import ovh.battistella.sillon.data.jellyfin.SyncCommandType
import ovh.battistella.sillon.data.jellyfin.SyncPlayEvent
import ovh.battistella.sillon.data.jellyfin.SyncQueue
import ovh.battistella.sillon.data.jellyfin.sameJellyfinId

enum class MyStatus { Loading, Ready, Playing, Paused, CatchingUp, CantPlay }

enum class EndReason { Left, ConnectionLost, GroupGone, AccessDenied, Failed }

sealed interface TogetherState {
    data object Off : TogetherState

    data class Connecting(val groupName: String?) : TogetherState

    data class InGroup(
        val groupId: String,
        val groupName: String,
        val members: List<String>,
        val groupState: GroupState,
        val queue: SyncQueue? = null,
        val me: MyStatus = MyStatus.Loading,
        val driftMs: Long? = null,
        val reconnecting: Boolean = false,
    ) : TogetherState {
        val currentPlaylistItemId: String? get() = queue?.current?.playlistItemId
    }

    data class Ended(val reason: EndReason) : TogetherState

    val isActive: Boolean get() = this is Connecting || this is InGroup
}

sealed interface Effect {
    data class LoadQueue(val queue: SyncQueue) : Effect
    data class Run(val command: SyncCommand) : Effect

    data object PauseLocal : Effect

    data object ExitGroupMode : Effect
}

sealed interface SessionEvent {
    data class Connect(val groupName: String?) : SessionEvent
    data class Server(val event: SyncPlayEvent) : SessionEvent
    data object ConnectionLost : SessionEvent
    data object Reconnected : SessionEvent

    data object GaveUp : SessionEvent
    data class Failed(val reason: EndReason) : SessionEvent
    data object Leave : SessionEvent
    data object Dismiss : SessionEvent
    data class Status(val status: MyStatus) : SessionEvent
    data class Drift(val driftMs: Long?) : SessionEvent
}

data class Step(val state: TogetherState, val effects: List<Effect> = emptyList())

object TogetherMachine {
    fun reduce(state: TogetherState, event: SessionEvent): Step = when (event) {
        is SessionEvent.Connect ->
            if (state.isActive) Step(state) else Step(TogetherState.Connecting(event.groupName))
        is SessionEvent.Server -> onServer(state, event.event)
        SessionEvent.ConnectionLost ->
            if (state is TogetherState.InGroup) Step(state.copy(reconnecting = true)) else Step(state)
        SessionEvent.Reconnected ->
            if (state is TogetherState.InGroup) Step(state.copy(reconnecting = false)) else Step(state)
        SessionEvent.GaveUp -> end(state, EndReason.ConnectionLost, pause = true)
        is SessionEvent.Failed -> end(state, event.reason, pause = false)
        SessionEvent.Leave -> end(state, EndReason.Left, pause = false)
        SessionEvent.Dismiss -> if (state is TogetherState.Ended) Step(TogetherState.Off) else Step(state)
        is SessionEvent.Status ->
            if (state is TogetherState.InGroup) Step(state.copy(me = event.status)) else Step(state)
        is SessionEvent.Drift ->
            if (state is TogetherState.InGroup) Step(state.copy(driftMs = event.driftMs)) else Step(state)
    }

    private fun onServer(state: TogetherState, event: SyncPlayEvent): Step = when (event) {
        is SyncPlayEvent.Joined -> when (state) {
            is TogetherState.Connecting, is TogetherState.InGroup -> Step(
                TogetherState.InGroup(
                    groupId = event.group.groupId,
                    groupName = event.group.groupName,
                    members = event.group.participants,
                    groupState = GroupState.entries.firstOrNull { it.name == event.group.state } ?: GroupState.Idle,
                ),
            )
            else -> Step(state)
        }
        is SyncPlayEvent.Left -> if (state is TogetherState.Connecting && event.why == SyncPlayEvent.Left.Why.NotInGroup) {
            Step(state)
        } else if (state.isActive) {
            end(
                state,
                when (event.why) {
                    SyncPlayEvent.Left.Why.Left -> EndReason.Left
                    SyncPlayEvent.Left.Why.NotInGroup, SyncPlayEvent.Left.Why.GroupGone -> EndReason.GroupGone
                    SyncPlayEvent.Left.Why.LibraryAccessDenied -> EndReason.AccessDenied
                },
                pause = event.why != SyncPlayEvent.Left.Why.Left,
            )
        } else {
            Step(state)
        }
        else -> if (state !is TogetherState.InGroup) Step(state) else inGroup(state, event)
    }

    private fun inGroup(state: TogetherState.InGroup, event: SyncPlayEvent): Step = when (event) {
        is SyncPlayEvent.UserJoined -> Step(state.copy(members = state.members + event.name))
        is SyncPlayEvent.UserLeft -> Step(state.copy(members = state.members.minusFirst(event.name)))
        is SyncPlayEvent.StateChanged -> Step(state.copy(groupState = event.state))
        is SyncPlayEvent.Queue -> if (event.queue.items.isEmpty()) {
            Step(state.copy(queue = null, me = MyStatus.Paused, driftMs = null), listOf(Effect.PauseLocal))
        } else {
            Step(state.copy(queue = event.queue, me = MyStatus.Loading, driftMs = null), listOf(Effect.LoadQueue(event.queue)))
        }
        is SyncPlayEvent.Command -> {
            val command = event.command
            if (command.groupId.isNotBlank() && !sameJellyfinId(command.groupId, state.groupId)) {
                Step(state)
            } else {
                Step(state, listOf(Effect.Run(command)))
            }
        }
        else -> Step(state)
    }

    private fun end(state: TogetherState, reason: EndReason, pause: Boolean): Step {
        if (!state.isActive) return Step(state)
        val effects = buildList {
            if (pause) add(Effect.PauseLocal)
            add(Effect.ExitGroupMode)
        }
        return Step(TogetherState.Ended(reason), effects)
    }

    private fun List<String>.minusFirst(name: String): List<String> {
        val index = indexOf(name)
        return if (index < 0) this else toMutableList().apply { removeAt(index) }
    }
}

sealed interface PlayerAction {
    data class PlayAt(val positionMs: Long, val delayMs: Long) : PlayerAction

    data class PauseAt(val positionMs: Long, val delayMs: Long) : PlayerAction

    data class SeekThenReady(val positionMs: Long) : PlayerAction

    data object Stop : PlayerAction

    data object Ignore : PlayerAction
}

object CommandPlanner {
    fun plan(command: SyncCommand, currentPlaylistItemId: String?, serverNowMs: Long, currentPositionMs: Long): PlayerAction {
        if (command.type != SyncCommandType.Stop &&
            command.playlistItemId.isNotBlank() &&
            !sameJellyfinId(command.playlistItemId, currentPlaylistItemId)
        ) {
            return PlayerAction.Ignore
        }
        val position = command.positionMs ?: currentPositionMs
        val delay = command.whenMs - serverNowMs
        return when (command.type) {
            SyncCommandType.Unpause ->
                if (delay >= 0) PlayerAction.PlayAt(position, delay) else PlayerAction.PlayAt(position - delay, 0)
            SyncCommandType.Pause -> PlayerAction.PauseAt(position, delay.coerceAtLeast(0))
            SyncCommandType.Seek -> PlayerAction.SeekThenReady(position)
            SyncCommandType.Stop -> PlayerAction.Stop
        }
    }

    fun timelineAfter(command: SyncCommand, currentPositionMs: Long): GroupTimeline? = when (command.type) {
        SyncCommandType.Unpause -> GroupTimeline(command.positionMs ?: currentPositionMs, command.whenMs, playing = true)
        SyncCommandType.Pause, SyncCommandType.Seek ->
            GroupTimeline(command.positionMs ?: currentPositionMs, command.whenMs, playing = false)
        SyncCommandType.Stop -> null
    }
}

sealed interface UserIntent {
    data object Play : UserIntent
    data object Pause : UserIntent
    data class Seek(val positionMs: Long) : UserIntent
    data class SeekToItem(val index: Int, val positionMs: Long) : UserIntent
    data object Next : UserIntent

    data class Previous(val positionMs: Long) : UserIntent
    data class NewQueue(val itemIds: List<String>, val index: Int, val positionMs: Long) : UserIntent
    data class Append(val itemIds: List<String>) : UserIntent
    data class Shuffle(val enabled: Boolean) : UserIntent
    data class Repeat(val mode: String) : UserIntent
}

sealed interface GroupRequest {
    data object Unpause : GroupRequest
    data object Pause : GroupRequest
    data class Seek(val positionMs: Long) : GroupRequest
    data class NextItem(val playlistItemId: String) : GroupRequest
    data class PreviousItem(val playlistItemId: String) : GroupRequest
    data class SetItem(val playlistItemId: String) : GroupRequest
    data class SetQueue(val itemIds: List<String>, val index: Int, val positionMs: Long) : GroupRequest
    data class Queue(val itemIds: List<String>) : GroupRequest
    data class Shuffle(val enabled: Boolean) : GroupRequest
    data class Repeat(val mode: String) : GroupRequest
}

object GroupRequests {
    const val RESTART_THRESHOLD_MS = 3_000L

    fun forIntent(intent: UserIntent, queue: SyncQueue?): GroupRequest? {
        val current = queue?.current?.playlistItemId
        return when (intent) {
            UserIntent.Play -> if (queue == null) null else GroupRequest.Unpause
            UserIntent.Pause -> if (queue == null) null else GroupRequest.Pause
            is UserIntent.Seek -> if (queue == null) null else GroupRequest.Seek(intent.positionMs.coerceAtLeast(0))
            is UserIntent.SeekToItem -> when {
                queue == null -> null
                intent.index == queue.playingIndex -> GroupRequest.Seek(intent.positionMs.coerceAtLeast(0))
                else -> queue.items.getOrNull(intent.index)?.let { GroupRequest.SetItem(it.playlistItemId) }
            }
            UserIntent.Next -> current?.let(GroupRequest::NextItem)
            is UserIntent.Previous -> when {
                current == null -> null
                intent.positionMs > RESTART_THRESHOLD_MS || queue?.playingIndex == 0 -> GroupRequest.Seek(0)
                else -> GroupRequest.PreviousItem(current)
            }
            is UserIntent.NewQueue ->
                if (intent.itemIds.isEmpty()) null
                else GroupRequest.SetQueue(intent.itemIds, intent.index.coerceIn(0, intent.itemIds.lastIndex), intent.positionMs.coerceAtLeast(0))
            is UserIntent.Append -> if (intent.itemIds.isEmpty()) null else GroupRequest.Queue(intent.itemIds)
            is UserIntent.Shuffle -> GroupRequest.Shuffle(intent.enabled)
            is UserIntent.Repeat -> GroupRequest.Repeat(intent.mode)
        }
    }
}

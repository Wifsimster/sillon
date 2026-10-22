package ovh.battistella.sillon.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import ovh.battistella.sillon.data.plex.PlexJson
import java.time.Instant

fun ticksToMs(ticks: Long): Long = ticks / TICKS_PER_MS

fun msToTicks(ms: Long): Long = ms.coerceAtLeast(0) * TICKS_PER_MS

fun parseServerDate(value: String?): Long? =
    value?.let { runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else "${it}Z").toEpochMilli() }.getOrNull() }

fun formatServerDate(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

fun sameJellyfinId(a: String?, b: String?): Boolean =
    a != null && b != null && a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)

@Serializable
data class SyncPlayGroupInfo(
    @SerialName("GroupId") val groupId: String,
    @SerialName("GroupName") val groupName: String = "",
    @SerialName("State") val state: String = "Idle",
    @SerialName("Participants") val participants: List<String> = emptyList(),
)

@Serializable
data class SyncPlayQueueItem(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PlaylistItemId") val playlistItemId: String,
)

@Serializable
data class UtcTime(
    @SerialName("RequestReceptionTime") val requestReceptionTime: String,
    @SerialName("ResponseTransmissionTime") val responseTransmissionTime: String,
)

enum class GroupState { Idle, Waiting, Paused, Playing }

enum class SyncCommandType { Unpause, Pause, Stop, Seek }

data class SyncCommand(
    val groupId: String,
    val playlistItemId: String,
    val whenMs: Long,
    val positionMs: Long?,
    val type: SyncCommandType,
)

data class SyncQueue(
    val items: List<SyncPlayQueueItem>,
    val playingIndex: Int,
    val startPositionMs: Long,
    val isPlaying: Boolean,
    val reason: String,
    val shuffle: Boolean,
    val repeatMode: String,
) {
    val current: SyncPlayQueueItem? get() = items.getOrNull(playingIndex)
}

sealed interface SyncPlayEvent {
    data class Joined(val group: SyncPlayGroupInfo) : SyncPlayEvent
    data class UserJoined(val name: String) : SyncPlayEvent
    data class UserLeft(val name: String) : SyncPlayEvent
    data class StateChanged(val state: GroupState, val reason: String?) : SyncPlayEvent
    data class Queue(val queue: SyncQueue) : SyncPlayEvent
    data class Command(val command: SyncCommand) : SyncPlayEvent

    data class Left(val why: Why) : SyncPlayEvent {
        enum class Why { Left, NotInGroup, GroupGone, LibraryAccessDenied }
    }

    data class KeepAliveRequested(val timeoutSeconds: Int) : SyncPlayEvent
}

object SyncPlayMessages {
    fun parse(text: String): SyncPlayEvent? {
        val root = runCatching { PlexJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return when (root.string("MessageType")) {
            "SyncPlayGroupUpdate" -> (root["Data"] as? JsonObject)?.let(::groupUpdate)
            "SyncPlayCommand" -> (root["Data"] as? JsonObject)?.let(::command)
            "ForceKeepAlive" -> SyncPlayEvent.KeepAliveRequested(
                (root["Data"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: DEFAULT_KEEP_ALIVE_S,
            )
            else -> null
        }
    }

    private fun groupUpdate(update: JsonObject): SyncPlayEvent? {
        val data = update["Data"]
        return when (update.string("Type")) {
            "GroupJoined" -> data?.let { decode(SyncPlayGroupInfo.serializer(), it) }?.let(SyncPlayEvent::Joined)
            "UserJoined" -> SyncPlayEvent.UserJoined(data.text())
            "UserLeft" -> SyncPlayEvent.UserLeft(data.text())
            "GroupLeft" -> SyncPlayEvent.Left(SyncPlayEvent.Left.Why.Left)
            "NotInGroup" -> SyncPlayEvent.Left(SyncPlayEvent.Left.Why.NotInGroup)
            "GroupDoesNotExist" -> SyncPlayEvent.Left(SyncPlayEvent.Left.Why.GroupGone)
            "LibraryAccessDenied" -> SyncPlayEvent.Left(SyncPlayEvent.Left.Why.LibraryAccessDenied)
            "StateUpdate" -> (data as? JsonObject)?.let { state ->
                val value = GroupState.entries.firstOrNull { it.name == state.string("State") } ?: return null
                SyncPlayEvent.StateChanged(value, state.string("Reason"))
            }
            "PlayQueue" -> (data as? JsonObject)?.let(::queue)?.let(SyncPlayEvent::Queue)
            else -> null
        }
    }

    private fun queue(data: JsonObject): SyncQueue {
        val items = (data["Playlist"] as? JsonArray).orEmpty()
            .mapNotNull { decode(SyncPlayQueueItem.serializer(), it) }
        return SyncQueue(
            items = items,
            playingIndex = data.string("PlayingItemIndex")?.toIntOrNull() ?: 0,
            startPositionMs = ticksToMs(data.string("StartPositionTicks")?.toLongOrNull() ?: 0L),
            isPlaying = data.string("IsPlaying") == "true",
            reason = data.string("Reason").orEmpty(),
            shuffle = data.string("ShuffleMode") == "Shuffle",
            repeatMode = data.string("RepeatMode") ?: "RepeatNone",
        )
    }

    private fun command(data: JsonObject): SyncPlayEvent? {
        val type = SyncCommandType.entries.firstOrNull { it.name == data.string("Command") } ?: return null
        val whenMs = parseServerDate(data.string("When")) ?: return null
        return SyncPlayEvent.Command(
            SyncCommand(
                groupId = data.string("GroupId").orEmpty(),
                playlistItemId = data.string("PlaylistItemId").orEmpty(),
                whenMs = whenMs,
                positionMs = data.string("PositionTicks")?.toLongOrNull()?.let(::ticksToMs),
                type = type,
            ),
        )
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, element: JsonElement): T? =
        runCatching { PlexJson.decodeFromJsonElement(serializer, element) }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()

    const val DEFAULT_KEEP_ALIVE_S = 60
    const val KEEP_ALIVE = """{"MessageType":"KeepAlive"}"""
}

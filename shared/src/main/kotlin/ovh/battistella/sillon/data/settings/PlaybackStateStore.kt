package ovh.battistella.sillon.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.storage.AppStorage

@Serializable
data class SavedQueue(
    val keys: List<String>,
    val index: Int,
    val positionMs: Long,
    val title: String = "",
    val artist: String = "",
    val mystery: SavedMystery? = null,
)

@Serializable
data class SavedMystery(
    val albumKey: String,
    val tracks: List<SavedMysteryTrack>,
    val offline: Boolean = false,
    val revealed: Boolean = false,
    val left: List<String> = emptyList(),
)

@Serializable
data class SavedMysteryTrack(val key: String, val side: Int, val number: Int)

@Singleton
class PlaybackStateStore @Inject constructor(
    private val storage: AppStorage,
) {
    private val prefs = storage.preferences("playback")

    suspend fun load(): SavedQueue? =
        prefs.data.first()[QUEUE]?.let {
            runCatching { PlexJson.decodeFromString(SavedQueue.serializer(), it) }.getOrNull()
        }?.takeIf { it.keys.isNotEmpty() }

    suspend fun save(queue: SavedQueue) {
        prefs.edit { it[QUEUE] = PlexJson.encodeToString(SavedQueue.serializer(), queue) }
    }

    suspend fun clear() {
        prefs.edit { it.remove(QUEUE) }
    }

    private companion object {
        val QUEUE = stringPreferencesKey("queue")
    }
}

package ovh.battistella.sillon.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexJson
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback")

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
    @ApplicationContext private val context: Context,
) {
    suspend fun load(): SavedQueue? =
        context.playbackDataStore.data.first()[QUEUE]?.let {
            runCatching { PlexJson.decodeFromString(SavedQueue.serializer(), it) }.getOrNull()
        }?.takeIf { it.keys.isNotEmpty() }

    suspend fun save(queue: SavedQueue) {
        context.playbackDataStore.edit { it[QUEUE] = PlexJson.encodeToString(SavedQueue.serializer(), queue) }
    }

    suspend fun clear() {
        context.playbackDataStore.edit { it.remove(QUEUE) }
    }

    private companion object {
        val QUEUE = stringPreferencesKey("queue")
    }
}

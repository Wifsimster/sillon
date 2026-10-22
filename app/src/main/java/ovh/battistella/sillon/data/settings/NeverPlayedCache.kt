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

private val Context.neverPlayedDataStore: DataStore<Preferences> by preferencesDataStore(name = "never_played")

@Serializable
data class CachedLibraryPlays(
    val neverPlayed: Int,
    val total: Int,
    val fetchedAt: Long,
)

@Serializable
data class OfflineHistory(
    val neverPlayed: Set<String>,
    val checked: Set<String>,
    val fetchedAt: Long,
)

@Singleton
class NeverPlayedCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun plays(serverId: String, sectionKey: String): CachedLibraryPlays? =
        read(playsKey(serverId, sectionKey))?.let {
            runCatching { PlexJson.decodeFromString(CachedLibraryPlays.serializer(), it) }.getOrNull()
        }

    suspend fun savePlays(serverId: String, sectionKey: String, plays: CachedLibraryPlays) {
        context.neverPlayedDataStore.edit {
            it[playsKey(serverId, sectionKey)] = PlexJson.encodeToString(CachedLibraryPlays.serializer(), plays)
        }
    }

    suspend fun history(serverId: String): OfflineHistory? =
        read(historyKey(serverId))?.let {
            runCatching { PlexJson.decodeFromString(OfflineHistory.serializer(), it) }.getOrNull()
        }

    suspend fun saveHistory(serverId: String, history: OfflineHistory) {
        context.neverPlayedDataStore.edit {
            it[historyKey(serverId)] = PlexJson.encodeToString(OfflineHistory.serializer(), history)
        }
    }

    suspend fun markPlayed(serverId: String, key: String) {
        val history = history(serverId) ?: return
        if (key !in history.neverPlayed) return
        saveHistory(serverId, history.copy(neverPlayed = history.neverPlayed - key))
    }

    private suspend fun read(key: Preferences.Key<String>): String? = context.neverPlayedDataStore.data.first()[key]

    private fun playsKey(serverId: String, sectionKey: String) = stringPreferencesKey("plays|$serverId|$sectionKey")

    private fun historyKey(serverId: String) = stringPreferencesKey("history|$serverId")
}

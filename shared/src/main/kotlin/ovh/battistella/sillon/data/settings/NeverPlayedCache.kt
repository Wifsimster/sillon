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
    private val storage: AppStorage,
) {
    private val prefs = storage.preferences("never_played")

    suspend fun plays(serverId: String, sectionKey: String): CachedLibraryPlays? =
        read(playsKey(serverId, sectionKey))?.let {
            runCatching { PlexJson.decodeFromString(CachedLibraryPlays.serializer(), it) }.getOrNull()
        }

    suspend fun savePlays(serverId: String, sectionKey: String, plays: CachedLibraryPlays) {
        prefs.edit {
            it[playsKey(serverId, sectionKey)] = PlexJson.encodeToString(CachedLibraryPlays.serializer(), plays)
        }
    }

    suspend fun history(serverId: String): OfflineHistory? =
        read(historyKey(serverId))?.let {
            runCatching { PlexJson.decodeFromString(OfflineHistory.serializer(), it) }.getOrNull()
        }

    suspend fun saveHistory(serverId: String, history: OfflineHistory) {
        prefs.edit {
            it[historyKey(serverId)] = PlexJson.encodeToString(OfflineHistory.serializer(), history)
        }
    }

    suspend fun markPlayed(serverId: String, key: String) {
        val history = history(serverId) ?: return
        if (key !in history.neverPlayed) return
        saveHistory(serverId, history.copy(neverPlayed = history.neverPlayed - key))
    }

    private suspend fun read(key: Preferences.Key<String>): String? = prefs.data.first()[key]

    private fun playsKey(serverId: String, sectionKey: String) = stringPreferencesKey("plays|$serverId|$sectionKey")

    private fun historyKey(serverId: String) = stringPreferencesKey("history|$serverId")
}

package ovh.battistella.sillon.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexJson
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fiveStarsDataStore: DataStore<Preferences> by preferencesDataStore(name = "five_stars")

/** The keys of the 5-star tracks (Jellyfin: favourites), as last seen on the server. */
@Serializable
data class CachedFiveStars(
    val keys: Set<String>,
    val fetchedAt: Long,
)

/**
 * Downloads carry no rating: this list, kept on the phone, lets "5 stars" play the downloaded
 * 5-star tracks without a connection. It holds track keys only, for one server at a time, at most
 * [MAX_KEYS] of them, and is cleared on sign-out.
 */
@Singleton
class FiveStarsCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun observe(serverId: String): Flow<CachedFiveStars?> = context.fiveStarsDataStore.data.map { prefs ->
        prefs[key(serverId)]?.let { runCatching { PlexJson.decodeFromString(CachedFiveStars.serializer(), it) }.getOrNull() }
    }

    /** Replaces the list, and drops the one of any other server. */
    suspend fun save(serverId: String, keys: Collection<String>, now: Long = System.currentTimeMillis()) {
        val value = CachedFiveStars(keys.asSequence().take(MAX_KEYS).toSet(), now)
        context.fiveStarsDataStore.edit {
            it.clear()
            it[key(serverId)] = PlexJson.encodeToString(CachedFiveStars.serializer(), value)
        }
    }

    suspend fun clear() {
        context.fiveStarsDataStore.edit { it.clear() }
    }

    private fun key(serverId: String) = stringPreferencesKey("keys|$serverId")

    companion object {
        const val MAX_KEYS = 10_000
        const val TTL_MS = 6 * 60 * 60_000L

        /** Whether the list should be fetched again: never fetched, or older than [TTL_MS]. */
        fun isStale(cached: CachedFiveStars?, now: Long): Boolean = cached == null || now - cached.fetchedAt >= TTL_MS
    }
}

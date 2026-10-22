package ovh.battistella.sillon.data.rating

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.plex.PlexJson
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ratingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ratings")

/** The [RatingBook] of the server in use, in memory for the player and kept on the phone. */
data class ServerRatings(val serverId: String? = null, val book: RatingBook = RatingBook())

/**
 * Holds the ratings given on the phone and the ones waiting to be sent, for one server at a
 * time. Track keys and ratings only; cleared on sign-out.
 */
@Singleton
class RatingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(ServerRatings())
    val state: StateFlow<ServerRatings> = _state.asStateFlow()

    /** The book of [serverId], read from the phone the first time. */
    suspend fun book(serverId: String): RatingBook = lock.withLock { load(serverId) }

    suspend fun update(serverId: String, change: (RatingBook) -> RatingBook): RatingBook = lock.withLock {
        val before = load(serverId)
        val after = change(before)
        if (after != before) {
            _state.value = ServerRatings(serverId, after)
            context.ratingsDataStore.edit { it[key(serverId)] = PlexJson.encodeToString(RatingBook.serializer(), after) }
        }
        after
    }

    suspend fun clear() = lock.withLock {
        _state.value = ServerRatings()
        context.ratingsDataStore.edit { it.clear() }
    }

    private suspend fun load(serverId: String): RatingBook {
        val current = _state.value
        if (current.serverId == serverId) return current.book
        val saved = context.ratingsDataStore.data.first()[key(serverId)]
            ?.let { runCatching { PlexJson.decodeFromString(RatingBook.serializer(), it) }.getOrNull() }
            ?: RatingBook()
        _state.value = ServerRatings(serverId, saved)
        return saved
    }

    private fun key(serverId: String) = stringPreferencesKey("book|$serverId")
}

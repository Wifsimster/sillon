package ovh.battistella.sillon.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ovh.battistella.sillon.data.plex.ServerEndpoint
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** The server the user picked, and how it was last reached. */
data class SelectedServer(
    val machineId: String,
    val name: String,
    val endpoint: ServerEndpoint,
)

/**
 * Where the user is in the setup: signed out, signed in without a server yet,
 * or ready to browse.
 */
sealed interface Session {
    data object SignedOut : Session
    data class NeedsServer(val userToken: String) : Session
    data class Ready(val userToken: String, val server: SelectedServer) : Session
}

/**
 * Persists the Plex account token and the chosen server in a private DataStore,
 * which is excluded from backups (see res/xml/data_extraction_rules.xml).
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.sessionDataStore

    val session: Flow<Session> = store.data.map { prefs ->
        val userToken = prefs[USER_TOKEN] ?: return@map Session.SignedOut
        val machineId = prefs[SERVER_ID]
        val uri = prefs[SERVER_URI]
        val serverToken = prefs[SERVER_TOKEN]
        if (machineId == null || uri == null || serverToken == null) {
            Session.NeedsServer(userToken)
        } else {
            Session.Ready(
                userToken,
                SelectedServer(machineId, prefs[SERVER_NAME].orEmpty(), ServerEndpoint(uri, serverToken)),
            )
        }
    }

    /**
     * Stable per-install identifier Plex uses to tell devices apart. Created on
     * first use and kept across sign-outs so plex.tv doesn't list a new device
     * every time.
     */
    suspend fun clientId(): String {
        store.data.first()[CLIENT_ID]?.let { return it }
        var id = ""
        store.edit { prefs ->
            id = prefs[CLIENT_ID] ?: UUID.randomUUID().toString().also { prefs[CLIENT_ID] = it }
        }
        return id
    }

    suspend fun signIn(userToken: String) {
        store.edit { it[USER_TOKEN] = userToken }
    }

    suspend fun selectServer(server: SelectedServer) {
        store.edit {
            it[SERVER_ID] = server.machineId
            it[SERVER_NAME] = server.name
            it[SERVER_URI] = server.endpoint.uri
            it[SERVER_TOKEN] = server.endpoint.token
        }
    }

    suspend fun clearServer() {
        store.edit {
            it.remove(SERVER_ID)
            it.remove(SERVER_NAME)
            it.remove(SERVER_URI)
            it.remove(SERVER_TOKEN)
        }
    }

    suspend fun signOut() {
        store.edit { prefs ->
            val clientId = prefs[CLIENT_ID]
            prefs.clear()
            if (clientId != null) prefs[CLIENT_ID] = clientId
        }
    }

    private companion object {
        val CLIENT_ID = stringPreferencesKey("client_id")
        val USER_TOKEN = stringPreferencesKey("user_token")
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val SERVER_URI = stringPreferencesKey("server_uri")
        val SERVER_TOKEN = stringPreferencesKey("server_token")
    }
}

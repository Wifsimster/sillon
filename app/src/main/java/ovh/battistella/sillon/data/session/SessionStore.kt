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
import ovh.battistella.sillon.data.plex.ServerKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class SelectedServer(
    val machineId: String,
    val name: String,
    val endpoint: ServerEndpoint,
)

sealed interface Session {
    data object SignedOut : Session
    data class NeedsServer(val userToken: String) : Session
    data class Ready(val userToken: String, val server: SelectedServer) : Session
}

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
            val kind = ServerKind.entries.firstOrNull { it.name == prefs[SERVER_KIND] } ?: ServerKind.PLEX
            Session.Ready(
                userToken,
                SelectedServer(
                    machineId,
                    prefs[SERVER_NAME].orEmpty(),
                    ServerEndpoint(uri, serverToken, kind, prefs[SERVER_USER_ID]),
                ),
            )
        }
    }

    val musicSectionKey: Flow<String?> = store.data.map { it[MUSIC_SECTION] }

    suspend fun selectMusicSection(key: String) {
        store.edit { it[MUSIC_SECTION] = key }
    }

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
        store.edit { it.putServer(server) }
    }

    suspend fun signInJellyfin(server: SelectedServer) {
        store.edit {
            it[USER_TOKEN] = server.endpoint.token
            it.putServer(server)
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putServer(server: SelectedServer) {
        this[SERVER_ID] = server.machineId
        this[SERVER_NAME] = server.name
        this[SERVER_URI] = server.endpoint.uri
        this[SERVER_TOKEN] = server.endpoint.token
        this[SERVER_KIND] = server.endpoint.kind.name
        val userId = server.endpoint.userId
        if (userId != null) this[SERVER_USER_ID] = userId else remove(SERVER_USER_ID)
    }

    suspend fun clearServer() {
        store.edit {
            it.remove(SERVER_ID)
            it.remove(SERVER_NAME)
            it.remove(SERVER_URI)
            it.remove(SERVER_TOKEN)
            it.remove(SERVER_KIND)
            it.remove(SERVER_USER_ID)
            it.remove(MUSIC_SECTION)
        }
    }

    /**
     * A Sonos speaker's Jellyfin token, kept until it is revoked: when the app is killed during a
     * Sonos session, the next start revokes it ([speakerTokenLeft]).
     */
    suspend fun rememberSpeakerToken(serverUri: String, token: String) {
        store.edit {
            it[SPEAKER_SERVER_URI] = serverUri
            it[SPEAKER_TOKEN] = token
        }
    }

    suspend fun speakerTokenLeft(): Pair<String, String>? {
        val prefs = store.data.first()
        val uri = prefs[SPEAKER_SERVER_URI] ?: return null
        val token = prefs[SPEAKER_TOKEN] ?: return null
        return uri to token
    }

    suspend fun forgetSpeakerToken() {
        store.edit {
            it.remove(SPEAKER_SERVER_URI)
            it.remove(SPEAKER_TOKEN)
        }
    }

    suspend fun signOut() {
        store.edit { prefs ->
            val clientId = prefs[CLIENT_ID]
            prefs.clear()
            val speakerUri = prefs[SPEAKER_SERVER_URI]
            val speakerToken = prefs[SPEAKER_TOKEN]
            prefs.clear()
            if (clientId != null) prefs[CLIENT_ID] = clientId
            // Still to revoke on the next start.
            if (speakerUri != null && speakerToken != null) {
                prefs[SPEAKER_SERVER_URI] = speakerUri
                prefs[SPEAKER_TOKEN] = speakerToken
            }
        }
    }

    private companion object {
        val CLIENT_ID = stringPreferencesKey("client_id")
        val USER_TOKEN = stringPreferencesKey("user_token")
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val SERVER_URI = stringPreferencesKey("server_uri")
        val SERVER_TOKEN = stringPreferencesKey("server_token")
        val MUSIC_SECTION = stringPreferencesKey("music_section")
        val SERVER_KIND = stringPreferencesKey("server_kind")
        val SERVER_USER_ID = stringPreferencesKey("server_user_id")
        val SPEAKER_SERVER_URI = stringPreferencesKey("speaker_server_uri")
        val SPEAKER_TOKEN = stringPreferencesKey("speaker_token")
    }
}

package ovh.battistella.sillon.desktop.platform

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ovh.battistella.sillon.data.storage.AppStorage

data class WindowPlacement(val width: Int, val height: Int, val x: Int?, val y: Int?, val maximized: Boolean)

class DesktopPrefs(storage: AppStorage) {
    private val prefs = storage.preferences("desktop")

    val autoUpdate: Flow<Boolean> = prefs.data.map { it[AUTO_UPDATE] ?: true }

    suspend fun setAutoUpdate(value: Boolean) {
        prefs.edit { it[AUTO_UPDATE] = value }
    }

    val lyricsShown: Flow<Boolean> = prefs.data.map { it[LYRICS_SHOWN] ?: true }

    suspend fun setLyricsShown(value: Boolean) {
        prefs.edit { it[LYRICS_SHOWN] = value }
    }

    suspend fun volume(): Float = (prefs.data.first()[VOLUME] ?: 1f).coerceIn(0f, 1f)

    suspend fun setVolume(value: Float) {
        prefs.edit { it[VOLUME] = value.coerceIn(0f, 1f) }
    }

    suspend fun window(): WindowPlacement? {
        val data = prefs.data.first()
        val width = data[WINDOW_WIDTH] ?: return null
        val height = data[WINDOW_HEIGHT] ?: return null
        return WindowPlacement(width, height, data[WINDOW_X], data[WINDOW_Y], data[WINDOW_MAXIMIZED] ?: false)
    }

    suspend fun setWindow(placement: WindowPlacement) {
        prefs.edit {
            it[WINDOW_WIDTH] = placement.width
            it[WINDOW_HEIGHT] = placement.height
            it.setOrRemove(WINDOW_X, placement.x)
            it.setOrRemove(WINDOW_Y, placement.y)
            it[WINDOW_MAXIMIZED] = placement.maximized
        }
    }

    suspend fun lastSeenVersion(): String? = prefs.data.first()[LAST_SEEN_VERSION]

    suspend fun setLastSeenVersion(version: String) {
        prefs.edit { it[LAST_SEEN_VERSION] = version }
    }

    suspend fun skippedVersion(): String? = prefs.data.first()[SKIPPED_VERSION]

    suspend fun setSkippedVersion(version: String) {
        prefs.edit { it[SKIPPED_VERSION] = version }
    }

    suspend fun attemptedVersion(): String? = prefs.data.first()[ATTEMPTED_VERSION]

    suspend fun setAttemptedVersion(version: String?) {
        prefs.edit { it.setOrRemove(ATTEMPTED_VERSION, version) }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value else remove(key)
    }

    private companion object {
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val VOLUME = floatPreferencesKey("volume")
        val LYRICS_SHOWN = booleanPreferencesKey("lyrics_shown")
        val WINDOW_WIDTH = intPreferencesKey("window_width")
        val WINDOW_HEIGHT = intPreferencesKey("window_height")
        val WINDOW_X = intPreferencesKey("window_x")
        val WINDOW_Y = intPreferencesKey("window_y")
        val WINDOW_MAXIMIZED = booleanPreferencesKey("window_maximized")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val ATTEMPTED_VERSION = stringPreferencesKey("attempted_version")
    }
}

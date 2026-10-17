package ovh.battistella.sillon.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences that aren't tied to the account or server. */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Download only on Wi-Fi (unmetered networks). On by default: albums are large. */
    val downloadOnWifiOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[WIFI_ONLY] ?: true }

    suspend fun setDownloadOnWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[WIFI_ONLY] = value }
    }

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
    }
}

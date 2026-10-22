package ovh.battistella.sillon.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class StreamQuality(val kbps: Int?) {
    ORIGINAL(null),
    KBPS_320(320),
    KBPS_192(192),
    KBPS_128(128),
}

enum class LoudnessMode { OFF, TRACK, ALBUM }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val downloadOnWifiOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[WIFI_ONLY] ?: true }

    val wifiQuality: Flow<StreamQuality> = context.settingsDataStore.data.map {
        it[WIFI_QUALITY].toQuality(StreamQuality.ORIGINAL)
    }

    val mobileQuality: Flow<StreamQuality> = context.settingsDataStore.data.map {
        it[MOBILE_QUALITY].toQuality(StreamQuality.KBPS_320)
    }

    suspend fun setWifiQuality(value: StreamQuality) {
        context.settingsDataStore.edit { it[WIFI_QUALITY] = value.name }
    }

    suspend fun setMobileQuality(value: StreamQuality) {
        context.settingsDataStore.edit { it[MOBILE_QUALITY] = value.name }
    }

    val loudness: Flow<LoudnessMode> = context.settingsDataStore.data.map { prefs ->
        LoudnessMode.entries.firstOrNull { it.name == prefs[LOUDNESS] } ?: LoudnessMode.TRACK
    }

    suspend fun setLoudness(value: LoudnessMode) {
        context.settingsDataStore.edit { it[LOUDNESS] = value.name }
    }

    val wallpaperColors: Flow<Boolean> = context.settingsDataStore.data.map { it[WALLPAPER_COLORS] ?: false }

    suspend fun setWallpaperColors(value: Boolean) {
        context.settingsDataStore.edit { it[WALLPAPER_COLORS] = value }
    }

    val sleeveWear: Flow<Boolean> = context.settingsDataStore.data.map { it[SLEEVE_WEAR] ?: true }

    suspend fun setSleeveWear(value: Boolean) {
        context.settingsDataStore.edit { it[SLEEVE_WEAR] = value }
    }

    val listenBySide: Flow<Boolean> = context.settingsDataStore.data.map { it[LISTEN_BY_SIDE] ?: false }

    suspend fun setListenBySide(value: Boolean) {
        context.settingsDataStore.edit { it[LISTEN_BY_SIDE] = value }
    }

    private fun String?.toQuality(default: StreamQuality) =
        StreamQuality.entries.firstOrNull { it.name == this } ?: default

    suspend fun setDownloadOnWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[WIFI_ONLY] = value }
    }

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val WIFI_QUALITY = stringPreferencesKey("stream_quality_wifi")
        val MOBILE_QUALITY = stringPreferencesKey("stream_quality_mobile")
        val LOUDNESS = stringPreferencesKey("loudness")
        val WALLPAPER_COLORS = booleanPreferencesKey("wallpaper_colors")
        val SLEEVE_WEAR = booleanPreferencesKey("sleeve_wear")
        val LISTEN_BY_SIDE = booleanPreferencesKey("listen_by_side")
    }
}

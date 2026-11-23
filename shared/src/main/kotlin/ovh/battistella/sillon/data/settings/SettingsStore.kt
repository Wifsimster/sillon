package ovh.battistella.sillon.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ovh.battistella.sillon.data.storage.AppStorage
import ovh.battistella.sillon.playback.cache.CacheLimit

enum class StreamQuality(val kbps: Int?) {
    ORIGINAL(null),
    KBPS_320(320),
    KBPS_192(192),
    KBPS_128(128),
}

enum class LoudnessMode { OFF, TRACK, ALBUM }

@Singleton
class SettingsStore @Inject constructor(
    private val storage: AppStorage,
) {
    private val prefs = storage.preferences("settings")

    val downloadOnWifiOnly: Flow<Boolean> = prefs.data.map { it[WIFI_ONLY] ?: true }

    val wifiQuality: Flow<StreamQuality> = prefs.data.map {
        it[WIFI_QUALITY].toQuality(StreamQuality.ORIGINAL)
    }

    val mobileQuality: Flow<StreamQuality> = prefs.data.map {
        it[MOBILE_QUALITY].toQuality(StreamQuality.KBPS_320)
    }

    suspend fun setWifiQuality(value: StreamQuality) {
        prefs.edit { it[WIFI_QUALITY] = value.name }
    }

    suspend fun setMobileQuality(value: StreamQuality) {
        prefs.edit { it[MOBILE_QUALITY] = value.name }
    }

    val loudness: Flow<LoudnessMode> = prefs.data.map { prefs ->
        LoudnessMode.entries.firstOrNull { it.name == prefs[LOUDNESS] } ?: LoudnessMode.TRACK
    }

    suspend fun setLoudness(value: LoudnessMode) {
        prefs.edit { it[LOUDNESS] = value.name }
    }

    val wallpaperColors: Flow<Boolean> = prefs.data.map { it[WALLPAPER_COLORS] ?: false }

    suspend fun setWallpaperColors(value: Boolean) {
        prefs.edit { it[WALLPAPER_COLORS] = value }
    }

    /** Light or dark, accent, pure black and wallpaper colours: everything the theme page sets, in one read. */
    val theme: Flow<ThemePreferences> = prefs.data.map { prefs ->
        ThemePreferences(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.SYSTEM,
            accent = ThemeAccent.entries.firstOrNull { it.name == prefs[THEME_ACCENT] } ?: ThemeAccent.AMBER,
            pureBlack = prefs[PURE_BLACK] ?: false,
            wallpaperColors = prefs[WALLPAPER_COLORS] ?: false,
        )
    }

    suspend fun setThemeMode(value: ThemeMode) {
        prefs.edit { it[THEME_MODE] = value.name }
    }

    suspend fun setThemeAccent(value: ThemeAccent) {
        prefs.edit { it[THEME_ACCENT] = value.name }
    }

    suspend fun setPureBlack(value: Boolean) {
        prefs.edit { it[PURE_BLACK] = value }
    }

    val sleeveWear: Flow<Boolean> = prefs.data.map { it[SLEEVE_WEAR] ?: true }

    suspend fun setSleeveWear(value: Boolean) {
        prefs.edit { it[SLEEVE_WEAR] = value }
    }

    /** Sort chosen on a track list ([list] names the album, playlist or search), as written by `TrackOrder.encode`. */
    fun trackSort(list: String): Flow<String?> = prefs.data.map { it[stringPreferencesKey("track_sort_$list")] }

    suspend fun setTrackSort(list: String, value: String?) {
        val key = stringPreferencesKey("track_sort_$list")
        prefs.edit { if (value != null) it[key] = value else it.remove(key) }
    }

    val listenBySide: Flow<Boolean> = prefs.data.map { it[LISTEN_BY_SIDE] ?: false }

    suspend fun setListenBySide(value: Boolean) {
        prefs.edit { it[LISTEN_BY_SIDE] = value }
    }

    /** Keep what is played in the listening cache: on unless the user turns it off. */
    val listeningCache: Flow<Boolean> = prefs.data.map { it[LISTENING_CACHE] ?: true }

    suspend fun setListeningCache(value: Boolean) {
        prefs.edit { it[LISTENING_CACHE] = value }
    }

    val listeningCacheGb: Flow<Int> = prefs.data.map { CacheLimit.sanitize(it[LISTENING_CACHE_GB]) }

    suspend fun setListeningCacheGb(value: Int) {
        prefs.edit { it[LISTENING_CACHE_GB] = CacheLimit.sanitize(value) }
    }

    /** Skip the tracks rated 1 star in albums and playlists played in order: on unless turned off. */
    val skipOneStar: Flow<Boolean> = prefs.data.map { it[SKIP_ONE_STAR] ?: true }

    suspend fun setSkipOneStar(value: Boolean) {
        prefs.edit { it[SKIP_ONE_STAR] = value }
    }

    val hiddenArtists: Flow<Set<String>> = prefs.data.map { it[HIDDEN_ARTISTS].orEmpty() }

    suspend fun setArtistHidden(name: String, hidden: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.edit { values ->
            val others = values[HIDDEN_ARTISTS].orEmpty().filterNot { it.equals(trimmed, ignoreCase = true) }.toSet()
            values[HIDDEN_ARTISTS] = if (hidden) others + trimmed else others
        }
    }

    private fun String?.toQuality(default: StreamQuality) =
        StreamQuality.entries.firstOrNull { it.name == this } ?: default

    suspend fun setDownloadOnWifiOnly(value: Boolean) {
        prefs.edit { it[WIFI_ONLY] = value }
    }

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val WIFI_QUALITY = stringPreferencesKey("stream_quality_wifi")
        val MOBILE_QUALITY = stringPreferencesKey("stream_quality_mobile")
        val LOUDNESS = stringPreferencesKey("loudness")
        val WALLPAPER_COLORS = booleanPreferencesKey("wallpaper_colors")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val SLEEVE_WEAR = booleanPreferencesKey("sleeve_wear")
        val LISTEN_BY_SIDE = booleanPreferencesKey("listen_by_side")
        val LISTENING_CACHE = booleanPreferencesKey("listening_cache")
        val LISTENING_CACHE_GB = intPreferencesKey("listening_cache_gb")
        val SKIP_ONE_STAR = booleanPreferencesKey("skip_one_star")
        val HIDDEN_ARTISTS = stringSetPreferencesKey("hidden_artists")
    }
}

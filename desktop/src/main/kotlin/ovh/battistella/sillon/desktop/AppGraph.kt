package ovh.battistella.sillon.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.rating.Rater
import ovh.battistella.sillon.data.rating.RatingStore
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.SessionStore
import ovh.battistella.sillon.data.settings.FiveStarsCache
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.wear.SleeveWearCache
import ovh.battistella.sillon.data.wear.SleeveWearRepository
import ovh.battistella.sillon.desktop.download.DownloadFiles
import ovh.battistella.sillon.desktop.download.Downloader
import ovh.battistella.sillon.desktop.platform.DesktopNetworkMonitor
import ovh.battistella.sillon.desktop.platform.DesktopPrefs
import ovh.battistella.sillon.desktop.platform.DesktopStorage
import ovh.battistella.sillon.desktop.platform.JsonDownloadDao
import ovh.battistella.sillon.desktop.platform.NoTrackCache
import ovh.battistella.sillon.desktop.player.Mpv
import ovh.battistella.sillon.desktop.player.Playback
import ovh.battistella.sillon.desktop.player.PlayerEngine
import ovh.battistella.sillon.desktop.player.Waveforms
import ovh.battistella.sillon.desktop.ui.screens.GenreLibrary
import ovh.battistella.sillon.desktop.update.UpdateFeed
import ovh.battistella.sillon.desktop.update.Updater
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@OptIn(FlowPreview::class)
class AppGraph(val storage: DesktopStorage = DesktopStorage()) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val version: String = packagedVersion()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val device = PlexDevice(
        product = "Sillon",
        version = version,
        platformVersion = System.getProperty("os.version").orEmpty(),
        deviceName = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: platformName(),
        platform = platformName(),
    )

    val network = DesktopNetworkMonitor()
    val settings = SettingsStore(storage)
    private val session = SessionStore(storage)
    private val ratings = RatingStore(storage)
    val fiveStars = FiveStarsCache(storage)
    private val downloadDao = JsonDownloadDao(File(storage.filesDir, "downloads.json"))

    private val sleeveWearCache = SleeveWearCache(storage)

    val repository = PlexRepository(
        api = PlexApi(client, device),
        sessionStore = session,
        downloads = downloadDao,
        settings = settings,
        network = network,
        jellyfin = JellyfinApi(client, device),
        sleeveWearCache = sleeveWearCache,
        fiveStarsCache = fiveStars,
        listeningCache = NoTrackCache(),
        ratings = ratings,
    )

    private val rater = Rater(repository, ratings, fiveStars, network)

    val sleeveWear = SleeveWearRepository(repository, sleeveWearCache, settings)

    val genres = GenreLibrary(repository)

    val waveforms = Waveforms(repository)

    val downloadFiles = DownloadFiles(storage.filesDir)

    val downloader = Downloader(downloadDao, repository, client, downloadFiles, scope)

    val playback = Playback(repository, settings, PlaybackStateStore(storage), rater, scope) { listener, skip ->
        PlayerEngine(Mpv.create(PlayerEngine.searchDirs()), listener, skip, System.getProperty("sillon.audio.output"))
    }

    val prefs = DesktopPrefs(storage)

    val updater = Updater(
        feed = UpdateFeed(client, (System.getProperty("sillon.update.feed") ?: UpdateFeed.DEFAULT).toHttpUrl()),
        client = client,
        prefs = prefs,
        currentVersion = runningVersion(),
        dir = File(storage.privateDir, "updates"),
        scope = scope,
    )

    init {
        scope.launch { runCatching { downloader.resume() } }
        scope.launch {
            runCatching { playback.setVolume(prefs.volume()) }
            playback.state.map { it.volume }.distinctUntilChanged().drop(1).debounce(VOLUME_SAVE_DELAY_MS).collect { prefs.setVolume(it) }
        }
        updater.start()
    }

    fun close() {
        runCatching { playback.release() }
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val VOLUME_SAVE_DELAY_MS = 500L

        fun packagedVersion(): String =
            AppGraph::class.java.getResource("/sillon-version.txt")?.readText()?.trim().orEmpty().ifBlank { "0.0.0" }

        fun runningVersion(): String = System.getProperty("sillon.update.version") ?: packagedVersion()

        fun platformName(): String {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.contains("win") -> "Windows"
                os.contains("mac") -> "macOS"
                else -> "Linux"
            }
        }
    }
}

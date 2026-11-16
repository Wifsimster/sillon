package ovh.battistella.sillon.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import ovh.battistella.sillon.desktop.download.DownloadFiles
import ovh.battistella.sillon.desktop.download.Downloader
import ovh.battistella.sillon.desktop.platform.DesktopNetworkMonitor
import ovh.battistella.sillon.desktop.platform.DesktopStorage
import ovh.battistella.sillon.desktop.platform.JsonDownloadDao
import ovh.battistella.sillon.desktop.platform.NoTrackCache
import ovh.battistella.sillon.desktop.player.Mpv
import ovh.battistella.sillon.desktop.player.Playback
import ovh.battistella.sillon.desktop.player.PlayerEngine
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class AppGraph(val storage: DesktopStorage = DesktopStorage()) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val version: String = AppGraph::class.java.getResource("/sillon-version.txt")?.readText()?.trim().orEmpty().ifBlank { "0.0.0" }

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

    val repository = PlexRepository(
        api = PlexApi(client, device),
        sessionStore = session,
        downloads = downloadDao,
        settings = settings,
        network = network,
        jellyfin = JellyfinApi(client, device),
        sleeveWearCache = SleeveWearCache(storage),
        fiveStarsCache = fiveStars,
        listeningCache = NoTrackCache(),
        ratings = ratings,
    )

    private val rater = Rater(repository, ratings, fiveStars, network)

    val downloadFiles = DownloadFiles(storage.filesDir)

    val downloader = Downloader(downloadDao, repository, client, downloadFiles, scope)

    val playback = Playback(repository, settings, PlaybackStateStore(storage), rater, scope) { listener, skip ->
        PlayerEngine(Mpv.create(PlayerEngine.searchDirs()), listener, skip, System.getProperty("sillon.audio.output"))
    }

    init {
        scope.launch { runCatching { downloader.resume() } }
    }

    fun close() {
        runCatching { playback.release() }
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
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

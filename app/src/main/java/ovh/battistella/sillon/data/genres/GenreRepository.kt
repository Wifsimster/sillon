package ovh.battistella.sillon.data.genres

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.home.SearchHomeRepository
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.repository.isPlayableOffline
import ovh.battistella.sillon.data.repository.toPlayable
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.wear.SleeveWearCache
import ovh.battistella.sillon.download.DownloadManager
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.ui.screens.search.downloadedAlbums
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** The genres to show: [onPhone] maps each downloaded album to its saved cover, if any. */
data class GenreView(
    val genres: List<Genre> = emptyList(),
    val online: Boolean = true,
    val onPhone: Map<String, String?> = emptyMap(),
    val known: Boolean = false,
)

/**
 * The genres of the search page and their pages. The list comes from disk at once and is refreshed from
 * the server in the background. Offline, only the genres of downloaded albums are offered; an unrevealed
 * surprise album counts in no genre (the same `hidden` set as the search page's rows).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class GenreRepository @Inject constructor(
    private val repository: PlexRepository,
    private val store: GenreCache,
    private val searchHome: SearchHomeRepository,
    private val downloads: DownloadManager,
    private val network: NetworkMonitor,
    private val settings: SettingsStore,
    private val filter: GenreShuffleFilter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = MutableStateFlow<CachedGenres?>(null)
    private val lock = Mutex()
    private var started = false

    val cached: Flow<CachedGenres?> = repository.session
        .map { SleeveWearCache.ownerOf(it) }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf<CachedGenres?>(null)
            } else {
                current.onStart { lock.withLock { load(owner) } }.map { it?.takeIf { c -> c.owner == owner } }
            }
        }

    private val onPhone: Flow<Map<String, String?>> = repository.session
        .map { (it as? Session.Ready)?.server?.machineId }
        .distinctUntilChanged()
        .flatMapLatest { serverId -> if (serverId == null) flowOf<List<DownloadEntity>>(emptyList()) else downloads.observe(serverId) }
        .map { downloadedAlbums(it) }
        .flowOn(Dispatchers.IO)

    /** What the search page and the genre pages show, kept current with the downloads and the network. */
    val view: Flow<GenreView> = combine(cached, searchHome.cached, onPhone, network.online) { genres, home, phone, online ->
        val hidden = searchHome.hidden(home?.sealed.orEmpty())
        GenreView(
            genres = Genres.group(genres?.albums.orEmpty(), hidden, onPhone = if (online) null else phone.keys),
            online = online,
            onPhone = phone,
            known = genres != null && genres.refreshedAt > 0,
        )
    }.flowOn(Dispatchers.Default)

    /** Forgets the genres on sign-out, once per process. */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            repository.session.collect { if (it is Session.SignedOut) clear() }
        }
    }

    /** Fetches the genres again when the cache is older than [maxAgeMs]. Throws when the server can't be reached. */
    suspend fun refresh(maxAgeMs: Long = REFRESH_AFTER_MS, now: Long = System.currentTimeMillis()) {
        val owner = currentOwner() ?: return
        val disk = lock.withLock { load(owner) }
        if (disk.refreshedAt > 0 && now - disk.refreshedAt < maxAgeMs) return
        val albums = repository.genreAlbums()
        lock.withLock {
            if (currentOwner() != owner) return
            val next = CachedGenres(owner, albums, now)
            current.value = next
            store.write(next)
        }
    }

    /**
     * Tracks for a genre shuffle, in a random order: from a random handful of the genre's albums on the
     * server, or from the downloaded ones offline. [GenreShuffleFilter] takes out what must not play.
     */
    suspend fun shuffle(genre: Genre, online: Boolean, random: Random = Random): List<PlayableTrack> {
        if (!online) return offlineShuffle(genre, random)
        val albums = Genres.shuffleAlbums(genre, random)
        val tracks = coroutineScope {
            albums.map { album -> async { runCatching { repository.tracksOf(album.key) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().filter { it.type.isEmpty() || it.type == "track" }
        val kept = filter.online(tracks).shuffled(random).take(Genres.SHUFFLE_TRACKS)
        return repository.playable(kept).ifEmpty { offlineShuffle(genre, random) }
    }

    private suspend fun offlineShuffle(genre: Genre, random: Random): List<PlayableTrack> {
        val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return emptyList()
        val keys = genre.albums.map { it.key }.toSet()
        val mode = settings.loudness.first()
        val rows = downloads.observe(serverId).first().filter { it.albumKey in keys && it.isPlayableOffline() }
        return filter.offline(rows).shuffled(random).take(Genres.SHUFFLE_TRACKS).map { it.toPlayable(mode) }
    }

    /** Call with [lock] held. */
    private suspend fun load(owner: String): CachedGenres {
        current.value?.takeIf { it.owner == owner }?.let { return it }
        val disk = store.read(owner) ?: CachedGenres(owner)
        current.value = disk
        return disk
    }

    private suspend fun clear() = lock.withLock {
        current.value = null
        store.clear()
    }

    private suspend fun currentOwner(): String? = SleeveWearCache.ownerOf(repository.session.first())

    private companion object {
        /** Genres change with the library, not with every listen: a quarter of an hour is fresh enough. */
        const val REFRESH_AFTER_MS = 15 * 60 * 1_000L
    }
}

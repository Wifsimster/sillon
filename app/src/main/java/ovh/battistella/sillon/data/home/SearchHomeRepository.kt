package ovh.battistella.sillon.data.home

import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.data.plex.ServerKind
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.Session
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import ovh.battistella.sillon.data.wear.SleeveWearCache
import ovh.battistella.sillon.playback.PlaybackConnection
import ovh.battistella.sillon.playback.mystery.MysterySession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two rows of the empty search page. They come from disk at once and are refreshed from the server
 * in the background; Sillon's own plays fill in what the server missed (offline plays).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SearchHomeRepository @Inject constructor(
    private val repository: PlexRepository,
    private val store: SearchHomeCache,
    private val wear: SleeveWearCache,
    private val mystery: MysterySession,
    private val saved: PlaybackStateStore,
    private val playback: Lazy<PlaybackConnection>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val home = MutableStateFlow<CachedHome?>(null)
    private val lock = Mutex()
    private var started = false

    /** What the page shows for the signed-in account, starting with what is on disk. */
    val cached: Flow<CachedHome?> = repository.session
        .map { SleeveWearCache.ownerOf(it) }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf<CachedHome?>(null)
            } else {
                home.onStart { lock.withLock { load(owner) } }.map { it?.takeIf { c -> c.owner == owner } }
            }
        }

    /** Starts listening to the player and to sign-outs, once per process. */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            repository.session.collect { if (it is Session.SignedOut) clear() }
        }
        scope.launch(Dispatchers.Main) { record() }
    }

    /** Albums that must not show yet: sealed ones, and the mystery playing or waiting to be resumed. */
    suspend fun hidden(sealed: Set<String>): Set<String> {
        val live = mystery.state.value?.takeUnless { it.revealed }?.albumKey
        val resumable = runCatching { saved.load()?.mystery }.getOrNull()?.takeUnless { it.revealed }?.albumKey
        return sealed + listOfNotNull(live, resumable)
    }

    /** The play counts of sleeve wear, or null when it has not counted them yet. */
    suspend fun classicKeys(): List<String>? {
        val owner = currentOwner() ?: return null
        return wear.read(owner)?.let { SearchHome.classics(it.albums) }
    }

    /** Fetches the recent albums from the server. Throws when it can't be reached; the cache stays. */
    suspend fun refreshRecent() {
        val owner = currentOwner() ?: return
        val recent = repository.recentAlbums()
        update(owner) { it.copy(recent = recent) }
    }

    /** Names the albums sleeve wear counts the most, asking the server only for the ones not known yet. */
    suspend fun refreshClassics() {
        val owner = currentOwner() ?: return
        val keys = classicKeys() ?: return
        val current = lock.withLock { load(owner) }
        val known = (current.classics + current.recent + current.local).associateBy { it.key }
        val missing = keys.filter { it !in known }
        val fetched = runCatching { repository.itemsByKeys(missing) }.getOrDefault(emptyList())
            .filter { it.type == "album" || it.type.isEmpty() }
            .map(SearchHome::album)
        val classics = SearchHome.ordered(keys, known.values + fetched)
        update(owner) { it.copy(classics = classics) }
    }

    private suspend fun record() {
        repository.session
            .map { (it as? Session.Ready)?.let { ready -> SleeveWearCache.ownerOf(ready)?.to(ready.server.endpoint.kind) } }
            .distinctUntilChanged()
            .flatMapLatest { owned ->
                if (owned == null) return@flatMapLatest emptyFlow<Triple<Pair<String, ServerKind>, HomeAlbum?, Pair<String, Boolean>?>>()
                val player = playback.get().state
                    .map { s -> s.albumKey?.takeIf { s.isPlaying }?.let { HomeAlbum(it, s.album, s.artist.ifBlank { null }) } }
                    .distinctUntilChanged { a, b -> a?.key == b?.key }
                val sleeve = mystery.state.map { m -> m?.let { it.albumKey to it.revealed } }.distinctUntilChanged()
                combine(player, sleeve) { open, m -> Triple(owned, open, m) }
            }
            .collect { (owned, open, sleeve) ->
                val (owner, kind) = owned
                update(owner) { cached ->
                    val sealed = SearchHome.seal(cached.sealed, sleeve?.first, sleeve?.second ?: false, open?.key)
                    val local = if (open == null || open.key in sealed || cached.local.firstOrNull()?.key == open.key) {
                        cached.local
                    } else {
                        val played = open.copy(thumb = SearchHome.thumbOf(kind, open.key), playedAt = System.currentTimeMillis())
                        SearchHome.record(cached.local, played)
                    }
                    cached.copy(sealed = sealed, local = local)
                }
            }
    }

    private suspend fun update(owner: String, change: (CachedHome) -> CachedHome) = lock.withLock {
        val current = load(owner)
        val next = change(current)
        if (next == current) return@withLock
        if (currentOwner() != owner) return@withLock
        home.value = next
        store.write(next)
    }

    /** Call with [lock] held. */
    private suspend fun load(owner: String): CachedHome {
        home.value?.takeIf { it.owner == owner }?.let { return it }
        val disk = store.read(owner) ?: CachedHome(owner)
        home.value = disk
        return disk
    }

    private suspend fun clear() = lock.withLock {
        home.value = null
        store.clear()
    }

    private suspend fun currentOwner(): String? = SleeveWearCache.ownerOf(repository.session.first())
}

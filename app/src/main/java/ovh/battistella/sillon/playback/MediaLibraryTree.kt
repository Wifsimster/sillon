package ovh.battistella.sillon.playback

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The content Android Auto (and other media browsers) can navigate:
 * Artists → albums → tracks, Albums → tracks, Playlists → tracks.
 *
 * Browse items carry no stream URL (it holds the Plex token, and has no
 * business in another app's process). A track is turned into a playable item
 * only when the car asks to play it, in [resolve].
 */
@Singleton
class MediaLibraryTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlexRepository,
) {
    /** Listings are re-requested on every page and every back navigation: keep them a few minutes. */
    private val cache = mutableMapOf<String, Pair<Long, List<MediaItem>>>()
    private val cacheLock = Mutex()

    /**
     * Only the system and known media surfaces may browse the library. Other
     * apps can still connect (transport controls work) but see nothing.
     */
    fun isCallerTrusted(caller: MediaSession.ControllerInfo): Boolean {
        if (caller.uid == Process.myUid() || caller.uid == Process.SYSTEM_UID) return true
        return caller.packageName in TRUSTED_BROWSER_PACKAGES
    }

    fun root(): MediaItem = folder(BrowseNode.Root, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    /** List rows rather than a grid: the browse items have no artwork for the car to show. */
    fun rootParams(): LibraryParams = LibraryParams.Builder()
        .setExtras(
            Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            },
        )
        .build()

    /** One page of [parentId]'s children. */
    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        // Keyed on server and library too, so switching either never shows stale lists.
        val scope = runCatching { repository.endpoint().uri }.getOrDefault("") + "|" + repository.musicSectionKey.first()
        val all = cached("$scope|$parentId") { listChildren(BrowseIds.parse(parentId)) }
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return all
        return all.drop(page * pageSize).take(pageSize)
    }

    suspend fun item(mediaId: String): MediaItem? = when (val node = BrowseIds.parse(mediaId)) {
        BrowseNode.Root -> root()
        BrowseNode.Artists, BrowseNode.Albums, BrowseNode.Playlists, BrowseNode.FiveStars ->
            topLevel().firstOrNull { it.mediaId == mediaId }
        is BrowseNode.Artist, is BrowseNode.Album, is BrowseNode.Playlist ->
            repository.metadata(keyOf(node))?.let { folderFor(node, it) }
        is BrowseNode.Track -> repository.metadata(node.key)?.let { trackItem(node.parent, it) }
        BrowseNode.Recent, null -> null
    }

    /**
     * Turn what a controller asked to play into playable items.
     *
     * - Items that already have a stream URL (queued by the app) are kept.
     * - A single browsed track becomes its whole album or playlist, starting at it.
     * - A voice request ("play Daft Punk on Sillon") arrives as an empty item with
     *   a search query, and plays the matching tracks.
     */
    suspend fun resolve(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): MediaItemsWithStartPosition {
        val single = items.singleOrNull()
        val query = single?.requestMetadata?.searchQuery
        if (single != null && single.localConfiguration == null && !query.isNullOrBlank()) {
            return MediaItemsWithStartPosition(searchTracks(query), 0, C.TIME_UNSET)
        }
        val node = single?.let { BrowseIds.parse(it.mediaId) }
        if (node is BrowseNode.Track) {
            val tracks = playable(tracksOf(node.parent))
            val index = tracks.indexOfFirst { it.mediaId == node.key }.coerceAtLeast(0)
            return MediaItemsWithStartPosition(tracks, index, C.TIME_UNSET)
        }
        val resolved = resolveEach(items)
        val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
        return MediaItemsWithStartPosition(resolved, start, startPositionMs)
    }

    /**
     * [resolve] for "add to queue": no expansion. Items queued by the app keep
     * their stream URL; anything else (a browsed track, or a bare ratingKey sent
     * back by another process) is fetched by key and made playable.
     */
    suspend fun resolveEach(items: List<MediaItem>): List<MediaItem> {
        fun keyOf(item: MediaItem): String? = when (val node = BrowseIds.parse(item.mediaId)) {
            is BrowseNode.Track -> node.key
            null -> item.mediaId.takeIf { it.isNotBlank() }
            else -> null
        }
        val missing = items.filter { it.localConfiguration == null }.mapNotNull(::keyOf)
        val fetched = if (missing.isEmpty()) {
            emptyMap()
        } else {
            val metadata = missing.mapNotNull { runCatching { repository.metadata(it) }.getOrNull() }
            playable(metadata).associateBy { it.mediaId }
        }
        return items.mapNotNull { item ->
            if (item.localConfiguration != null) item else keyOf(item)?.let { fetched[it] }
        }
    }

    private suspend fun listChildren(node: BrowseNode?): List<MediaItem> = when (node) {
        BrowseNode.Root -> topLevel()
        BrowseNode.Artists -> repository.currentMusicSection()?.let { section ->
            repository.artists(section.key).map { folderFor(BrowseNode.Artist(it.ratingKey), it) }
        }.orEmpty()
        BrowseNode.Albums -> repository.currentMusicSection()?.let { section ->
            repository.albums(section.key).map { folderFor(BrowseNode.Album(it.ratingKey), it) }
        }.orEmpty()
        BrowseNode.Playlists -> repository.playlists().map { folderFor(BrowseNode.Playlist(it.ratingKey), it) }
        is BrowseNode.Artist -> repository.children(node.key).map { folderFor(BrowseNode.Album(it.ratingKey), it) }
        is BrowseNode.Album, is BrowseNode.Playlist, BrowseNode.FiveStars -> tracksOf(node).map { trackItem(node, it) }
        BrowseNode.Recent, is BrowseNode.Track, null -> emptyList()
    }

    private fun topLevel(): List<MediaItem> = listOf(
        folder(BrowseNode.FiveStars, context.getString(R.string.five_stars), MediaMetadata.MEDIA_TYPE_PLAYLIST),
        folder(BrowseNode.Artists, context.getString(R.string.library_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
        folder(BrowseNode.Albums, context.getString(R.string.library_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        folder(BrowseNode.Playlists, context.getString(R.string.library_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
    )

    private suspend fun tracksOf(parent: BrowseNode): List<PlexMetadata> = when (parent) {
        is BrowseNode.Album -> repository.children(parent.key)
        is BrowseNode.Playlist -> repository.playlistItems(parent.key)
        BrowseNode.FiveStars -> repository.fiveStarTracks()
        else -> emptyList()
    }

    private suspend fun searchTracks(query: String): List<MediaItem> {
        val section = repository.currentMusicSection() ?: return emptyList()
        val hubs = repository.search(section.key, query)
        val tracks = hubs.filter { it.type == "track" }.flatMap { it.metadata }
        if (tracks.isNotEmpty()) return playable(tracks)
        // No track matched by name: play the first matching album, or the first
        // album of the first matching artist.
        val album = hubs.firstOrNull { it.type == "album" }?.metadata?.firstOrNull()
            ?: hubs.firstOrNull { it.type == "artist" }?.metadata?.firstOrNull()
                ?.let { artist -> repository.children(artist.ratingKey).firstOrNull() }
            ?: return emptyList()
        return playable(repository.children(album.ratingKey))
    }

    private suspend fun playable(tracks: List<PlexMetadata>): List<MediaItem> =
        repository.playable(tracks).map(MediaItems::from)

    private fun folderFor(node: BrowseNode, item: PlexMetadata): MediaItem {
        val type = when (node) {
            is BrowseNode.Artist -> MediaMetadata.MEDIA_TYPE_ARTIST
            is BrowseNode.Playlist -> MediaMetadata.MEDIA_TYPE_PLAYLIST
            else -> MediaMetadata.MEDIA_TYPE_ALBUM
        }
        val subtitle = when (node) {
            is BrowseNode.Album -> listOfNotNull(item.parentTitle, item.year?.toString()).joinToString(" · ")
            else -> null
        }
        return folder(node, item.title, type, subtitle)
    }

    private fun folder(node: BrowseNode, title: String, type: Int, subtitle: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle?.takeIf { it.isNotBlank() })
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(type)
            .build()
        return MediaItem.Builder().setMediaId(BrowseIds.of(node)).setMediaMetadata(metadata).build()
    }

    private fun trackItem(parent: BrowseNode, track: PlexMetadata): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.trackArtist)
            .setAlbumTitle(track.parentTitle)
            .setTrackNumber(track.index)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(BrowseIds.of(BrowseNode.Track(parent, track.ratingKey)))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun keyOf(node: BrowseNode): String = when (node) {
        is BrowseNode.Artist -> node.key
        is BrowseNode.Album -> node.key
        is BrowseNode.Playlist -> node.key
        is BrowseNode.Track -> node.key
        else -> ""
    }

    private suspend fun cached(key: String, load: suspend () -> List<MediaItem>): List<MediaItem> {
        val now = System.currentTimeMillis()
        cacheLock.withLock { cache[key]?.takeIf { now - it.first < CACHE_MS }?.let { return it.second } }
        val items = load()
        cacheLock.withLock { cache[key] = now to items }
        return items
    }

    private companion object {
        const val CACHE_MS = 5 * 60_000L

        val TRUSTED_BROWSER_PACKAGES = setOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.autosimulator", // Android Auto head-unit simulator
            "com.google.android.carassistant", // Automotive assistant
            "com.google.android.googlequicksearchbox", // Google Assistant
            "com.google.android.wearable.app", // Wear OS companion
            "com.android.systemui", // System media controls
            "com.google.android.bluetooth", // Bluetooth media controls
        )
    }
}

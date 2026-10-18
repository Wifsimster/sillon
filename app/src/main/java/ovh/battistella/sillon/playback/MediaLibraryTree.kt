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
import ovh.battistella.sillon.data.settings.PlaybackStateStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaLibraryTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlexRepository,
    private val playbackState: PlaybackStateStore,
) {
    private val cache = mutableMapOf<String, Pair<Long, List<MediaItem>>>()
    private val cacheLock = Mutex()

    fun isCallerTrusted(caller: MediaSession.ControllerInfo): Boolean {
        if (caller.uid == Process.myUid() || caller.uid == Process.SYSTEM_UID) return true
        return caller.packageName in TRUSTED_BROWSER_PACKAGES
    }

    fun root(): MediaItem = folder(BrowseNode.Root, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun rootParams(): LibraryParams = LibraryParams.Builder()
        .setExtras(
            Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            },
        )
        .build()

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val scope = runCatching { repository.endpoint().uri }.getOrDefault("") + "|" + repository.musicSectionKey.first()
        val all = cached("$scope|$parentId") { listChildren(BrowseIds.parse(parentId)) }
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return all
        return all.drop(page * pageSize).take(pageSize)
    }

    suspend fun item(mediaId: String): MediaItem? = when (val node = BrowseIds.parse(mediaId)) {
        BrowseNode.Root -> root()
        BrowseNode.Artists, BrowseNode.Albums, BrowseNode.Playlists, BrowseNode.FiveStars, BrowseNode.Radios ->
            topLevel().firstOrNull { it.mediaId == mediaId }
        is BrowseNode.Station -> children(BrowseIds.RADIOS, 0, Int.MAX_VALUE).firstOrNull { it.mediaId == mediaId }
        is BrowseNode.Artist, is BrowseNode.Album, is BrowseNode.Playlist ->
            repository.metadata(keyOf(node))?.let { folderFor(node, it) }
        is BrowseNode.Track -> repository.metadata(node.key)?.let { trackItem(node.parent, it) }
        BrowseNode.Resume -> resumeItem()
        BrowseNode.Recent, null -> null
    }

    suspend fun resolve(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): MediaItemsWithStartPosition {
        val single = items.singleOrNull()
        val query = single?.requestMetadata?.searchQuery
        if (single != null && single.localConfiguration == null && !query.isNullOrBlank()) {
            return MediaItemsWithStartPosition(searchTracks(query), 0, C.TIME_UNSET)
        }
        val node = single?.let { BrowseIds.parse(it.mediaId) }
        if (node == BrowseNode.Resume) return resumption()
        if (node is BrowseNode.Station) {
            val tracks = playable(repository.stationTracks(ovh.battistella.sillon.data.repository.Station(node.key, null)))
            return MediaItemsWithStartPosition(tracks, 0, C.TIME_UNSET)
        }
        if (node is BrowseNode.Track) {
            val tracks = playable(tracksOf(node.parent))
            val index = tracks.indexOfFirst { it.mediaId == node.key }.coerceAtLeast(0)
            return MediaItemsWithStartPosition(tracks, index, C.TIME_UNSET)
        }
        val resolved = resolveEach(items)
        val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
        return MediaItemsWithStartPosition(resolved, start, startPositionMs)
    }

    suspend fun recentRoot(): MediaItem? =
        playbackState.load()?.let { folder(BrowseNode.Recent, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED) }

    suspend fun resumption(): MediaItemsWithStartPosition {
        val saved = playbackState.load() ?: return MediaItemsWithStartPosition(emptyList(), 0, 0L)
        val items = repository.restoreQueue(saved.keys).map(MediaItems::from)
        val current = saved.keys.getOrNull(saved.index)
        val index = items.indexOfFirst { it.mediaId == current }
        return if (index >= 0) {
            MediaItemsWithStartPosition(items, index, saved.positionMs)
        } else {
            MediaItemsWithStartPosition(items, 0, 0L)
        }
    }

    private suspend fun resumeItem(): MediaItem? {
        val saved = playbackState.load() ?: return null
        val metadata = MediaMetadata.Builder()
            .setTitle(saved.title.ifBlank { context.getString(R.string.app_name) })
            .setArtist(saved.artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder().setMediaId(BrowseIds.RESUME).setMediaMetadata(metadata).build()
    }

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
        BrowseNode.Radios -> repository.stations().map { station ->
            val metadata = MediaMetadata.Builder()
                .setTitle(station.title ?: context.getString(R.string.radio_library))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
            MediaItem.Builder().setMediaId(BrowseIds.of(BrowseNode.Station(station.key))).setMediaMetadata(metadata).build()
        }
        is BrowseNode.Artist -> repository.albumsOf(node.key).map { folderFor(BrowseNode.Album(it.ratingKey), it) }
        is BrowseNode.Album, is BrowseNode.Playlist, BrowseNode.FiveStars -> tracksOf(node).map { trackItem(node, it) }
        BrowseNode.Recent -> listOfNotNull(resumeItem())
        BrowseNode.Resume, is BrowseNode.Track, is BrowseNode.Station, null -> emptyList()
    }

    private suspend fun topLevel(): List<MediaItem> = listOf(
        folder(
            BrowseNode.FiveStars,
            context.getString(if (repository.isJellyfin.first()) R.string.five_stars_jellyfin else R.string.five_stars),
            MediaMetadata.MEDIA_TYPE_PLAYLIST,
        ),
        folder(BrowseNode.Artists, context.getString(R.string.library_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
        folder(BrowseNode.Albums, context.getString(R.string.library_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        folder(BrowseNode.Playlists, context.getString(R.string.library_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folder(BrowseNode.Radios, context.getString(R.string.radios), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS),
    )

    private suspend fun tracksOf(parent: BrowseNode): List<PlexMetadata> = when (parent) {
        is BrowseNode.Album -> repository.tracksOf(parent.key)
        is BrowseNode.Playlist -> repository.playlistItems(parent.key)
        BrowseNode.FiveStars -> repository.fiveStarTracks()
        else -> emptyList()
    }

    private suspend fun searchTracks(query: String): List<MediaItem> {
        val section = repository.currentMusicSection() ?: return emptyList()
        val hubs = repository.search(section.key, query)
        val tracks = hubs.filter { it.type == "track" }.flatMap { it.metadata }
        if (tracks.isNotEmpty()) return playable(tracks)
        val album = hubs.firstOrNull { it.type == "album" }?.metadata?.firstOrNull()
            ?: hubs.firstOrNull { it.type == "artist" }?.metadata?.firstOrNull()
                ?.let { artist -> repository.albumsOf(artist.ratingKey).firstOrNull() }
            ?: return emptyList()
        return playable(repository.tracksOf(album.ratingKey))
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
        return folder(node, item.title, type, subtitle, ArtworkProvider.uriFor(item.artwork))
    }

    private fun folder(
        node: BrowseNode,
        title: String,
        type: Int,
        subtitle: String? = null,
        artwork: android.net.Uri? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle?.takeIf { it.isNotBlank() })
            .setArtworkUri(artwork)
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
            .setArtworkUri(ArtworkProvider.uriFor(track.artwork))
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
            "com.google.android.projection.gearhead",
            "com.google.android.autosimulator",
            "com.google.android.carassistant",
            "com.google.android.googlequicksearchbox",
            "com.google.android.wearable.app",
            "com.android.systemui",
            "com.google.android.bluetooth",
        )
    }
}

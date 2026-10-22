package ovh.battistella.sillon.playback.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser

/** What the listening cache holds whole for one track. */
data class CachedVersions(val original: Boolean, val transcodeKbps: Int?) {
    companion object {
        val NONE = CachedVersions(original = false, transcodeKbps = null)
    }
}

/**
 * The transcoded copies in the listening cache. A transcode is an HLS tree (playlists, then
 * segments) whose URLs carry a play session that changes on every play; each file is kept under
 * `<server>/<track>/aac-<kbps>/<file>`, the same from one play to the next.
 *
 * One copy never mixes two plays: the first request of a new play clears whatever an earlier,
 * unfinished play left under its key, and from then on only that play writes and reads it.
 * Requests from any other play of the same track go to the network untouched.
 */
@OptIn(UnstableApi::class)
class TranscodeCache(private val cache: () -> Cache) {
    private enum class State { NEW, ACTIVE, PASSING }

    private class Play(val prefix: String, var state: State)

    private val plays = object : LinkedHashMap<String, Play>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Play>) = size > MAX_PLAYS
    }

    /** For each transcode, the play sessions allowed to read and write it. */
    private val owners = HashMap<String, Set<String>>()

    /** A play about to stream [prefix] from the server: it is kept as it plays. */
    @Synchronized
    fun register(session: String, prefix: String) {
        plays[session] = Play(prefix, State.NEW)
    }

    /** A play served from the whole copy under [prefix], whose playlists still name the play that wrote it. */
    @Synchronized
    fun reuse(session: String, prefix: String) {
        val writer = entryKey(prefix)?.let { meta(it, META_SESSION) }
        if (writer == null) {
            register(session, prefix)
            return
        }
        plays[session] = Play(prefix, State.ACTIVE)
        plays[writer] = Play(prefix, State.ACTIVE)
        owners[prefix] = setOf(session, writer)
    }

    /** The cache key for a transcode request, or null when it must go to the network as is. */
    @Synchronized
    fun keyFor(url: String): String? {
        val session = TranscodeUrls.sessionIn(url) { it in plays } ?: return null
        val play = plays.getValue(session)
        val part = TranscodeUrls.partOf(url, session) ?: return null
        val key = ListeningCacheKeys.part(play.prefix, part)
        if (play.state == State.NEW) {
            // The first request of a play is its entry playlist. A whole copy stays as it is;
            // anything else under this key is a fragment of an earlier play, and goes.
            if (isWhole(play.prefix)) {
                play.state = State.PASSING
                return null
            }
            removeAll(play.prefix)
            owners[play.prefix] = setOf(session)
            play.state = State.ACTIVE
            val path = TranscodeUrls.pathOf(url) ?: return null
            cache().applyContentMetadataMutations(
                key,
                ContentMetadataMutations().set(META_SESSION, session).set(META_PATH, path),
            )
        }
        return key.takeIf { owners[play.prefix]?.contains(session) == true }
    }

    /** The best copies of a track that are whole. A whole original makes the transcodes useless: they go. */
    fun versions(serverId: String, trackKey: String, kbpsChoices: List<Int>): CachedVersions {
        val original = ListeningCacheKeys.track(serverId, trackKey)
        if (ListeningCache.isWhole(cache(), original)) {
            dropTranscodes(original)
            return CachedVersions(original = true, transcodeKbps = null)
        }
        val kbps = kbpsChoices.sortedDescending().firstOrNull { isWhole(ListeningCacheKeys.transcode(serverId, trackKey, it)) }
        return CachedVersions(original = false, transcodeKbps = kbps)
    }

    /** A read of [key] ended: once an original is whole, the transcodes of that track go. */
    fun onClosed(key: String) {
        if (ListeningCacheKeys.isOriginal(key) && ListeningCache.isWhole(cache(), key)) dropTranscodes(key)
    }

    /** Every playlist and segment of the transcode under [prefix] is in the cache, from one play. */
    @Synchronized
    fun isWhole(prefix: String): Boolean = runCatching {
        val entry = entryKey(prefix) ?: return false
        val session = meta(entry, META_SESSION) ?: return false
        val path = meta(entry, META_PATH) ?: return false
        val base = BASE + path
        var playlist = parse(entry, base) ?: return false
        if (playlist is HlsMultivariantPlaylist) {
            val variant = playlist.variants.firstOrNull()?.url?.toString() ?: return false
            val key = keyIn(prefix, variant, session) ?: return false
            playlist = parse(key, variant) ?: return false
        }
        val media = playlist as? HlsMediaPlaylist ?: return false
        media.hasEndTag && media.segments.isNotEmpty() && media.segments.all { segment ->
            val key = keyIn(prefix, UriUtil.resolve(media.baseUri, segment.url), session)
            key != null && ListeningCache.isWhole(cache(), key)
        }
    }.getOrDefault(false)

    private fun keyIn(prefix: String, url: String, session: String): String? =
        TranscodeUrls.partOf(url, session)?.let { ListeningCacheKeys.part(prefix, it) }

    private fun parse(key: String, uri: String) = read(key)?.let {
        HlsPlaylistParser().parse(Uri.parse(uri), it.inputStream())
    }

    private fun read(key: String): ByteArray? {
        if (!ListeningCache.isWhole(cache(), key)) return null
        val source = CacheDataSource(cache(), null)
        return try {
            source.open(DataSpec.Builder().setUri(BASE).setKey(key).build())
            DataSourceUtil.readToEnd(source)
        } finally {
            source.close()
        }
    }

    private fun entryKey(prefix: String): String? =
        cache().keys.firstOrNull { it.startsWith("$prefix/") && meta(it, META_SESSION) != null }

    private fun meta(key: String, name: String): String? = cache().getContentMetadata(key).get(name, null as String?)

    private fun removeAll(prefix: String) {
        cache().keys.filter { it.startsWith("$prefix/") }.forEach(cache()::removeResource)
    }

    private fun dropTranscodes(originalKey: String) {
        val transcodes = ListeningCacheKeys.transcodesOf(originalKey)
        cache().keys.filter { it.startsWith(transcodes) }.forEach(cache()::removeResource)
    }

    private companion object {
        const val MAX_PLAYS = 2_000
        const val META_SESSION = "sillon-session"
        const val META_PATH = "sillon-path"

        /** Cached playlists are parsed against a placeholder host: only the path matters to their links. */
        const val BASE = "http://cache.invalid"
    }
}

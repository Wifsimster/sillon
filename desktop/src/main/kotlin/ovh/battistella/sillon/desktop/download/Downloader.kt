package ovh.battistella.sillon.desktop.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.PlexRepository
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class DownloadFiles(private val root: File) {
    fun musicDir(serverId: String): File = File(root, "music/${safe(serverId)}")

    fun target(serverId: String, ratingKey: String, partKey: String): File {
        val extension = partKey.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "audio"
        return File(musicDir(serverId), "${safe(ratingKey)}.$extension")
    }

    fun partFile(serverId: String, ratingKey: String): File = File(musicDir(serverId), "${safe(ratingKey)}.part")

    fun artwork(serverId: String, albumKey: String): File = File(root, "artwork/${safe(serverId)}/${safe(albumKey)}.jpg")

    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

class Downloader(
    private val dao: DownloadDao,
    private val repository: PlexRepository,
    private val client: OkHttpClient,
    private val files: DownloadFiles,
    private val scope: CoroutineScope,
) {
    private var worker: Job? = null

    fun observe(serverId: String): Flow<List<DownloadEntity>> = dao.observe(serverId)

    suspend fun download(tracks: List<PlexMetadata>) {
        val serverId = repository.currentServerId()
        val complete = tracks.mapNotNull { track ->
            if (track.partKey != null) track else runCatching { repository.metadata(track.ratingKey) }.getOrNull()
        }
        val loudness = runCatching { repository.loudness(complete) }.getOrDefault(emptyMap())
        val rows = complete.mapNotNull { track ->
            val partKey = track.partKey ?: return@mapNotNull null
            DownloadEntity(
                serverId = serverId,
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.trackArtist,
                album = track.parentTitle,
                albumKey = track.parentRatingKey,
                albumArtist = track.grandparentTitle,
                trackNumber = track.index,
                discNumber = track.parentIndex,
                durationMs = track.duration,
                partKey = partKey,
                thumb = track.parentThumb ?: track.thumb,
                state = DownloadState.QUEUED,
                gainDb = loudness[track.ratingKey]?.track,
                albumGainDb = loudness[track.ratingKey]?.album,
            )
        }
        dao.insert(rows)
        schedule()
    }

    suspend fun delete(serverId: String, ratingKeys: List<String>) {
        if (ratingKeys.isEmpty()) return
        dao.get(serverId, ratingKeys).forEach { download ->
            download.filePath?.let { File(it).delete() }
            files.partFile(serverId, download.ratingKey).delete()
        }
        dao.delete(serverId, ratingKeys)
    }

    suspend fun retryFailed() {
        dao.retryFailed()
        schedule()
    }

    @Synchronized
    fun schedule() {
        if (worker?.isActive == true) return
        worker = scope.launch(Dispatchers.IO) { runCatching { drain() } }
    }

    suspend fun resume() {
        dao.all().filter { it.state == DownloadState.DOWNLOADING }.forEach {
            dao.setState(it.serverId, it.ratingKey, DownloadState.QUEUED, 0)
        }
        if (dao.next() != null) schedule()
    }

    private suspend fun drain() {
        val server = runCatching { repository.endpoint() }.getOrNull() ?: return
        val serverId = repository.currentServerId()
        var attempts = 0
        while (true) {
            coroutineContext.ensureActive()
            val download = dao.next() ?: break
            if (download.serverId != serverId) {
                dao.setState(download.serverId, download.ratingKey, DownloadState.FAILED, 0)
                continue
            }
            try {
                fetch(download, server)
                attempts = 0
            } catch (e: PlexHttpException) {
                dao.setState(download.serverId, download.ratingKey, DownloadState.FAILED, 0)
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                attempts++
                if (attempts >= MAX_ATTEMPTS) {
                    dao.setState(download.serverId, download.ratingKey, DownloadState.FAILED, 0)
                    attempts = 0
                } else {
                    dao.setState(download.serverId, download.ratingKey, DownloadState.QUEUED, 0)
                    kotlinx.coroutines.delay(RETRY_MS * attempts)
                }
            }
        }
    }

    private suspend fun fetch(download: DownloadEntity, server: ServerEndpoint) = withContext(Dispatchers.IO) {
        dao.setState(download.serverId, download.ratingKey, DownloadState.DOWNLOADING, 0)
        val target = files.target(download.serverId, download.ratingKey, download.partKey)
        val part = files.partFile(download.serverId, download.ratingKey)
        part.parentFile?.mkdirs()
        val request = Request.Builder().url(repository.streamUrl(server, download.partKey)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code in 400..499) throw PlexHttpException(response.code, response.message)
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength()
            var written = 0L
            var lastProgress = -1
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = (written * 100 / total).toInt()
                            if (progress >= lastProgress + PROGRESS_STEP) {
                                lastProgress = progress
                                dao.setState(download.serverId, download.ratingKey, DownloadState.DOWNLOADING, progress)
                            }
                        }
                    }
                }
            }
            if (total > 0 && written != total) throw IOException("Truncated: $written of $total bytes")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not move ${part.name}")
        val artwork = download.albumKey?.let { albumKey ->
            files.artwork(download.serverId, albumKey).takeIf { it.isFile || fetchArtwork(download, server, it) }
        }
        dao.complete(download.serverId, download.ratingKey, target.absolutePath, target.length(), artwork?.absolutePath)
    }

    private fun fetchArtwork(download: DownloadEntity, server: ServerEndpoint, file: File): Boolean {
        val url = repository.artworkUrl(server, download.thumb, ARTWORK_PX) ?: return false
        return runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use false
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".part")
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val RETRY_MS = 5_000L
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP = 5
        const val ARTWORK_PX = 600
    }
}

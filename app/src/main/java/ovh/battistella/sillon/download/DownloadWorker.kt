package ovh.battistella.sillon.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.PlexRepository
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Downloads queued tracks one after the other, until the queue is empty. Runs
 * as a foreground worker with a progress notification so a long album isn't
 * cut short when the app goes to the background.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val repository: PlexRepository,
    private val client: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val server = runCatching { repository.endpoint() }.getOrNull() ?: return@withContext Result.success()
        val serverId = repository.currentServerId()
        var done = 0
        while (true) {
            coroutineContext.ensureActive()
            val download = dao.next() ?: break
            if (download.serverId != serverId) {
                // Queued from another server: it can't be fetched from this one.
                dao.setState(download.serverId, download.ratingKey, DownloadState.FAILED, 0)
                continue
            }
            runCatching { setForeground(foregroundInfo(download.title, done)) }
            try {
                fetch(download, server)
                done++
            } catch (e: PlexHttpException) {
                // The server refused (track removed, access revoked): retrying won't help.
                dao.setState(download.serverId, download.ratingKey, DownloadState.FAILED, 0)
            } catch (e: IOException) {
                // Network trouble. If the worker was stopped (Wi-Fi lost), this
                // rethrows the cancellation; otherwise retry the queue later.
                coroutineContext.ensureActive()
                dao.setState(download.serverId, download.ratingKey, DownloadState.QUEUED, 0)
                return@withContext if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    dao.setState(download.serverId, download.ratingKey, DownloadState.FAILED, 0)
                    Result.failure()
                }
            }
        }
        Result.success()
    }

    private suspend fun fetch(download: DownloadEntity, server: ServerEndpoint) {
        val context = applicationContext
        dao.setState(download.serverId, download.ratingKey, DownloadState.DOWNLOADING, 0)
        val target = DownloadFiles.target(context, download.serverId, download.ratingKey, download.partKey)
        val part = DownloadFiles.partFile(context, download.serverId, download.ratingKey)
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
            // A connection closed early must not pass for a complete file.
            if (total > 0 && written != total) throw IOException("Truncated: $written of $total bytes")
        }
        if (!part.renameTo(target)) throw IOException("Could not move ${part.name}")

        val artwork = download.albumKey?.let { albumKey ->
            DownloadFiles.artwork(context, download.serverId, albumKey).takeIf { it.isFile || fetchArtwork(download, server, it) }
        }
        dao.complete(download.serverId, download.ratingKey, target.absolutePath, target.length(), artwork?.absolutePath)
    }

    /** Best effort: a missing cover never fails the track. */
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

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null, 0)

    private fun foregroundInfo(title: String?, done: Int): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.downloads_in_progress))
            .setContentText(title)
            .setSubText(if (done > 0) context.getString(R.string.downloads_done_count, done) else null)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4_001
        const val MAX_ATTEMPTS = 5
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP = 5
        const val ARTWORK_PX = 600
    }
}

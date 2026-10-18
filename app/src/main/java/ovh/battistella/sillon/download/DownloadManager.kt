package ovh.battistella.sillon.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.settings.SettingsStore
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val repository: PlexRepository,
    private val settings: SettingsStore,
) {
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
            DownloadFiles.partFile(context, serverId, download.ratingKey).delete()
        }
        dao.delete(serverId, ratingKeys)
    }

    suspend fun retryFailed() {
        dao.retryFailed()
        schedule()
    }

    suspend fun schedule(replace: Boolean = false) {
        val wifiOnly = settings.downloadOnWifiOnly.first()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (!wifiOnly) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    suspend fun setWifiOnly(value: Boolean) {
        settings.setDownloadOnWifiOnly(value)
        if (dao.next() != null) schedule(replace = true)
    }

    private companion object {
        const val WORK_NAME = "downloads"
    }
}

object DownloadFiles {
    fun musicDir(context: Context, serverId: String): File =
        File(context.filesDir, "music/${safe(serverId)}")

    fun target(context: Context, serverId: String, ratingKey: String, partKey: String): File {
        val extension = partKey.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "audio"
        return File(musicDir(context, serverId), "${safe(ratingKey)}.$extension")
    }

    fun partFile(context: Context, serverId: String, ratingKey: String): File =
        File(musicDir(context, serverId), "${safe(ratingKey)}.part")

    fun artwork(context: Context, serverId: String, albumKey: String): File =
        File(context.filesDir, "artwork/${safe(serverId)}/${safe(albumKey)}.jpg")

    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

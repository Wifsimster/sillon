package ovh.battistella.sillon.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.local.DownloadState
import java.io.File

@Serializable
private data class StoredDownload(
    val serverId: String,
    val ratingKey: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumKey: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMs: Long? = null,
    val partKey: String,
    val thumb: String? = null,
    val state: String,
    val progress: Int = 0,
    val sizeBytes: Long = 0,
    val filePath: String? = null,
    val artworkPath: String? = null,
    val addedAt: Long = 0,
    val gainDb: Float? = null,
    val albumGainDb: Float? = null,
) {
    fun toEntity() = DownloadEntity(
        serverId, ratingKey, title, artist, album, albumKey, albumArtist, trackNumber, discNumber, durationMs, partKey, thumb,
        DownloadState.entries.firstOrNull { it.name == state } ?: DownloadState.FAILED,
        progress, sizeBytes, filePath, artworkPath, addedAt, gainDb, albumGainDb,
    )

    companion object {
        fun of(e: DownloadEntity) = StoredDownload(
            e.serverId, e.ratingKey, e.title, e.artist, e.album, e.albumKey, e.albumArtist, e.trackNumber, e.discNumber, e.durationMs,
            e.partKey, e.thumb, e.state.name, e.progress, e.sizeBytes, e.filePath, e.artworkPath, e.addedAt, e.gainDb, e.albumGainDb,
        )
    }
}

class JsonDownloadDao(private val file: File) : DownloadDao {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val rows = MutableStateFlow(read())

    private fun read(): List<DownloadEntity> = runCatching {
        json.decodeFromString(ListSerializer(StoredDownload.serializer()), file.readText()).map { it.toEntity() }
    }.getOrDefault(emptyList())

    private suspend fun change(persist: Boolean = true, transform: (List<DownloadEntity>) -> List<DownloadEntity>) = lock.withLock {
        val next = transform(rows.value)
        if (next == rows.value) return@withLock
        rows.value = next
        if (persist) write(next)
    }

    private suspend fun write(list: List<DownloadEntity>) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(ListSerializer(StoredDownload.serializer()), list.map(StoredDownload::of)))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private val libraryOrder = compareBy<DownloadEntity, String?>(nullsFirst()) { it.albumArtist }
        .thenBy(nullsFirst()) { it.album }
        .thenBy(nullsFirst()) { it.discNumber }
        .thenBy(nullsFirst()) { it.trackNumber }
        .thenBy { it.title }

    private val albumOrder = compareBy<DownloadEntity, Int?>(nullsFirst()) { it.discNumber }
        .thenBy(nullsFirst()) { it.trackNumber }
        .thenBy { it.title }

    override fun observe(serverId: String): Flow<List<DownloadEntity>> =
        rows.map { all -> all.filter { it.serverId == serverId }.sortedWith(libraryOrder) }.distinctUntilChanged()

    override suspend fun get(serverId: String, keys: List<String>): List<DownloadEntity> {
        val wanted = keys.toSet()
        return rows.value.filter { it.serverId == serverId && it.ratingKey in wanted }
    }

    override suspend fun album(serverId: String, albumKey: String): List<DownloadEntity> =
        rows.value.filter { it.serverId == serverId && it.albumKey == albumKey }.sortedWith(albumOrder)

    override suspend fun next(): DownloadEntity? = rows.value
        .filter { it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING }
        .sortedWith(compareBy<DownloadEntity> { it.addedAt }.thenBy(nullsFirst()) { it.discNumber }.thenBy(nullsFirst()) { it.trackNumber })
        .firstOrNull()

    override suspend fun all(): List<DownloadEntity> = rows.value

    override suspend fun insert(downloads: List<DownloadEntity>) = change { all ->
        val present = all.map { it.serverId to it.ratingKey }.toSet()
        all + downloads.filter { (it.serverId to it.ratingKey) !in present }.distinctBy { it.serverId to it.ratingKey }
    }

    override suspend fun setState(serverId: String, ratingKey: String, state: DownloadState, progress: Int) =
        change(persist = state != DownloadState.DOWNLOADING || progress == 0) { all ->
            all.map { if (it.serverId == serverId && it.ratingKey == ratingKey) it.copy(state = state, progress = progress) else it }
        }

    override suspend fun complete(serverId: String, ratingKey: String, filePath: String, sizeBytes: Long, artworkPath: String?) = change { all ->
        all.map {
            if (it.serverId == serverId && it.ratingKey == ratingKey) {
                it.copy(state = DownloadState.DONE, progress = 100, filePath = filePath, sizeBytes = sizeBytes, artworkPath = artworkPath)
            } else {
                it
            }
        }
    }

    override suspend fun retryFailed() = change { all ->
        all.map { if (it.state == DownloadState.FAILED) it.copy(state = DownloadState.QUEUED, progress = 0) else it }
    }

    override suspend fun delete(serverId: String, keys: List<String>) {
        val wanted = keys.toSet()
        change { all -> all.filterNot { it.serverId == serverId && it.ratingKey in wanted } }
    }
}

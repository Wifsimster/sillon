package ovh.battistella.sillon.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class DownloadState { QUEUED, DOWNLOADING, DONE, FAILED }

@Entity(
    tableName = "downloads",
    primaryKeys = ["serverId", "ratingKey"],
    indices = [Index("serverId", "albumKey"), Index("state")],
)
data class DownloadEntity(
    val serverId: String,
    val ratingKey: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumKey: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val partKey: String,
    val thumb: String?,
    val state: DownloadState,
    val progress: Int = 0,
    val sizeBytes: Long = 0,
    val filePath: String? = null,
    val artworkPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val gainDb: Float? = null,
    val albumGainDb: Float? = null,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE serverId = :serverId ORDER BY albumArtist, album, discNumber, trackNumber, title")
    fun observe(serverId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND ratingKey IN (:keys)")
    suspend fun get(serverId: String, keys: List<String>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND albumKey = :albumKey ORDER BY discNumber, trackNumber, title")
    suspend fun album(serverId: String, albumKey: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED', 'DOWNLOADING') ORDER BY addedAt, discNumber, trackNumber LIMIT 1")
    suspend fun next(): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(downloads: List<DownloadEntity>)

    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE serverId = :serverId AND ratingKey = :ratingKey")
    suspend fun setState(serverId: String, ratingKey: String, state: DownloadState, progress: Int)

    @Query(
        "UPDATE downloads SET state = 'DONE', progress = 100, filePath = :filePath, sizeBytes = :sizeBytes, " +
            "artworkPath = :artworkPath WHERE serverId = :serverId AND ratingKey = :ratingKey",
    )
    suspend fun complete(serverId: String, ratingKey: String, filePath: String, sizeBytes: Long, artworkPath: String?)

    @Query("UPDATE downloads SET state = 'QUEUED', progress = 0 WHERE state = 'FAILED'")
    suspend fun retryFailed()

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND ratingKey IN (:keys)")
    suspend fun delete(serverId: String, keys: List<String>)
}

@Database(entities = [DownloadEntity::class], version = 2, exportSchema = true)
abstract class SillonDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `gainDb` REAL")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `albumGainDb` REAL")
    }
}

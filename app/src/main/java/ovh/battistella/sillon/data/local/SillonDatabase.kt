package ovh.battistella.sillon.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

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

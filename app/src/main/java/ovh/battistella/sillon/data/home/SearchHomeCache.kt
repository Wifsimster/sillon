package ovh.battistella.sillon.data.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The empty search page on disk, one file per server and account (see SleeveWearCache.ownerOf), removed on sign-out. */
@Singleton
class SearchHomeCache @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.noBackupFilesDir, "search-home")

    suspend fun read(owner: String): CachedHome? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(CachedHome.serializer(), file(owner).readText()) }
            .getOrNull()
            ?.takeIf { it.owner == owner }
    }

    suspend fun write(cached: CachedHome) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(cached.owner)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(json.encodeToString(CachedHome.serializer(), cached))
            temp.renameTo(target)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { dir.deleteRecursively() }
    }

    private fun file(owner: String): File = File(dir, owner.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
}

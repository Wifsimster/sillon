package ovh.battistella.sillon.data.genres

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The genres of the library on disk, next to the search page's rows: one file per server and account
 * (see SleeveWearCache.ownerOf), removed on sign-out. Album keys, names and server paths only, never a token.
 */
@Singleton
class GenreCache @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.noBackupFilesDir, "search-genres")

    suspend fun read(owner: String): CachedGenres? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(CachedGenres.serializer(), file(owner).readText()) }
            .getOrNull()
            ?.takeIf { it.owner == owner }
    }

    suspend fun write(cached: CachedGenres) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(cached.owner)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(json.encodeToString(CachedGenres.serializer(), cached))
            temp.renameTo(target)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { dir.deleteRecursively() }
    }

    private fun file(owner: String): File = File(dir, owner.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
}

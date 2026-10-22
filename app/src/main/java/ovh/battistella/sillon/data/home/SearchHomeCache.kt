package ovh.battistella.sillon.data.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The empty search page on disk, one file per server and account (see SleeveWearCache.ownerOf), removed
 * on sign-out. The sealed mystery albums live in a second file that sign-out keeps: the server still
 * knows when they were played, so forgetting them would show a surprise in Recently played on the next
 * sign-in. That file holds album keys only, never a token.
 */
@Singleton
class SearchHomeCache @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.noBackupFilesDir, "search-home")
    private val sealedDir get() = File(context.noBackupFilesDir, "search-home-sealed")
    private val keys = SetSerializer(String.serializer())

    suspend fun read(owner: String): CachedHome? = withContext(Dispatchers.IO) {
        val cached = runCatching { json.decodeFromString(CachedHome.serializer(), file(dir, owner).readText()) }
            .getOrNull()
            ?.takeIf { it.owner == owner }
        val sealed = readSealed(owner)
        when {
            cached != null -> cached.copy(sealed = cached.sealed + sealed)
            sealed.isNotEmpty() -> CachedHome(owner, sealed = sealed)
            else -> null
        }
    }

    suspend fun write(cached: CachedHome) = withContext(Dispatchers.IO) {
        runCatching {
            writeAtomically(file(dir, cached.owner), json.encodeToString(CachedHome.serializer(), cached))
            writeAtomically(file(sealedDir, cached.owner), json.encodeToString(keys, cached.sealed))
        }
    }

    /** Sign-out: the rows go, the sealed albums stay. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { dir.deleteRecursively() }
    }

    private fun readSealed(owner: String): Set<String> =
        runCatching { json.decodeFromString(keys, file(sealedDir, owner).readText()) }.getOrDefault(emptySet())

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        temp.renameTo(target)
    }

    private fun file(root: File, owner: String): File = File(root, owner.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
}

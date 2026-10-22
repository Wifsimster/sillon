package ovh.battistella.sillon.data.wear

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ovh.battistella.sillon.data.session.Session
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedPlays(val owner: String, val sectionKey: String, val albums: Map<String, AlbumPlays>)

/** Play counts on disk, one file per server and account, removed on sign-out. */
@Singleton
class SleeveWearCache @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.noBackupFilesDir, "sleeve-wear")

    suspend fun read(owner: String): CachedPlays? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(CachedPlays.serializer(), file(owner).readText()) }
            .getOrNull()
            ?.takeIf { it.owner == owner }
    }

    suspend fun write(cached: CachedPlays) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(cached.owner)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(json.encodeToString(CachedPlays.serializer(), cached))
            temp.renameTo(target)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { dir.deleteRecursively() }
    }

    private fun file(owner: String): File = File(dir, owner.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    companion object {
        /**
         * Server plus account. Jellyfin has a user id; Plex only gives the account token here, so the key
         * holds a short hash of it, never the token itself.
         */
        fun ownerOf(session: Session): String? {
            val ready = session as? Session.Ready ?: return null
            val user = ready.server.endpoint.userId ?: sha256(ready.userToken).take(USER_HASH_CHARS)
            return "${ready.server.machineId}-$user"
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        private const val USER_HASH_CHARS = 16
    }
}

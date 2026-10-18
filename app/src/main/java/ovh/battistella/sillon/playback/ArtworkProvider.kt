package ovh.battistella.sillon.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import ovh.battistella.sillon.BuildConfig
import ovh.battistella.sillon.data.repository.PlexRepository
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Base64

/**
 * Serves cover art to Android Auto and other media browsers, which only load
 * browse-item artwork from `content://` URIs.
 *
 * A URI names a cover by its server reference (a Plex thumb path or a
 * Jellyfin item), never by a URL: the token stays in the app, which builds the
 * real URL and downloads the image here. The provider isn't exported; the
 * library service grants each URI to the browser it hands it to.
 */
class ArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): PlexRepository
        fun okHttpClient(): OkHttpClient
    }

    private val deps by lazy {
        EntryPointAccessors.fromApplication(checkNotNull(context).applicationContext, Dependencies::class.java)
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val thumb = thumbOf(uri) ?: throw FileNotFoundException("Not a cover: $uri")
        val file = cached(thumb)
        if (!file.isFile) download(thumb, file)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun cached(thumb: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(thumb.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(checkNotNull(context).cacheDir, "auto_artwork/$name.jpg")
    }

    private fun download(thumb: String, file: File) {
        val url = runBlocking {
            runCatching { deps.repository().run { artworkUrl(endpoint(), thumb, SIZE_PX) } }.getOrNull()
        } ?: throw FileNotFoundException("No server for $thumb")
        try {
            deps.okHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) throw FileNotFoundException("HTTP ${response.code}")
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".part")
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "cover download failed: ${e.message}")
            throw FileNotFoundException(e.message)
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    // Read-only: covers can only be opened.
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "SillonArtwork"
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.artwork"

        /** Covers in the car are small; one size keeps the cache simple. */
        private const val SIZE_PX = 400

        /** A content URI for a cover reference, or null when there's none. */
        fun uriFor(thumb: String?): Uri? {
            if (thumb.isNullOrBlank()) return null
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(thumb.toByteArray())
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("cover").appendPath(encoded).build()
        }

        fun thumbOf(uri: Uri): String? {
            if (uri.authority != AUTHORITY || uri.pathSegments.size != 2 || uri.pathSegments[0] != "cover") return null
            return runCatching { String(Base64.getUrlDecoder().decode(uri.pathSegments[1])) }.getOrNull()
        }

        /** Let [packageName] (a trusted media browser) open the covers of [items]. */
        fun grant(context: Context, packageName: String, uris: List<Uri>) {
            uris.filter { it.authority == AUTHORITY }.forEach {
                context.grantUriPermission(packageName, it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}

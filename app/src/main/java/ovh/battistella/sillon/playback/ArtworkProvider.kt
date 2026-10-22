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
import ovh.battistella.sillon.playback.mystery.SleeveArt
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Base64

class ArtworkProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): PlexRepository
        fun okHttpClient(): OkHttpClient
        fun sleeveArt(): SleeveArt
    }

    private val deps by lazy {
        EntryPointAccessors.fromApplication(checkNotNull(context).applicationContext, Dependencies::class.java)
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri == SLEEVE_URI) {
            val sleeve = runCatching { deps.sleeveArt().file() }.getOrElse { throw FileNotFoundException("No sleeve") }
            return ParcelFileDescriptor.open(sleeve, ParcelFileDescriptor.MODE_READ_ONLY)
        }
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

    override fun getType(uri: Uri): String = if (uri == SLEEVE_URI) "image/png" else "image/jpeg"

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "SillonArtwork"
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.artwork"

        private const val SIZE_PX = 400

        val SLEEVE_URI: Uri = Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("sleeve").appendPath("v1").build()

        fun uriFor(thumb: String?): Uri? {
            if (thumb.isNullOrBlank()) return null
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(thumb.toByteArray())
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("cover").appendPath(encoded).build()
        }

        fun thumbOf(uri: Uri): String? {
            if (uri.authority != AUTHORITY || uri.pathSegments.size != 2 || uri.pathSegments[0] != "cover") return null
            return runCatching { String(Base64.getUrlDecoder().decode(uri.pathSegments[1])) }.getOrNull()
        }

        fun grant(context: Context, packageName: String, uris: List<Uri>) {
            uris.filter { it.authority == AUTHORITY }.forEach {
                context.grantUriPermission(packageName, it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}

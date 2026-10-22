package ovh.battistella.sillon.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import ovh.battistella.sillon.BuildConfig
import java.io.File

/**
 * Share cards live in the app's cache, in their own folder, and only as long as a share needs them.
 *
 * A card is a PNG written straight from the pixels: no EXIF, no text chunks, and a file name that
 * says nothing but "sillon-card".
 */
object CardFiles {
    const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.cards"
    private const val DIR = "share_cards"
    private const val MAX_AGE_MS = 60 * 60 * 1000L

    fun dir(context: Context): File = File(context.cacheDir, DIR)

    /** Writes the card, after removing every older one: only the card being shared stays in the cache. */
    fun write(context: Context, card: Bitmap, now: Long = System.currentTimeMillis()): File {
        val dir = dir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "sillon-card-$now.png")
        val tmp = File(dir, "${file.name}.part")
        tmp.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file)
        return file
    }

    /** Removes cards older than an hour; a share sheet has long finished reading them by then. */
    fun purge(context: Context, now: Long = System.currentTimeMillis(), maxAgeMs: Long = MAX_AGE_MS) {
        dir(context).listFiles()?.filter { now - it.lastModified() >= maxAgeMs }?.forEach { it.delete() }
    }

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, AUTHORITY, file)

    /** The system share sheet with the image alone: no text, no link. */
    fun shareIntent(uri: Uri, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

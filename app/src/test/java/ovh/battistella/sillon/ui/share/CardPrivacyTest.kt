package ovh.battistella.sillon.ui.share

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.io.File

/**
 * The image that leaves the phone: drawn from text and pixels only, written without metadata,
 * and named without anything that could point to the server.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class CardPrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val renderer = CardRenderer(context)

    // What a signed-in session knows; none of it may end up in the card.
    private val serverUrl = "https://music.synthetic-home.example:32400"
    private val token = "SYNTHETIC-TOKEN-abc123"
    private val serverName = "Synthetic Living Room NAS"
    private val coverUrl = "$serverUrl/photo/:/transcode?width=900&url=%2Flibrary%2Fmetadata%2F42%2Fthumb&X-Plex-Token=$token"

    private val text = CardText(
        title = "Synthetic Album",
        artist = "Synthetic Artist",
        detail = "1979",
        stars = 4.5f,
        note = "“Our summer record”",
        lyrics = listOf("A synthetic first line", "And a second one"),
    )

    private fun card(template: CardTemplate = CardTemplate.VINYL, format: CardFormat = CardFormat.SQUARE): File {
        val cover = syntheticCover()
        val bitmap = renderer.render(renderer.layout(format, template, text), cover, CardPalette.of(cover))
        return CardFiles.write(context, bitmap)
    }

    @Test
    fun `the shared file is a PNG with no EXIF`() {
        val file = card()
        val exif = ExifInterface(file.absolutePath)
        EXIF_TAGS.forEach { tag -> assertNull("EXIF $tag", exif.getAttribute(tag)) }
        assertFalse(exif.hasThumbnail())
        assertFalse(exif.getLatLong(FloatArray(2)))
    }

    @Test
    fun `the PNG carries pixels only, no text or EXIF chunks`() {
        CardTemplate.entries.forEach { template ->
            CardFormat.entries.forEach { format ->
                val chunks = pngChunks(card(template, format))
                assertEquals("IHDR", chunks.first())
                assertEquals("IEND", chunks.last())
                val extra = chunks.toSet() - setOf("IHDR", "IDAT", "IEND", "sRGB", "gAMA", "cHRM", "pHYs", "sBIT", "bKGD")
                assertTrue("unexpected chunks $extra in $template/$format", extra.isEmpty())
            }
        }
    }

    @Test
    fun `no server URL, token or server name in the file or its name`() {
        val file = card(CardTemplate.POLAROID, CardFormat.STORY)
        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
        listOf(serverUrl, "synthetic-home", token, serverName, "X-Plex-Token", "http", coverUrl).forEach {
            assertFalse("'$it' in the file", bytes.contains(it, ignoreCase = true))
        }
        assertTrue(file.name.matches(Regex("""sillon-card-\d+\.png""")))
        assertEquals(CardFiles.dir(context), file.parentFile)
    }

    @Test
    fun `the card's text comes only from the music`() {
        val layout = renderer.layout(CardFormat.STORY, CardTemplate.LABEL, text)
        val drawn = layout.text.joinToString("\n")
        listOf(serverUrl, token, serverName, "http", "://").forEach { assertFalse(drawn.contains(it)) }
        assertEquals(text.lines.joinToString("\n"), drawn)
    }

    @Test
    fun `the share intent carries the image alone`() {
        val uri = android.net.Uri.parse("content://${CardFiles.AUTHORITY}/cards/sillon-card-1.png")
        val chooser = CardFiles.shareIntent(uri, "Share a card")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals("image/png", send.type)
        assertNull(send.getStringExtra(android.content.Intent.EXTRA_TEXT))
        assertNull(send.getStringExtra(android.content.Intent.EXTRA_SUBJECT))
        assertTrue(send.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `only the latest card stays in the cache, and old ones are purged`() {
        val first = card()
        Thread.sleep(5)
        val second = CardFiles.write(context, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), now = System.currentTimeMillis() + 1)
        assertFalse(first.exists())
        assertTrue(second.exists())
        CardFiles.purge(context, now = second.lastModified() + 60 * 60 * 1000L)
        assertFalse(second.exists())
        assertEquals(0, CardFiles.dir(context).listFiles()?.size ?: 0)
    }

    private fun pngChunks(file: File): List<String> = DataInputStream(file.inputStream().buffered()).use { input ->
        val signature = ByteArray(8).also { input.readFully(it) }
        assertTrue(signature.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        val chunks = mutableListOf<String>()
        while (true) {
            val length = input.readInt()
            val type = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            chunks += type
            input.skipBytes(length + 4)
            if (type == "IEND") break
        }
        chunks
    }

    private fun syntheticCover(): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF1E3A5F.toInt())
        canvas.drawCircle(180f, 130f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 163, 61) })
        return bitmap
    }

    private companion object {
        val EXIF_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_MAKER_NOTE, ExifInterface.TAG_DATETIME_DIGITIZED,
        )
    }
}

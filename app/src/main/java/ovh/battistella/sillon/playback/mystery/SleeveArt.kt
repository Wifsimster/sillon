package ovh.battistella.sillon.playback.mystery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ovh.battistella.sillon.R
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

object SleeveColors {
    const val KRAFT = 0xFFC4A07A.toInt()
    const val KRAFT_SHADE = 0xFFB08B64.toInt()
    const val LABEL = 0xFFE8A33D.toInt()
    const val VINYL = 0xFF15120F.toInt()
}

@Singleton
class SleeveArt @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val png: ByteArray by lazy { render() }

    val uri: Uri? by lazy {
        runCatching {
            val file = File(context.cacheDir, "mystery/sleeve-v1.png")
            if (!file.isFile) {
                file.parentFile?.mkdirs()
                file.writeBytes(png)
            }
            Uri.fromFile(file)
        }.getOrNull()
    }

    private fun render(): ByteArray {
        val size = SIZE_PX.toFloat()
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(SleeveColors.KRAFT)
        paint.style = Paint.Style.STROKE
        paint.color = SleeveColors.KRAFT_SHADE
        paint.strokeWidth = size * 0.012f
        canvas.drawCircle(size / 2, size / 2, size * 0.44f, paint)
        paint.style = Paint.Style.FILL
        paint.color = SleeveColors.LABEL
        canvas.drawCircle(size / 2, size / 2, size * 0.19f, paint)
        paint.color = SleeveColors.VINYL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.24f
        paint.typeface = runCatching { ResourcesCompat.getFont(context, R.font.fraunces_semibold) }.getOrNull() ?: Typeface.SERIF
        val baseline = size / 2 - (paint.descent() + paint.ascent()) / 2
        canvas.drawText("?", size / 2, baseline, paint)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val SIZE_PX = 320
    }
}

package app.embeddingspace.explorer.ui.media

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import android.graphics.Bitmap
import java.io.File

/** Loads a picture to fit inside [maxPx], honouring the EXIF turn. Used for the full image preview. */
object ImageLoad {
    fun loadFit(path: String, maxPx: Int): ImageBitmap? = try {
        val f = File(path)
        if (!f.exists()) null else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            raw?.let { rotate(it, path).asImageBitmap() }
        }
    } catch (e: Throwable) {
        null
    }

    private fun rotate(b: Bitmap, path: String): Bitmap {
        val deg = try {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Throwable) {
            0f
        }
        if (deg == 0f) return b
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)
    }
}

package app.embeddingspace.explorer.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Small pre-drawn glyphs for the cards, so a frame only blits them. */
object Glyphs {
    /** White play triangle on a transparent square. */
    fun play(size: Int = 48): ImageBitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); pathEffect = null }
        val f = size / 32f
        val path = Path().apply { moveTo(11 * f, 6 * f); lineTo(27 * f, 16 * f); lineTo(11 * f, 26 * f); close() }
        Canvas(bmp).drawPath(path, p)
        return bmp.asImageBitmap()
    }
}

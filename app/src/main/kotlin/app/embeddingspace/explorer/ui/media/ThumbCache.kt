package app.embeddingspace.explorer.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Small square thumbnails, decoded once and kept. The map draws only a handful at a time, lists draw more. */
object ThumbCache {
    private val cache = LruCache<String, ImageBitmap>(400)
    private val failed = HashSet<String>()

    /** Square, centre cropped. */
    fun loadSquare(path: String, px: Int): ImageBitmap? = load("$path|$px|s", path, px, rounded = false)

    /** Square with rounded corners, for drawing on the map. */
    fun loadRounded(path: String, px: Int): ImageBitmap? = load("$path|$px|r", path, px, rounded = true)

    fun peekRounded(path: String, px: Int): ImageBitmap? = cache.get("$path|$px|r")

    /** Card picture: 256 by 208, centre cropped, rounded corners. The map scales it to the card. */
    fun loadCard(path: String): ImageBitmap? = load("$path|card", path, CARD_W, rounded = true, cardH = CARD_H)

    fun peekCard(path: String): ImageBitmap? = cache.get("$path|card")

    const val CARD_W = 256
    const val CARD_H = 208

    fun clear() {
        cache.evictAll(); failed.clear()
    }

    private fun load(key: String, path: String, px: Int, rounded: Boolean, cardH: Int = px): ImageBitmap? {
        cache.get(key)?.let { return it }
        if (synchronized(failed) { key in failed }) return null
        val bmp = try {
            decode(path, px, rounded, cardH)
        } catch (e: Throwable) {
            null
        }
        if (bmp == null) {
            synchronized(failed) { failed.add(key) }
            return null
        }
        val img = bmp.asImageBitmap()
        cache.put(key, img)
        return img
    }

    private fun decode(path: String, px: Int, rounded: Boolean, ph: Int): Bitmap? {
        val f = File(path)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minOf(px, ph)) sample *= 2
        val src = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // centre crop to the wanted aspect, then scale
        val want = px.toFloat() / ph
        var cw = src.width
        var ch = (cw / want).toInt()
        if (ch > src.height) { ch = src.height; cw = (ch * want).toInt() }
        val crop = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        val scaled = if (cw == px && ch == ph) crop else Bitmap.createScaledBitmap(crop, px, ph, true)
        if (!rounded) return scaled
        val out = Bitmap.createBitmap(px, ph, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val rad = if (px == ph) px * 0.22f else px * 0.13f
        Canvas(out).drawRoundRect(RectF(0f, 0f, px.toFloat(), ph.toFloat()), rad, rad, paint)
        return out
    }
}

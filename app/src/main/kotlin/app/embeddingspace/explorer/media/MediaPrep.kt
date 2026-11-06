package app.embeddingspace.explorer.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import app.embeddingspace.explorer.domain.audio.Pcm
import app.embeddingspace.explorer.domain.audio.PcmAudio
import app.embeddingspace.explorer.domain.audio.Wav
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** What the pipeline needs from one picked file: stored copies plus the bytes to embed. */
class PreparedImage(val mediaPath: String, val thumbPath: String, val jpeg: ByteArray, val title: String)

class PreparedVideo(val mediaPath: String, val thumbPath: String, val frames: List<ByteArray>, val durationMs: Long, val title: String)

class PreparedAudio(val mediaPath: String, val wav: ByteArray, val durationMs: Long, val title: String)

/** Copies picked files into app storage and makes thumbnails, frames and 16 kHz mono audio for the model. */
class MediaPrep(private val context: Context, private val dir: File) {
    init {
        dir.mkdirs()
    }

    /** Copies a content uri into [target]. Returns the file. */
    fun copyUri(uri: Uri, target: File): File {
        context.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(target).use { o -> i.copyTo(o) } }
            ?: error("cannot open $uri")
        return target
    }

    fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Throwable) {
        null
    }

    fun prepareImage(id: String, source: File, title: String): PreparedImage {
        val bmp = decodeUpright(source.absolutePath, 1600) ?: error("unreadable image")
        val media = File(dir, "$id.jpg")
        FileOutputStream(media).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val thumb = File(dir, "$id.thumb.jpg")
        FileOutputStream(thumb).use { squareThumb(bmp, 320).compress(Bitmap.CompressFormat.JPEG, 85, it) }
        // the model resizes anyway, so a modest size keeps this fast
        val small = scaleToLongEdge(bmp, 640)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (source.absolutePath != media.absolutePath && source.parentFile?.name == "pending") source.delete()
        return PreparedImage(media.absolutePath, thumb.absolutePath, out.toByteArray(), title)
    }

    /** Frames at 1 fps, at most [maxFrames], spread evenly over the clip. */
    fun prepareVideo(id: String, source: File, title: String, maxFrames: Int = 8): PreparedVideo {
        val media = File(dir, "$id.mp4")
        if (source.absolutePath != media.absolutePath) source.copyTo(media, overwrite = true)
        if (source.parentFile?.name == "pending") source.delete()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(media.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val seconds = (dur / 1000).toInt().coerceAtLeast(1)
            val count = minOf(seconds, maxFrames)
            val times = if (count <= 1) listOf(0L) else (0 until count).map { i -> (dur * 1000L * i / count) + 100_000L }
            val frames = ArrayList<ByteArray>()
            var first: Bitmap? = null
            for (t in times) {
                val f = r.getFrameAtTime(t.coerceAtMost(dur * 1000L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                if (first == null) first = f
                val small = scaleToLongEdge(f, 448)
                val out = ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 85, out)
                frames.add(out.toByteArray())
            }
            if (frames.isEmpty() || first == null) error("no frames")
            val thumb = File(dir, "$id.thumb.jpg")
            FileOutputStream(thumb).use { squareThumb(first, 320).compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return PreparedVideo(media.absolutePath, thumb.absolutePath, frames, dur, title)
        } finally {
            r.release()
        }
    }

    /** Keeps the original file for playback and makes 16 kHz mono WAV bytes (at most 5 minutes) for the model. */
    fun prepareAudio(id: String, source: File, title: String): PreparedAudio {
        val ext = source.extension.ifBlank { "m4a" }
        val media = File(dir, "$id.$ext")
        if (source.absolutePath != media.absolutePath) source.copyTo(media, overwrite = true)
        if (source.parentFile?.name == "pending") source.delete()
        val pcm: PcmAudio = AudioDecoder.forModel(media.absolutePath, 300) ?: error("unreadable audio")
        return PreparedAudio(media.absolutePath, Wav.encode(pcm), pcm.durationMs, title)
    }

    // ---------------------------------------------------------------- bitmaps

    private fun decodeUpright(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
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
        val turned = if (deg == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
        return scaleToLongEdge(turned, maxEdge)
    }

    private fun scaleToLongEdge(b: Bitmap, edge: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= edge) return b
        val f = edge.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun squareThumb(b: Bitmap, px: Int): Bitmap {
        val side = minOf(b.width, b.height)
        val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(sq, px, px, true)
    }
}

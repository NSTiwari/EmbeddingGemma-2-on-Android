package app.embeddingspace.explorer.ui.media

import android.util.LruCache
import app.embeddingspace.explorer.domain.audio.Waveform
import app.embeddingspace.explorer.media.AudioDecoder

/** Small waveforms for the audio cards on the map. Decoded once, off the main thread. */
object WaveCache {
    const val BARS = 20
    private val cache = LruCache<String, FloatArray>(300)

    fun peek(path: String): FloatArray? = cache.get(path)

    /** Decodes at most a minute of the file. Returns null if it cannot be read. */
    fun load(path: String): FloatArray? {
        cache.get(path)?.let { return it }
        val a = try { AudioDecoder.decodeFile(path, 60) } catch (e: Throwable) { null } ?: return null
        val peaks = Waveform.peaks(a.samples, BARS)
        cache.put(path, peaks)
        return peaks
    }

    /** A stand-in shape for a card whose audio has not been read yet. Same input, same bars. */
    fun placeholder(seed: Int, out: FloatArray = FloatArray(BARS)): FloatArray {
        var s = seed * 31 + 7
        for (i in out.indices) {
            s = s * 1103515245 + 12345
            out[i] = 0.25f + ((s ushr 16) and 0x7fff) / 32768f * 0.6f
        }
        return out
    }
}

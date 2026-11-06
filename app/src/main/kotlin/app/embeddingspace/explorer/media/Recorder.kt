package app.embeddingspace.explorer.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.embeddingspace.explorer.domain.audio.PcmAudio
import app.embeddingspace.explorer.domain.audio.Wav
import java.io.File
import kotlin.math.abs
import kotlin.concurrent.thread

/** Records the microphone as 16 kHz mono PCM and writes a WAV file when stopped. */
class Recorder {
    @Volatile private var running = false
    @Volatile var level = 0f
        private set
    @Volatile var elapsedMs = 0L
        private set
    private var worker: Thread? = null
    private var rec: AudioRecord? = null
    private val samples = ArrayList<ShortArray>()

    val isRecording: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        } catch (e: Throwable) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r
        samples.clear()
        elapsedMs = 0
        running = true
        r.startRecording()
        worker = thread(name = "recorder") {
            val buf = ShortArray(min)
            var total = 0L
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n <= 0) continue
                samples.add(buf.copyOf(n))
                total += n
                var peak = 0
                for (i in 0 until n) peak = maxOf(peak, abs(buf[i].toInt()))
                level = (peak / 32768f).coerceIn(0f, 1f)
                elapsedMs = total * 1000 / rate
                if (elapsedMs >= MAX_MS) running = false
            }
        }
        return true
    }

    /** Stops and writes the file. Returns null if nothing usable was recorded. */
    fun stop(target: File): File? {
        running = false
        worker?.join(1500)
        try { rec?.stop() } catch (_: Throwable) {}
        rec?.release()
        rec = null
        val total = samples.sumOf { it.size }
        if (total < 16_000 / 2) return null
        val all = ShortArray(total)
        var p = 0
        for (s in samples) { System.arraycopy(s, 0, all, p, s.size); p += s.size }
        samples.clear()
        target.writeBytes(Wav.encode(PcmAudio(all, 16_000)))
        return target
    }

    fun cancel() {
        running = false
        worker?.join(1000)
        try { rec?.stop() } catch (_: Throwable) {}
        rec?.release()
        rec = null
        samples.clear()
    }

    companion object {
        const val MAX_MS = 5 * 60 * 1000L
    }
}

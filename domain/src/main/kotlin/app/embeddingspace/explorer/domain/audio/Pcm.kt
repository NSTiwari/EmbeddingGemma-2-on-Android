package app.embeddingspace.explorer.domain.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Mono 16-bit samples at a known rate. */
class PcmAudio(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = if (sampleRate <= 0) 0 else samples.size * 1000L / sampleRate
}

/** Turns whatever the phone decodes into the 16 kHz mono PCM that the embedding model wants. */
object Pcm {
    const val TARGET_RATE = 16_000

    /** Averages the channels of interleaved samples. */
    fun interleavedToMono(data: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return data
        val frames = data.size / channels
        return ShortArray(frames) { f ->
            var s = 0
            for (c in 0 until channels) s += data[f * channels + c]
            (s / channels).toShort()
        }
    }

    /** Box filter when going down (keeps it from aliasing), linear interpolation when going up. */
    fun resample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        require(srcRate > 0 && dstRate > 0) { "rates must be positive" }
        if (input.isEmpty() || srcRate == dstRate) return input
        val outLen = max(1, (input.size.toLong() * dstRate / srcRate).toInt())
        val ratio = srcRate.toDouble() / dstRate
        val out = ShortArray(outLen)
        if (ratio > 1.0) {
            for (j in 0 until outLen) {
                val a = (j * ratio).toInt()
                val b = minOf(input.size, max(a + 1, ((j + 1) * ratio).toInt()))
                var s = 0L
                for (i in a until b) s += input[i]
                out[j] = (s / (b - a)).toInt().toShort()
            }
        } else {
            for (j in 0 until outLen) {
                val pos = j * ratio
                val i0 = pos.toInt().coerceAtMost(input.size - 1)
                val i1 = minOf(i0 + 1, input.size - 1)
                val f = pos - i0
                out[j] = (input[i0] * (1 - f) + input[i1] * f).toInt().toShort()
            }
        }
        return out
    }

    /** Interleaved samples of any rate to mono at 16 kHz. */
    fun toModelInput(data: ShortArray, channels: Int, rate: Int): PcmAudio =
        PcmAudio(resample(interleavedToMono(data, channels), rate, TARGET_RATE), TARGET_RATE)

    /** Keeps at most [maxSeconds] from the start. */
    fun cap(a: PcmAudio, maxSeconds: Int): PcmAudio {
        val limit = a.sampleRate * maxSeconds
        return if (a.samples.size <= limit) a else PcmAudio(a.samples.copyOf(limit), a.sampleRate)
    }

    fun peak(samples: ShortArray): Int {
        var m = 0
        for (s in samples) m = max(m, abs(s.toInt()))
        return m
    }
}

/** Minimal WAV reading and writing (16-bit PCM). */
object Wav {
    fun encode(a: PcmAudio): ByteArray {
        val data = a.samples.size * 2
        val buf = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        buf.putInt(a.sampleRate).putInt(a.sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data)
        for (s in a.samples) buf.putShort(s)
        return buf.array()
    }

    /** Parses a 16-bit PCM WAV into mono samples. Returns null for anything else. */
    fun parse(bytes: ByteArray): PcmAudio? {
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var pos = 12
        var channels = 1
        var rate = 16_000
        var bits = 16
        var format = 1
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            var size = buf.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size) size = bytes.size - body
            if (id == "fmt " && size >= 16) {
                format = buf.getShort(body).toInt()
                channels = buf.getShort(body + 2).toInt()
                rate = buf.getInt(body + 4)
                bits = buf.getShort(body + 14).toInt()
            } else if (id == "data") {
                if ((format != 1 && format != 0xFFFE) || bits != 16 || channels < 1) return null
                val n = size / 2
                val raw = ShortArray(n) { buf.getShort(body + it * 2) }
                return PcmAudio(Pcm.interleavedToMono(raw, channels), rate)
            }
            pos = body + size + (size and 1)
        }
        return null
    }
}

/** Drawing helpers for the audio preview. */
object Waveform {
    /** One value in 0..1 per bar: the loudest sample in that slice, scaled so the peak bar reaches 1. */
    fun peaks(samples: ShortArray, bars: Int): FloatArray {
        require(bars > 0) { "need at least one bar" }
        val out = FloatArray(bars)
        if (samples.isEmpty()) return out
        for (b in 0 until bars) {
            val a = (b.toLong() * samples.size / bars).toInt()
            val e = max(a + 1, ((b + 1).toLong() * samples.size / bars).toInt()).coerceAtMost(samples.size)
            var m = 0
            for (i in a until e) m = max(m, abs(samples[i].toInt()))
            out[b] = m / 32768f
        }
        val top = out.maxOrNull() ?: 0f
        if (top > 1e-6f) for (i in out.indices) out[i] /= top
        return out
    }

    /** Which bar the playhead is on, clamped to the last bar. */
    fun playheadBar(positionMs: Long, durationMs: Long, bars: Int): Int {
        if (durationMs <= 0) return 0
        return ((positionMs.coerceIn(0, durationMs) * bars) / durationMs).toInt().coerceAtMost(bars - 1)
    }
}

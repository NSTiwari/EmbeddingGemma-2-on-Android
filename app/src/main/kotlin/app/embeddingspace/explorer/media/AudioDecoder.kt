package app.embeddingspace.explorer.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import app.embeddingspace.explorer.domain.audio.Pcm
import app.embeddingspace.explorer.domain.audio.PcmAudio
import app.embeddingspace.explorer.domain.audio.Wav
import java.io.File
import java.nio.ByteOrder

/** Turns audio files (wav, mp3, m4a, ogg, the audio of a video) into mono 16-bit PCM. */
object AudioDecoder {
    /** Mono PCM at the file's own rate, capped at [maxSeconds]. Used for waveforms. */
    fun decodeFile(path: String, maxSeconds: Int = 300): PcmAudio? {
        if (path.endsWith(".wav", ignoreCase = true)) {
            val wav = runCatching { Wav.parse(File(path).readBytes()) }.getOrNull()
            if (wav != null) return Pcm.cap(wav, maxSeconds)
        }
        return decode(maxSeconds) { it.setDataSource(path) }
    }

    fun decodeUri(context: Context, uri: Uri, maxSeconds: Int = 300): PcmAudio? = decode(maxSeconds) { it.setDataSource(context, uri, null) }

    /** What the model wants: 16 kHz mono, at most [maxSeconds] long. */
    fun forModel(path: String, maxSeconds: Int = 300): PcmAudio? {
        val a = decodeFile(path, maxSeconds) ?: return null
        return PcmAudio(Pcm.resample(a.samples, a.sampleRate, Pcm.TARGET_RATE), Pcm.TARGET_RATE)
    }

    private fun decode(maxSeconds: Int, source: (MediaExtractor) -> Unit): PcmAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            source(extractor)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var out = ShortArray(rate * channels * 15)
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val ib = codec.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val buf = codec.getInputBuffer(ib)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ib, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val ob = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    ob >= 0 -> {
                        val buf = codec.getOutputBuffer(ob)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val count = sb.remaining()
                        if (n + count > out.size) out = out.copyOf(maxOf(out.size * 2, n + count))
                        sb.get(out, n, count)
                        n += count
                        codec.releaseOutputBuffer(ob, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (n >= maxSeconds * rate * channels) outputDone = true
                    }
                    ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (nf.containsKey(MediaFormat.KEY_PCM_ENCODING) && nf.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT) return null
                    }
                }
            }
            if (n == 0) return null
            val mono = Pcm.interleavedToMono(out.copyOf(n), channels)
            return Pcm.cap(PcmAudio(mono, rate), maxSeconds)
        } catch (e: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            extractor.release()
        }
    }
}

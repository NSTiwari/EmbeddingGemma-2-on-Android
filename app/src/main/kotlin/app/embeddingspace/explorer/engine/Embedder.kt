package app.embeddingspace.explorer.engine

import android.util.Log
import app.embeddingspace.explorer.domain.vec.Vectors
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Turns media into one 256-d unit vector in the shared space. */
interface Embedder {
    suspend fun embedText(text: String): FloatArray
    suspend fun embedImage(jpeg: ByteArray): FloatArray
    suspend fun embedAudio(wav: ByteArray): FloatArray
    suspend fun embedFrames(jpegFrames: List<ByteArray>): FloatArray
    fun close()
}

const val STORED_DIM = 256

/** The on-device model through the LiteRT-LM embedding engine, on the CPU. One engine, one worker thread. */
class LiteRtEmbedder(
    private val modelFile: File,
    private val cacheDir: File,
) : Embedder {
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "embedder").apply { isDaemon = true } }
    private val dispatcher = thread.asCoroutineDispatcher()
    private var engine: EmbeddingEngine? = null

    private fun ensure(): EmbeddingEngine {
        engine?.let { return it }
        val cpu = Backend.CPU()
        val dir = File(cacheDir, "lm_cpu").also { it.mkdirs() }
        val config = EmbeddingEngineConfig(
            modelPath = modelFile.absolutePath,
            backend = cpu,
            visionBackend = cpu,
            audioBackend = cpu,
            cacheDir = dir.absolutePath,
            maxInputLength = 1024,
            visionTokensPerImage = 70,
        )
        val e = EmbeddingEngine(config)
        e.initialize()
        engine = e
        return e
    }

    private val options = EmbeddingOptions(outputSize = STORED_DIM, visionTokensPerImage = 70)

    private fun fix(v: FloatArray): FloatArray =
        if (v.size > STORED_DIM) Vectors.truncate(v, STORED_DIM) else Vectors.normalizeInPlace(v.copyOf())

    override suspend fun embedText(text: String): FloatArray = withContext(dispatcher) {
        fix(ensure().computeEmbedding(listOf(InputData.Text(text.take(MAX_TEXT_CHARS))), options).embedding)
    }

    override suspend fun embedImage(jpeg: ByteArray): FloatArray = withContext(dispatcher) {
        fix(ensure().computeEmbedding(listOf(InputData.Image(jpeg)), options).embedding)
    }

    /** [wav] is a 16 kHz mono 16-bit WAV file in memory. */
    override suspend fun embedAudio(wav: ByteArray): FloatArray = withContext(dispatcher) {
        val e = ensure()
        try {
            fix(e.computeEmbedding(listOf(InputData.Audio(wav)), options).embedding)
        } catch (t: Throwable) {
            // Unverified on phones: if WAV bytes are refused, try the bare PCM after the 44 byte header.
            Log.w(TAG, "wav input failed, trying raw pcm", t)
            if (wav.size <= 44) throw t
            fix(e.computeEmbedding(listOf(InputData.Audio(wav.copyOfRange(44, wav.size))), options).embedding)
        }
    }

    override suspend fun embedFrames(jpegFrames: List<ByteArray>): FloatArray = withContext(dispatcher) {
        fix(ensure().computeEmbedding(jpegFrames.map { InputData.Image(it) }, options).embedding)
    }

    override fun close() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
        thread.shutdown()
    }

    companion object {
        private const val TAG = "ExplorerEmbedder"
        const val MAX_TEXT_CHARS = 3000
    }
}

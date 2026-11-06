package app.embeddingspace.explorer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Downloads, resumes and deletes the model file. Keeps the partial file so a dropped connection can continue. */
class ModelManager(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "model").also { it.mkdirs() }
    val file = File(dir, ModelInfo.FILE_NAME)
    private val part = File(dir, ModelInfo.FILE_NAME + ".part")

    private val _state = MutableStateFlow<ModelState>(initial())
    val state: StateFlow<ModelState> = _state
    private var job: Job? = null

    private fun initial(): ModelState = when {
        file.exists() && file.length() > 0 -> ModelState.Ready(file.length())
        part.exists() && part.length() > 0 -> ModelState.Failed(part.length(), ModelInfo.EXPECTED_BYTES)
        else -> ModelState.Missing
    }

    val isReady: Boolean get() = _state.value is ModelState.Ready

    fun download() {
        if (job?.isActive == true || isReady) return
        job = scope.launch(Dispatchers.IO) {
            try {
                run()
            } catch (e: CancellationException) {
                val done = if (part.exists()) part.length() else 0L
                _state.value = if (done > 0) ModelState.Failed(done, ModelInfo.EXPECTED_BYTES) else ModelState.Missing
                throw e
            } catch (e: Throwable) {
                _state.value = ModelState.Failed(if (part.exists()) part.length() else 0L, ModelInfo.EXPECTED_BYTES)
            }
        }
    }

    /** Stops the transfer but keeps what has arrived. */
    fun pause() {
        job?.cancel()
    }

    private suspend fun run() {
        var have = if (part.exists()) part.length() else 0L
        var total = ModelInfo.EXPECTED_BYTES
        _state.value = ModelState.Downloading(have, total)
        val c = URL(ModelInfo.URL).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
        c.connect()
        val code = c.responseCode
        if (code == 416) {
            // the partial file is already complete
            finish()
            return
        }
        if (code != 200 && code != 206) error("http $code")
        if (code == 200 && have > 0) {
            // server ignored the range, start again
            part.delete(); have = 0
        }
        val len = c.contentLengthLong
        if (len > 0) total = have + len
        val ctx = currentCoroutineContext()
        c.inputStream.use { input ->
            RandomAccessFile(part, "rw").use { out ->
                out.seek(have)
                val buf = ByteArray(1 shl 16)
                var done = have
                var lastPush = 0L
                while (true) {
                    ctx.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - lastPush > 150_000_000L) {
                        lastPush = now
                        _state.value = ModelState.Downloading(done, total)
                    }
                }
            }
        }
        finish()
    }

    private fun finish() {
        if (part.length() < ModelInfo.EXPECTED_BYTES * 9 / 10) error("short file")
        if (file.exists()) file.delete()
        if (!part.renameTo(file)) error("rename failed")
        _state.value = ModelState.Ready(file.length())
    }

    fun delete() {
        job?.cancel()
        file.delete(); part.delete()
        _state.value = ModelState.Missing
    }
}

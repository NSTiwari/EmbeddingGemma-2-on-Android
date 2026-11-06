package app.embeddingspace.explorer.data

/** Where the on-device model stands. */
sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val done: Long, val total: Long, val paused: Boolean = false) : ModelState {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }
    data class Failed(val done: Long, val total: Long) : ModelState
    data class Ready(val bytes: Long) : ModelState
}

object ModelInfo {
    const val FILE_NAME = "embeddinggemma-2-740m.litertlm"
    const val URL = "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/$FILE_NAME"
    const val EXPECTED_BYTES = 484_622_336L

    fun mb(bytes: Long): String = "%d MB".format((bytes + 500_000) / 1_000_000)
}

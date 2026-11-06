package app.embeddingspace.explorer.domain.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Rows of float32 numbers stored little-endian, as used for the demo vectors and the vocabulary. */
object FloatMatrix {
    fun read(bytes: ByteArray, dim: Int): Array<FloatArray> {
        require(dim > 0) { "dim must be positive" }
        require(bytes.size % (dim * 4) == 0) { "${bytes.size} bytes is not a whole number of $dim-d rows" }
        val rows = bytes.size / (dim * 4)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return Array(rows) { FloatArray(dim) { buf.getFloat() } }
    }

    fun write(rows: Array<FloatArray>): ByteArray {
        if (rows.isEmpty()) return ByteArray(0)
        val buf = ByteBuffer.allocate(rows.size * rows[0].size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (r in rows) for (v in r) buf.putFloat(v)
        return buf.array()
    }

    fun toBlob(v: FloatArray): ByteArray = write(arrayOf(v))

    fun fromBlob(b: ByteArray): FloatArray = read(b, b.size / 4)[0]
}

@Serializable
data class DemoItem(
    val id: String,
    val type: String,
    val title: String,
    val creator: String? = null,
    val license: String = "",
    val licenseUrl: String = "",
    val sourceUrl: String? = null,
    val concept: String = "",
    val media: String? = null,
    val thumb: String? = null,
    val text: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val cluster: Int = -1,
)

@Serializable
data class DemoCluster(
    val id: Int,
    val name: String,
    val guess: Boolean = false,
    val color: Int = 0,
    val words: List<String> = emptyList(),
)

@Serializable
data class DemoManifest(
    val dim: Int = 256,
    val items: List<DemoItem>,
    val clusters: List<DemoCluster> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

        fun parse(text: String): DemoManifest = json.decodeFromString(serializer(), text)

        fun encode(m: DemoManifest): String = json.encodeToString(serializer(), m)
    }
}

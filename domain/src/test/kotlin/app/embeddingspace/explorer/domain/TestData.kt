package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.cluster.Vocabulary
import app.embeddingspace.explorer.domain.io.DemoManifest
import app.embeddingspace.explorer.domain.io.FloatMatrix
import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.vec.Vectors

/** The real demo vectors (from the spike's embeddings.npz, cut to 256-d) and the vocabulary, read from the app assets. */
object TestData {
    private fun bytes(name: String): ByteArray = TestData::class.java.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    /** The full 97 item set of the spike, kept as test data for the maths checks. */
    val manifest: DemoManifest by lazy { DemoManifest.parse(String(bytes("spike/demo.json"))) }
    val vectors: Array<FloatArray> by lazy { FloatMatrix.read(bytes("spike/vectors.bin"), 256) }
    val vocab: Vocabulary by lazy { Vocabulary.parse(String(bytes("vocab.txt")), bytes("vocab.bin"), 256) }
    val concepts: List<String> get() = manifest.items.map { it.concept }
    val types: List<MediaType> get() = manifest.items.map { MediaType.fromKey(it.type) }

    /** A space holding all demo items with their shipped positions and clusters. */
    fun space(): Space {
        val s = Space(256)
        manifest.items.forEachIndexed { i, it ->
            s.addExisting(SpaceEntry(it.id, MediaType.fromKey(it.type), vectors[i], Origin.DEMO, it.x, it.y, it.z, it.cluster))
        }
        return s
    }

    /** Indices of the items used in the spike's layout test: 13 concepts, images, audio and notes (no video, no extra concepts). */
    fun spikeSubset(): List<Int> {
        val main = setOf("beach_waves", "rain", "city_traffic", "dog", "birdsong", "forest", "campfire", "train", "typing_keyboard", "cooking_kitchen", "thunderstorm", "crowd_cheering", "piano")
        return manifest.items.indices.filter { manifest.items[it].concept in main && manifest.items[it].type != "video" }
    }

    /** Unit vector with a 1 at [i]. */
    fun unit(dim: Int, i: Int): FloatArray = FloatArray(dim).also { it[i] = 1f }

    /** Deterministic noise around [base], normalised. */
    fun noisy(base: FloatArray, amount: Float, seed: Long): FloatArray {
        val r = UmapLite.Rng(seed)
        return Vectors.normalizeInPlace(FloatArray(base.size) { base[it] + (r.nextFloat() - 0.5f) * amount })
    }

    /** Blobs in a 32-d space: [perBlob] noisy points around each of [blobs] random directions. */
    fun blobs(blobs: Int, perBlob: Int, dim: Int = 32, noise: Float = 0.25f, seed: Long = 5): Pair<Array<FloatArray>, IntArray> {
        val r = UmapLite.Rng(seed)
        val centres = Array(blobs) { Vectors.normalizeInPlace(FloatArray(dim) { r.nextFloat() - 0.5f }) }
        val x = ArrayList<FloatArray>()
        val y = ArrayList<Int>()
        for (b in 0 until blobs) for (k in 0 until perBlob) {
            x.add(noisy(centres[b], noise, seed * 100 + b * 1000L + k)); y.add(b)
        }
        return x.toTypedArray() to y.toIntArray()
    }
}

/** The small demo set that ships inside the app (18 items). */
object ShippedDemo {
    private fun bytes(name: String): ByteArray = ShippedDemo::class.java.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    val manifest: DemoManifest by lazy { DemoManifest.parse(String(bytes("demo.json"))) }
    val vectors: Array<FloatArray> by lazy { FloatMatrix.read(bytes("vectors.bin"), manifest.dim) }

    fun exists(resource: String): Boolean = ShippedDemo::class.java.classLoader!!.getResource(resource) != null

    fun space(): Space {
        val s = Space(manifest.dim)
        manifest.items.forEachIndexed { i, it ->
            s.addExisting(SpaceEntry(it.id, MediaType.fromKey(it.type), vectors[i], Origin.DEMO, it.x, it.y, it.z, it.cluster))
        }
        return s
    }
}

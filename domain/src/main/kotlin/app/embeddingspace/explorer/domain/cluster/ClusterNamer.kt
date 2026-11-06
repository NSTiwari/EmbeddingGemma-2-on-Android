package app.embeddingspace.explorer.domain.cluster

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.vec.Vectors

/** A list of concept words with their text vectors (256-d, unit length). */
class Vocabulary(val words: List<String>, val vectors: Array<FloatArray>) {
    init {
        require(words.size == vectors.size) { "${words.size} words but ${vectors.size} vectors" }
    }

    val size: Int get() = words.size

    /** The word vectors centred with the text mean, the same way notes are centred. */
    fun centred(textMean: FloatArray?): Array<FloatArray> =
        if (textMean == null) vectors
        else Array(vectors.size) { Vectors.normalizeInPlace(Vectors.sub(vectors[it], textMean)) }

    companion object {
        /** Words one per line, vectors as little-endian float32 rows of [dim] numbers. */
        fun parse(wordsText: String, bin: ByteArray, dim: Int): Vocabulary {
            val words = wordsText.split('\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            val m = app.embeddingspace.explorer.domain.io.FloatMatrix.read(bin, dim)
            return Vocabulary(words, m)
        }
    }
}

/** The outcome of naming one cluster. */
data class NameResult(val label: String, val words: List<String>, val guess: Boolean, val topSim: Float)

/**
 * Names clusters by comparing the cluster centroid with the vocabulary.
 * Image, text and mixed clusters name well. Audio-only clusters do not, so they get three words and a guess flag.
 */
class ClusterNamer(private val vocab: Vocabulary, textMean: FloatArray?, private val minConfidence: Float = 0.10f) {
    private val centredVocab = vocab.centred(textMean)

    /** Per-modality centroids, normalised, summed and normalised again (the spike's "mixed" centroid). */
    fun centroid(members: List<Pair<MediaType, FloatArray>>): FloatArray {
        require(members.isNotEmpty())
        val dim = members[0].second.size
        val byGroup = members.groupBy { it.first.centringGroup }
        val total = FloatArray(dim)
        for ((_, list) in byGroup) {
            val c = Vectors.normalizeInPlace(Vectors.mean(list.map { it.second }))
            for (i in 0 until dim) total[i] += c[i]
        }
        return Vectors.normalizeInPlace(total)
    }

    fun rank(centroid: FloatArray, k: Int = 3): List<Pair<String, Float>> {
        val sims = FloatArray(centredVocab.size) { Vectors.dot(centroid, centredVocab[it]) }
        return Vectors.topK(sims, k).map { vocab.words[it] to sims[it] }
    }

    fun name(members: List<Pair<MediaType, FloatArray>>, usedNames: Set<String> = emptySet()): NameResult {
        val top = rank(centroid(members), 3)
        val audioOnly = members.all { it.first == MediaType.AUDIO }
        val topSim = top.firstOrNull()?.second ?: 0f
        val guess = audioOnly || topSim < minConfidence
        val words = top.map { it.first }
        val used = usedNames.map { it.lowercase() }.toSet()
        val label = when {
            guess -> words.joinToString(" / ") { cap(it) }
            cap(words[0]).lowercase() !in used -> cap(words[0])
            words.size > 1 && "${cap(words[0])} / ${cap(words[1])}".lowercase() !in used -> "${cap(words[0])} / ${cap(words[1])}"
            else -> uniqueSuffix(cap(words[0]), used)
        }
        return NameResult(label, words, guess, topSim)
    }

    private fun uniqueSuffix(base: String, used: Set<String>): String {
        var n = 2
        while ("$base $n".lowercase() in used) n++
        return "$base $n"
    }

    private fun cap(w: String) = w.replaceFirstChar { it.uppercase() }
}

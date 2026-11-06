package app.embeddingspace.explorer.domain.vec

import app.embeddingspace.explorer.domain.model.MediaType

/**
 * Per-modality running means. Raw vectors cluster by media type (the modality gap), so every
 * similarity in the app uses vectors centred with the mean of their own modality.
 *
 * Video frames live in the image space, so video shares the image mean.
 */
class ModalityMeans(val dim: Int, val minCount: Int = 3) {
    private val sums = HashMap<MediaType, DoubleArray>()
    private val counts = HashMap<MediaType, Int>()

    /** Bumps on every change so caches built from the means know when they went stale. */
    var version: Int = 0
        private set

    private fun group(t: MediaType) = t.centringGroup

    fun count(type: MediaType): Int = counts[group(type)] ?: 0

    fun add(type: MediaType, v: FloatArray) {
        val g = group(type)
        val s = sums.getOrPut(g) { DoubleArray(dim) }
        for (i in 0 until dim) s[i] += v[i].toDouble()
        counts[g] = (counts[g] ?: 0) + 1
        version++
    }

    fun remove(type: MediaType, v: FloatArray) {
        val g = group(type)
        val s = sums[g] ?: return
        val c = counts[g] ?: return
        if (c <= 1) {
            sums.remove(g); counts.remove(g)
        } else {
            for (i in 0 until dim) s[i] -= v[i].toDouble()
            counts[g] = c - 1
        }
        version++
    }

    /** Mean of a modality, or null when it has no items. */
    fun mean(type: MediaType): FloatArray? {
        val g = group(type)
        val s = sums[g] ?: return null
        val c = counts[g] ?: return null
        if (c == 0) return null
        return FloatArray(dim) { (s[it] / c).toFloat() }
    }

    /** Throws the running sums away and rebuilds them from the vectors. Fixes drift after many edits. */
    fun recompute(items: Iterable<Pair<MediaType, FloatArray>>) {
        sums.clear(); counts.clear()
        for ((t, v) in items) {
            val g = group(t)
            val s = sums.getOrPut(g) { DoubleArray(dim) }
            for (i in 0 until dim) s[i] += v[i].toDouble()
            counts[g] = (counts[g] ?: 0) + 1
        }
        version++
    }

    /**
     * Centres [v] with the mean of its modality and renormalises. With too few items for a stable
     * mean the vector is only normalised, so a single new item never collapses to zero.
     */
    fun centre(type: MediaType, v: FloatArray): FloatArray {
        val m = mean(type)
        if (m == null || count(type) < minCount) return Vectors.normalized(v)
        val out = FloatArray(dim) { v[it] - m[it] }
        if (Vectors.norm(out) < 1e-6f) return Vectors.normalized(v)
        return Vectors.normalizeInPlace(out)
    }
}

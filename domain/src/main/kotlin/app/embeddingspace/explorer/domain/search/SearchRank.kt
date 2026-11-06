package app.embeddingspace.explorer.domain.search

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.vec.Vectors
import kotlin.math.sqrt

/** One search hit. [score] is the z-score inside its media type, [sim] the plain centred cosine. */
data class Hit(val index: Int, val score: Float, val sim: Float)

/** The outcome of a search: ranked hits and a glow strength per point (0 means dimmed). */
class SearchResult(val hits: List<Hit>, val glow: FloatArray) {
    val best: Hit? get() = hits.firstOrNull()
}

/**
 * Turns a text query vector into glow values. Media types have different similarity scales (text to text is high,
 * text to audio is low), so each type is scored against its own spread. No query prefix is used: the spike found
 * that prefixes made no real difference.
 */
object SearchRank {
    fun search(space: Space, queryRaw: FloatArray, minZ: Float = 1.8f, maxHits: Int = 24): SearchResult {
        val n = space.size
        val glow = FloatArray(n)
        if (n == 0) return SearchResult(emptyList(), glow)
        val q = space.means.centre(MediaType.TEXT, queryRaw)
        val sims = FloatArray(n) { Vectors.dot(q, space.centredOf(it)) }
        val z = FloatArray(n)
        for (group in MediaType.entries.groupBy { it.centringGroup }.keys) {
            val idx = (0 until n).filter { space.entries[it].type.centringGroup == group }
            if (idx.isEmpty()) continue
            val mean = idx.map { sims[it] }.average().toFloat()
            var v = 0f
            for (i in idx) v += (sims[i] - mean) * (sims[i] - mean)
            val sd = sqrt(v / idx.size).coerceAtLeast(0.02f)
            for (i in idx) z[i] = (sims[i] - mean) / sd
        }
        val ranked = (0 until n).sortedByDescending { z[it] }
        var chosen = ranked.filter { z[it] >= minZ }.take(maxHits)
        if (chosen.size < 3) chosen = ranked.take(minOf(3, n))
        val top = z[chosen.first()]
        val floor = z[chosen.last()] - 0.5f
        for (i in chosen) glow[i] = if (top - floor < 1e-4f) 1f else ((z[i] - floor) / (top - floor)).coerceIn(0.25f, 1f)
        return SearchResult(chosen.map { Hit(it, z[it], sims[it]) }, glow)
    }
}

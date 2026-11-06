package app.embeddingspace.explorer.domain.cluster

import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.vec.Vectors

/** Spherical k-means on unit vectors (cosine similarity), with k-means++ seeding and a fixed seed. */
object KMeans {
    class Result(val labels: IntArray, val centroids: Array<FloatArray>)

    fun run(x: Array<FloatArray>, k: Int, seed: Long = 7L, maxIter: Int = 30): Result {
        val n = x.size
        require(n > 0) { "nothing to cluster" }
        val kk = k.coerceIn(1, n)
        val rng = UmapLite.Rng(seed)
        val centroids = seedPlusPlus(x, kk, rng)
        val labels = IntArray(n) { -1 }
        for (iter in 0 until maxIter) {
            var changed = false
            for (i in 0 until n) {
                var best = 0
                var bestSim = Float.NEGATIVE_INFINITY
                for (c in 0 until kk) {
                    val s = Vectors.dot(x[i], centroids[c])
                    if (s > bestSim) { bestSim = s; best = c }
                }
                if (labels[i] != best) { labels[i] = best; changed = true }
            }
            // update
            val sums = Array(kk) { FloatArray(x[0].size) }
            val counts = IntArray(kk)
            for (i in 0 until n) {
                counts[labels[i]]++
                val s = sums[labels[i]]
                for (j in s.indices) s[j] += x[i][j]
            }
            for (c in 0 until kk) {
                if (counts[c] == 0) {
                    // empty cluster: take the point that fits its centroid worst
                    var worst = 0
                    var worstSim = Float.MAX_VALUE
                    for (i in 0 until n) {
                        val s = Vectors.dot(x[i], centroids[labels[i]])
                        if (s < worstSim) { worstSim = s; worst = i }
                    }
                    centroids[c] = x[worst].copyOf()
                    changed = true
                } else {
                    centroids[c] = Vectors.normalizeInPlace(sums[c])
                }
            }
            if (!changed) break
        }
        return Result(labels, centroids)
    }

    private fun seedPlusPlus(x: Array<FloatArray>, k: Int, rng: UmapLite.Rng): Array<FloatArray> {
        val n = x.size
        val out = ArrayList<FloatArray>()
        out.add(x[rng.nextInt(n)].copyOf())
        val d = FloatArray(n) { 1f - Vectors.dot(x[it], out[0]) }
        while (out.size < k) {
            var total = 0.0
            for (v in d) total += (v * v).toDouble()
            var pick = n - 1
            if (total > 0) {
                var r = rng.nextFloat() * total
                for (i in 0 until n) {
                    r -= (d[i] * d[i]).toDouble()
                    if (r <= 0) { pick = i; break }
                }
            } else pick = rng.nextInt(n)
            out.add(x[pick].copyOf())
            for (i in 0 until n) d[i] = minOf(d[i], 1f - Vectors.dot(x[i], out.last()))
        }
        return out.toTypedArray()
    }
}

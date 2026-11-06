package app.embeddingspace.explorer.domain.cluster

import app.embeddingspace.explorer.domain.vec.Vectors

/** Picks the number of clusters by silhouette score and runs k-means with it. */
object ClusterSearch {
    class Outcome(val labels: IntArray, val centroids: Array<FloatArray>, val k: Int, val silhouette: Float)

    /** Mean silhouette with cosine distance. [sim] is the full n by n similarity matrix. */
    fun silhouette(sim: Array<FloatArray>, labels: IntArray): Float {
        val n = labels.size
        val k = (labels.maxOrNull() ?: -1) + 1
        if (k < 2 || n < 3) return 0f
        val sizes = IntArray(k)
        for (l in labels) sizes[l]++
        var total = 0f
        var counted = 0
        val sums = FloatArray(k)
        for (i in 0 until n) {
            if (sizes[labels[i]] < 2) continue
            java.util.Arrays.fill(sums, 0f)
            for (j in 0 until n) if (j != i) sums[labels[j]] += 1f - sim[i][j]
            val a = sums[labels[i]] / (sizes[labels[i]] - 1)
            var b = Float.MAX_VALUE
            for (c in 0 until k) if (c != labels[i] && sizes[c] > 0) b = minOf(b, sums[c] / sizes[c])
            if (b == Float.MAX_VALUE) continue
            val m = maxOf(a, b)
            if (m > 1e-9f) { total += (b - a) / m; counted++ }
        }
        return if (counted == 0) 0f else total / counted
    }

    fun similarityMatrix(x: Array<FloatArray>): Array<FloatArray> {
        val n = x.size
        val s = Array(n) { FloatArray(n) }
        for (i in 0 until n) {
            s[i][i] = 1f
            for (j in i + 1 until n) {
                val v = Vectors.dot(x[i], x[j])
                s[i][j] = v; s[j][i] = v
            }
        }
        return s
    }

    /** Candidate cluster counts. Every k for small maps, a spread of values for big ones. */
    fun candidates(n: Int, maxK: Int): List<Int> {
        val top = minOf(maxK, maxOf(2, n / 2))
        if (top <= 24 || n <= 250) return (2..top).toList()
        val out = sortedSetOf<Int>()
        var k = 2.0
        while (k <= top) { out.add(k.toInt()); k *= 1.25 }
        out.add(top)
        return out.toList()
    }

    /**
     * Tries a range of k and returns the best. Silhouette alone likes very few clusters, so among the
     * values within [slack] of the best score the largest k wins: a map reads better with more islands.
     */
    fun best(x: Array<FloatArray>, maxK: Int = 40, slack: Float = 0.01f, seed: Long = 7L): Outcome {
        val n = x.size
        if (n < 4) {
            val r = KMeans.run(x, minOf(n, 2), seed)
            return Outcome(r.labels, r.centroids, r.centroids.size, 0f)
        }
        val sim = similarityMatrix(x)
        val tried = candidates(n, maxK).map { k ->
            val r = KMeans.run(x, k, seed)
            Triple(k, r, silhouette(sim, r.labels))
        }
        val top = tried.maxOf { it.third }
        val pick = tried.filter { it.third >= top - slack }.maxByOrNull { it.first }!!
        val labels = mergeSingletons(x, pick.second.labels, pick.second.centroids)
        return Outcome(labels, pick.second.centroids, labels.toSet().size, pick.third)
    }

    /** A cluster with one member reads badly on a big map, so it joins the closest other cluster. */
    fun mergeSingletons(x: Array<FloatArray>, labels: IntArray, centroids: Array<FloatArray>): IntArray {
        if (x.size < 12) return labels
        val sizes = HashMap<Int, Int>()
        for (l in labels) sizes[l] = (sizes[l] ?: 0) + 1
        val out = labels.copyOf()
        for (i in out.indices) {
            if (sizes[labels[i]] != 1) continue
            var best = -1
            var bestSim = Float.NEGATIVE_INFINITY
            for (c in centroids.indices) {
                if (c == labels[i] || (sizes[c] ?: 0) < 2) continue
                val s = Vectors.dot(x[i], centroids[c])
                if (s > bestSim) { bestSim = s; best = c }
            }
            if (best >= 0) out[i] = best
        }
        // compact the labels to 0..k-1
        val map = out.toSet().sorted().withIndex().associate { it.value to it.index }
        return IntArray(out.size) { map.getValue(out[it]) }
    }
}

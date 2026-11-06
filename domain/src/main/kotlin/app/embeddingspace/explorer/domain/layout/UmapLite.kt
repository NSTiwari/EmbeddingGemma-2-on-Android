package app.embeddingspace.explorer.domain.layout

import app.embeddingspace.explorer.domain.vec.Vectors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Settings of the layout fit. The defaults match what the Python spike used (n_neighbors 10, min_dist 0.1). */
data class UmapParams(
    val nNeighbors: Int = 10,
    val minDist: Float = 0.1f,
    val spread: Float = 1f,
    /** 0 means choose by size: 500 for small maps, fewer for big ones. */
    val epochs: Int = 0,
    val negativeSampleRate: Int = 5,
    val repulsion: Float = 1f,
    val learningRate: Float = 1f,
    val seed: Long = 42L,
    /** Turn a fresh layout into its principal frame (longest spread up the screen). */
    val orient: Boolean = true,
)

/** A weighted edge of the neighbour graph. */
class GraphEdges(val head: IntArray, val tail: IntArray, val weight: FloatArray) {
    val size: Int get() = head.size
}

/**
 * A small UMAP in pure Kotlin: kNN graph on cosine distance, fuzzy simplicial set weights,
 * PCA start, SGD with negative sampling, three output dimensions, fixed seed.
 * Input vectors must be unit length (cosine distance is then 1 - dot).
 */
class UmapLite(private val params: UmapParams = UmapParams()) {

    /** Neighbour indices and cosine distances, nearest first. */
    class Knn(val index: Array<IntArray>, val dist: Array<FloatArray>)

    fun knn(x: Array<FloatArray>, k: Int): Knn {
        val n = x.size
        val kk = min(k, n - 1)
        val idx = Array(n) { IntArray(kk) }
        val dst = Array(n) { FloatArray(kk) }
        val sims = FloatArray(n)
        for (i in 0 until n) {
            val xi = x[i]
            for (j in 0 until n) sims[j] = if (j == i) Float.NEGATIVE_INFINITY else Vectors.dot(xi, x[j])
            val top = Vectors.topK(sims, kk, i)
            for (p in 0 until kk) {
                idx[i][p] = top[p]
                dst[i][p] = max(0f, 1f - sims[top[p]])
            }
        }
        return Knn(idx, dst)
    }

    /** rho (distance to the nearest neighbour) and sigma (local scale) of each point. */
    fun smoothKnnDist(dist: Array<FloatArray>, k: Int): Pair<FloatArray, FloatArray> {
        val n = dist.size
        val rho = FloatArray(n)
        val sigma = FloatArray(n)
        val target = ln(k.toDouble()) / ln(2.0)
        for (i in 0 until n) {
            val d = dist[i]
            rho[i] = d.firstOrNull { it > 0f } ?: 0f
            var lo = 0.0
            var hi = Double.POSITIVE_INFINITY
            var mid = 1.0
            for (iter in 0 until 64) {
                var psum = 0.0
                for (v in d) {
                    val dd = v - rho[i]
                    psum += if (dd > 0) exp(-dd / mid) else 1.0
                }
                if (abs(psum - target) < 1e-5) break
                if (psum > target) {
                    hi = mid; mid = (lo + hi) / 2
                } else {
                    lo = mid
                    mid = if (hi == Double.POSITIVE_INFINITY) mid * 2 else (lo + hi) / 2
                }
            }
            val meanDist = d.average().toFloat()
            sigma[i] = max(mid.toFloat(), 1e-3f * max(meanDist, 1e-6f))
        }
        return rho to sigma
    }

    /** Symmetric fuzzy union of the directed neighbour weights. */
    fun fuzzyGraph(knn: Knn): GraphEdges {
        val n = knn.index.size
        val k = if (n > 0) knn.index[0].size else 0
        val (rho, sigma) = smoothKnnDist(knn.dist, max(k, 2))
        val directed = HashMap<Long, Float>(n * k * 2)
        for (i in 0 until n) for (p in 0 until k) {
            val j = knn.index[i][p]
            val dd = knn.dist[i][p] - rho[i]
            val w = if (dd <= 0f) 1f else exp((-dd / sigma[i]).toDouble()).toFloat()
            directed[i.toLong() * n + j] = w
        }
        val heads = ArrayList<Int>()
        val tails = ArrayList<Int>()
        val ws = ArrayList<Float>()
        for ((key, a) in directed) {
            val i = (key / n).toInt()
            val j = (key % n).toInt()
            val b = directed[j.toLong() * n + i]
            if (b != null && j < i) continue // handled when we visit the smaller index
            val w = if (b == null) a else a + b - a * b
            heads.add(i); tails.add(j); ws.add(w)
        }
        return GraphEdges(heads.toIntArray(), tails.toIntArray(), ws.toFloatArray())
    }

    /** Fits 1 / (1 + a x^(2b)) to the target membership curve. Known result for min_dist 0.1: a 1.577, b 0.895. */
    fun fitAB(): Pair<Float, Float> {
        val m = 300
        val xs = DoubleArray(m) { (it + 1) * 3.0 * params.spread / m }
        val ys = DoubleArray(m) { if (xs[it] < params.minDist) 1.0 else exp(-(xs[it] - params.minDist) / params.spread) }
        var a = 1.5
        var b = 0.9
        var lambda = 1e-3
        fun cost(a: Double, b: Double): Double {
            var s = 0.0
            for (i in 0 until m) {
                val f = 1.0 / (1.0 + a * xs[i].pow(2 * b))
                s += (f - ys[i]) * (f - ys[i])
            }
            return s
        }
        var c0 = cost(a, b)
        for (iter in 0 until 200) {
            var jaa = 0.0; var jab = 0.0; var jbb = 0.0; var ga = 0.0; var gb = 0.0
            for (i in 0 until m) {
                val xp = xs[i].pow(2 * b)
                val f = 1.0 / (1.0 + a * xp)
                val da = -xp * f * f
                val db = -a * xp * 2 * ln(xs[i]) * f * f
                val r = f - ys[i]
                jaa += da * da; jab += da * db; jbb += db * db; ga += da * r; gb += db * r
            }
            val h11 = jaa * (1 + lambda)
            val h22 = jbb * (1 + lambda)
            val det = h11 * h22 - jab * jab
            if (abs(det) < 1e-18) break
            val sa = (h22 * ga - jab * gb) / det
            val sb = (h11 * gb - jab * ga) / det
            val na = a - sa
            val nb = b - sb
            if (na > 0 && nb > 0) {
                val c1 = cost(na, nb)
                if (c1 < c0) {
                    a = na; b = nb; c0 = c1; lambda = max(lambda / 3, 1e-9)
                    if (abs(sa) + abs(sb) < 1e-9) break
                    continue
                }
            }
            lambda *= 4
            if (lambda > 1e9) break
        }
        return a.toFloat() to b.toFloat()
    }

    /**
     * Lays out [x] in 3D. [init] (flat x,y,z per point) seeds the layout so a refit keeps the old shape;
     * without it the start is PCA. [onProgress] gets 0..1 and may return false to stop early.
     */
    fun fit(x: Array<FloatArray>, init: FloatArray? = null, onProgress: ((Float) -> Boolean)? = null): FloatArray {
        val n = x.size
        if (n == 0) return FloatArray(0)
        if (n == 1) return FloatArray(3)
        if (n < 4) {
            val p = Pca.scaleToMax(Pca.project(x, 3), 5f)
            if (p.all { abs(it) < 1e-6f }) for (i in p.indices) p[i] = (i % 3 - 1) * (i / 3 + 1) * 1f
            return p
        }
        // a small map needs a small neighbourhood, or its few clusters blur into one
        val k = min(min(params.nNeighbors, max(4, n / 3)), n - 1)
        val graph = fuzzyGraph(knn(x, k))
        val (a, b) = fitAB()
        val emb = init?.takeIf { it.size == n * 3 }?.copyOf() ?: Pca.scaleToMax(Pca.project(x, 3), 10f)
        // tiny deterministic noise so identical start points can separate
        val noise = Rng(params.seed xor 0x5bd1e995L)
        for (i in emb.indices) emb[i] += (noise.nextFloat() - 0.5f) * 1e-3f
        val epochs = if (params.epochs > 0) params.epochs else if (init != null) 150 else if (n <= 600) 500 else 250
        optimize(emb, graph, n, epochs, a, b, init != null, onProgress)
        recentre(emb)
        // a fresh layout gets a portrait friendly shape, a seeded refit keeps the frame it started in
        return if (init == null && params.orient) LayoutShape.orient(emb) else emb
    }

    private fun optimize(
        emb: FloatArray, g: GraphEdges, n: Int, epochs: Int, a: Float, b: Float, seeded: Boolean,
        onProgress: ((Float) -> Boolean)?,
    ) {
        if (g.size == 0) return
        var maxW = 0f
        for (w in g.weight) maxW = max(maxW, w)
        val keep = ArrayList<Int>()
        for (e in 0 until g.size) if (g.weight[e] >= maxW / epochs) keep.add(e)
        val m = keep.size
        val head = IntArray(m) { g.head[keep[it]] }
        val tail = IntArray(m) { g.tail[keep[it]] }
        val epochsPerSample = FloatArray(m) { maxW / g.weight[keep[it]] }
        val nextSample = epochsPerSample.copyOf()
        val negEps = FloatArray(m) { epochsPerSample[it] / params.negativeSampleRate }
        val nextNeg = negEps.copyOf()
        val rng = Rng(params.seed)
        val a2b = 2f * a * b
        val startAlpha = if (seeded) params.learningRate * 0.4f else params.learningRate
        for (ep in 0 until epochs) {
            val alpha = startAlpha * (1f - ep.toFloat() / epochs)
            for (e in 0 until m) {
                if (nextSample[e] > ep) continue
                val j = head[e] * 3
                val kx = tail[e] * 3
                var d2 = sq(emb[j] - emb[kx]) + sq(emb[j + 1] - emb[kx + 1]) + sq(emb[j + 2] - emb[kx + 2])
                if (d2 > 0f) {
                    val coeff = -a2b * d2.toDouble().pow((b - 1f).toDouble()).toFloat() / (a * d2.toDouble().pow(b.toDouble()).toFloat() + 1f)
                    for (c in 0 until 3) {
                        val gr = clip(coeff * (emb[j + c] - emb[kx + c]))
                        emb[j + c] += gr * alpha
                        emb[kx + c] -= gr * alpha
                    }
                }
                nextSample[e] += epochsPerSample[e]
                val nNeg = ((ep - nextNeg[e]) / negEps[e]).toInt()
                for (p in 0 until nNeg) {
                    val r = rng.nextInt(n) * 3
                    if (r == j) continue
                    d2 = sq(emb[j] - emb[r]) + sq(emb[j + 1] - emb[r + 1]) + sq(emb[j + 2] - emb[r + 2])
                    val coeff = if (d2 > 0f) {
                        2f * params.repulsion * b / ((0.001f + d2) * (a * d2.toDouble().pow(b.toDouble()).toFloat() + 1f))
                    } else 0f
                    for (c in 0 until 3) {
                        val gr = if (coeff > 0f) clip(coeff * (emb[j + c] - emb[r + c])) else 4f
                        emb[j + c] += gr * alpha
                    }
                }
                nextNeg[e] += nNeg * negEps[e]
            }
            if (onProgress != null && ep % 10 == 0 && !onProgress(ep.toFloat() / epochs)) return
        }
        onProgress?.invoke(1f)
    }

    private fun sq(v: Float) = v * v
    private fun clip(v: Float) = if (v > 4f) 4f else if (v < -4f) -4f else v

    private fun recentre(p: FloatArray) {
        val n = p.size / 3
        var mx = 0f; var my = 0f; var mz = 0f
        for (i in 0 until n) { mx += p[i * 3]; my += p[i * 3 + 1]; mz += p[i * 3 + 2] }
        mx /= n; my /= n; mz /= n
        for (i in 0 until n) { p[i * 3] -= mx; p[i * 3 + 1] -= my; p[i * 3 + 2] -= mz }
    }

    /** Small fast generator with a fixed seed, so layouts repeat exactly. */
    class Rng(seed: Long) {
        private var s = seed * 6364136223846793005L + 1442695040888963407L
        private fun next(): Long {
            s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17)
            return s
        }

        fun nextInt(bound: Int): Int = ((next() ushr 33) % bound).toInt()
        fun nextFloat(): Float = ((next() ushr 40).toInt()) / 16777216f
    }
}

package app.embeddingspace.explorer.domain.layout

import kotlin.math.abs
import kotlin.math.sqrt

/** Plain PCA by power iteration. Good enough for 3 components, and it is the UMAP starting point. */
object Pca {
    /**
     * Projects the rows of [x] onto their first [components] principal axes.
     * Returns a flat array row by row (n * components numbers). Deterministic.
     */
    fun project(x: Array<FloatArray>, components: Int = 3, iterations: Int = 60): FloatArray {
        val n = x.size
        if (n == 0) return FloatArray(0)
        val d = x[0].size
        val mean = FloatArray(d)
        for (r in x) for (j in 0 until d) mean[j] += r[j]
        for (j in 0 until d) mean[j] /= n
        // work on a centred copy that gets deflated as axes are found
        val w = Array(n) { i -> FloatArray(d) { j -> x[i][j] - mean[j] } }
        val out = FloatArray(n * components)
        for (c in 0 until components) {
            var v = FloatArray(d) { j -> if ((j + c) % 3 == 0) 1f else if ((j + c) % 3 == 1) 0.5f else -0.25f }
            normalize(v)
            val score = FloatArray(n)
            for (it in 0 until iterations) {
                for (i in 0 until n) {
                    var s = 0f
                    val row = w[i]
                    for (j in 0 until d) s += row[j] * v[j]
                    score[i] = s
                }
                val next = FloatArray(d)
                for (i in 0 until n) {
                    val s = score[i]
                    val row = w[i]
                    for (j in 0 until d) next[j] += row[j] * s
                }
                if (normalize(next) < 1e-9f) break
                v = next
            }
            for (i in 0 until n) {
                var s = 0f
                val row = w[i]
                for (j in 0 until d) s += row[j] * v[j]
                out[i * components + c] = s
                for (j in 0 until d) row[j] -= s * v[j]
            }
        }
        return out
    }

    private fun normalize(v: FloatArray): Float {
        var s = 0f
        for (a in v) s += a * a
        val n = sqrt(s)
        if (n > 1e-12f) for (i in v.indices) v[i] /= n
        return n
    }

    /** Scales a flat layout so its biggest absolute coordinate equals [maxAbs]. */
    fun scaleToMax(p: FloatArray, maxAbs: Float): FloatArray {
        var m = 0f
        for (a in p) m = maxOf(m, abs(a))
        if (m < 1e-12f) return p
        val f = maxAbs / m
        for (i in p.indices) p[i] *= f
        return p
    }
}

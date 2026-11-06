package app.embeddingspace.explorer.domain.vec

import kotlin.math.sqrt

/** Small dense-vector helpers. Vectors are plain FloatArrays so they stay cheap to store and loop over. */
object Vectors {
    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun norm(a: FloatArray): Float = sqrt(dot(a, a))

    /** Scales in place to unit length. A zero vector stays zero. */
    fun normalizeInPlace(a: FloatArray): FloatArray {
        val n = norm(a)
        if (n > 1e-12f) {
            val inv = 1f / n
            for (i in a.indices) a[i] *= inv
        }
        return a
    }

    fun normalized(a: FloatArray): FloatArray = normalizeInPlace(a.copyOf())

    fun cosine(a: FloatArray, b: FloatArray): Float {
        val na = norm(a)
        val nb = norm(b)
        if (na < 1e-12f || nb < 1e-12f) return 0f
        return dot(a, b) / (na * nb)
    }

    /** Matryoshka style cut: keep the first [dim] numbers, then renormalise. */
    fun truncate(a: FloatArray, dim: Int): FloatArray {
        require(dim in 1..a.size) { "dim $dim out of range for ${a.size}" }
        return normalizeInPlace(a.copyOf(dim))
    }

    fun add(a: FloatArray, b: FloatArray): FloatArray = FloatArray(a.size) { a[it] + b[it] }

    fun sub(a: FloatArray, b: FloatArray): FloatArray = FloatArray(a.size) { a[it] - b[it] }

    fun scale(a: FloatArray, f: Float): FloatArray = FloatArray(a.size) { a[it] * f }

    fun mean(rows: List<FloatArray>): FloatArray {
        require(rows.isNotEmpty()) { "mean of nothing" }
        val out = FloatArray(rows[0].size)
        for (r in rows) for (i in out.indices) out[i] += r[i]
        val inv = 1f / rows.size
        for (i in out.indices) out[i] *= inv
        return out
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            s += d * d
        }
        return sqrt(s)
    }

    /** Indices of the [k] largest values, best first. Ties keep the lower index first. */
    fun topK(values: FloatArray, k: Int, skip: Int = -1): IntArray {
        val kk = minOf(k, values.size - if (skip in values.indices) 1 else 0)
        if (kk <= 0) return IntArray(0)
        val idx = IntArray(kk) { -1 }
        val best = FloatArray(kk) { Float.NEGATIVE_INFINITY }
        for (i in values.indices) {
            if (i == skip) continue
            val v = values[i]
            if (v <= best[kk - 1] && idx[kk - 1] >= 0) continue
            var p = kk - 1
            while (p > 0 && v > best[p - 1]) {
                best[p] = best[p - 1]; idx[p] = idx[p - 1]; p--
            }
            best[p] = v; idx[p] = i
        }
        return idx.filter { it >= 0 }.toIntArray()
    }
}

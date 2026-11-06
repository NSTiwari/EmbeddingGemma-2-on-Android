package app.embeddingspace.explorer.domain.layout

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gives a fresh layout a phone friendly shape: the longest spread runs up the screen (portrait), and the three
 * spreads are partly evened out so the cloud fills the view instead of lying in a thin band.
 */
object LayoutShape {
    /** Eigenvalues (largest first) and matching unit eigenvectors (columns) of a symmetric 3x3 matrix, by Jacobi sweeps. */
    fun eigen3(m: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val a = Array(3) { m[it].copyOf() }
        val v = Array(3) { i -> DoubleArray(3) { j -> if (i == j) 1.0 else 0.0 } }
        for (sweep in 0 until 30) {
            var off = 0.0
            for (p in 0 until 2) for (q in p + 1 until 3) off += a[p][q] * a[p][q]
            if (off < 1e-18) break
            for (p in 0 until 2) for (q in p + 1 until 3) {
                if (abs(a[p][q]) < 1e-15) continue
                val theta = 0.5 * atan2(2 * a[p][q], a[q][q] - a[p][p])
                val c = cos(theta)
                val s = sin(theta)
                for (k in 0 until 3) {
                    val akp = a[k][p]
                    val akq = a[k][q]
                    a[k][p] = c * akp - s * akq
                    a[k][q] = s * akp + c * akq
                }
                for (k in 0 until 3) {
                    val apk = a[p][k]
                    val aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk
                    a[q][k] = s * apk + c * aqk
                }
                for (k in 0 until 3) {
                    val vkp = v[k][p]
                    val vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq
                    v[k][q] = s * vkp + c * vkq
                }
            }
        }
        val order = (0 until 3).sortedByDescending { a[it][it] }
        val vals = DoubleArray(3) { a[order[it]][order[it]] }
        val vecs = Array(3) { col -> DoubleArray(3) { row -> v[row][order[col]] } }
        return vals to vecs
    }

    /**
     * Rotates [pos] (flat x,y,z) into its principal axes, longest axis on y, second on x, third on z, and scales
     * each axis by (mean spread / spread)^[equalise]; the vertical axis is then stretched by [portraitStretch] to use a
     * tall screen. Positions end up centred on zero. Returns a new array.
     */
    fun orient(pos: FloatArray, equalise: Float = 0.6f, portraitStretch: Float = 1.05f): FloatArray {
        val n = pos.size / 3
        if (n < 4) return pos.copyOf()
        val c = DoubleArray(3)
        for (i in 0 until n) for (k in 0 until 3) c[k] += pos[i * 3 + k].toDouble()
        for (k in 0 until 3) c[k] /= n
        val cov = Array(3) { DoubleArray(3) }
        for (i in 0 until n) for (a in 0 until 3) for (b in 0 until 3) {
            cov[a][b] += (pos[i * 3 + a] - c[a]) * (pos[i * 3 + b] - c[b])
        }
        for (a in 0 until 3) for (b in 0 until 3) cov[a][b] /= n
        val (vals, vecs) = eigen3(cov)
        val spread = DoubleArray(3) { sqrt(vals[it].coerceAtLeast(1e-12)) }
        val mean = spread.average()
        val scale = DoubleArray(3) { (mean / spread[it]).pow(equalise.toDouble()) }
        // principal axis 0 -> y, 1 -> x, 2 -> z
        val target = intArrayOf(1, 0, 2)
        val out = FloatArray(pos.size)
        for (i in 0 until n) {
            for (axis in 0 until 3) {
                var s = 0.0
                for (k in 0 until 3) s += (pos[i * 3 + k] - c[k]) * vecs[axis][k]
                out[i * 3 + target[axis]] = (s * scale[axis] * (if (target[axis] == 1) portraitStretch else 1f)).toFloat()
            }
        }
        return out
    }

    /** Moves a flat x,y,z layout so its mean is the origin. The origin is the centre of the space, drawn as the hub. */
    fun recentre(pos: FloatArray): FloatArray {
        val n = pos.size / 3
        if (n == 0) return pos
        val m = DoubleArray(3)
        for (i in 0 until n) for (k in 0 until 3) m[k] += pos[i * 3 + k].toDouble()
        for (k in 0 until 3) m[k] /= n
        for (i in 0 until n) for (k in 0 until 3) pos[i * 3 + k] = (pos[i * 3 + k] - m[k]).toFloat()
        return pos
    }

    /**
     * Pushes clusters away from each other: each cluster's centre moves out from the middle by [factor] while its
     * members keep their shape around it. Then the whole layout is scaled back to the size it had, so repeating it
     * does not make the map grow. [spread] opens each cluster up too (small maps only by default), so a handful of cards
     * of one cluster do not sit on top of each other. [labels] gives the cluster of each point.
     */
    fun separate(pos: FloatArray, labels: IntArray, factor: Float = 1.5f, spread: Float = if (pos.size / 3 <= 40) 2f else 1f): FloatArray {
        val n = pos.size / 3
        if (n < 2) return pos
        fun radius(): Float {
            var s = 0f
            for (i in 0 until n) s += sqrt(pos[i * 3] * pos[i * 3] + pos[i * 3 + 1] * pos[i * 3 + 1] + pos[i * 3 + 2] * pos[i * 3 + 2])
            return s / n
        }
        recentre(pos)
        val before = radius()
        val sums = HashMap<Int, FloatArray>()
        val counts = HashMap<Int, Int>()
        for (i in 0 until n) {
            val a = sums.getOrPut(labels[i]) { FloatArray(3) }
            for (k in 0 until 3) a[k] += pos[i * 3 + k]
            counts[labels[i]] = (counts[labels[i]] ?: 0) + 1
        }
        for (i in 0 until n) {
            val c = counts.getValue(labels[i])
            val s = sums.getValue(labels[i])
            for (k in 0 until 3) {
                val centre = s[k] / c
                pos[i * 3 + k] = centre * factor + (pos[i * 3 + k] - centre) * spread
            }
        }
        val after = radius()
        if (after > 1e-6f && before > 1e-6f) {
            val f = before / after
            for (i in pos.indices) pos[i] *= f
        }
        return recentre(pos)
    }

    /**
     * Settles a small map once: points closer than [minDist] push each other apart in 3D, with a light pull back to
     * where they were so the shape and the clusters stay. The result is a layout where cards of a handful of items do
     * not pile up. The map is centred on the origin afterwards.
     */
    fun spaceOut(pos: FloatArray, minDist: Float, iterations: Int = 80, anchor: Float = 0.04f): FloatArray {
        val n = pos.size / 3
        if (n < 2 || minDist <= 0f) return pos
        val home = pos.copyOf()
        for (it in 0 until iterations) {
            for (i in 0 until n) for (j in i + 1 until n) {
                var dx = pos[j * 3] - pos[i * 3]
                var dy = pos[j * 3 + 1] - pos[i * 3 + 1]
                var dz = pos[j * 3 + 2] - pos[i * 3 + 2]
                var d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d >= minDist) continue
                if (d < 1e-5f) { dx = 1f; dy = 0.3f * ((i + j) % 3 - 1); dz = 0.2f * (j % 3 - 1); d = sqrt(dx * dx + dy * dy + dz * dz) }
                val push = (minDist - d) / 2 * 0.6f
                val ux = dx / d; val uy = dy / d; val uz = dz / d
                pos[i * 3] -= ux * push; pos[i * 3 + 1] -= uy * push; pos[i * 3 + 2] -= uz * push
                pos[j * 3] += ux * push; pos[j * 3 + 1] += uy * push; pos[j * 3 + 2] += uz * push
            }
            for (k in pos.indices) pos[k] += (home[k] - pos[k]) * anchor
        }
        return recentre(pos)
    }
}

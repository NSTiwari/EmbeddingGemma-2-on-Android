package app.embeddingspace.explorer.domain.layout

import kotlin.math.sqrt

/** Ways to judge a 3D layout against known labels. Used by the tests and the demo generator. */
object LayoutMetrics {
    /** Average share of the [k] nearest layout neighbours that carry the same label as the point. */
    fun knnSameLabel(pos: FloatArray, labels: List<String>, k: Int = 5): Double {
        val n = labels.size
        require(pos.size == n * 3)
        var total = 0.0
        val d = FloatArray(n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                val dx = pos[i * 3] - pos[j * 3]
                val dy = pos[i * 3 + 1] - pos[j * 3 + 1]
                val dz = pos[i * 3 + 2] - pos[j * 3 + 2]
                d[j] = if (i == j) Float.MAX_VALUE else sqrt(dx * dx + dy * dy + dz * dz)
            }
            val order = (0 until n).sortedBy { d[it] }.take(k)
            total += order.count { labels[it] == labels[i] }.toDouble() / order.size
        }
        return total / n
    }

    /** Cluster purity: each cluster votes for its most common label. */
    fun purity(clusters: IntArray, labels: List<String>): Double {
        var hit = 0
        for (c in clusters.toSet()) {
            val members = clusters.indices.filter { clusters[it] == c }
            hit += members.groupingBy { labels[it] }.eachCount().values.max()
        }
        return hit.toDouble() / clusters.size
    }

    /** Mean distance from every point to the centre of the layout. */
    fun meanRadius(pos: FloatArray): Float {
        val n = pos.size / 3
        if (n == 0) return 0f
        var cx = 0f; var cy = 0f; var cz = 0f
        for (i in 0 until n) { cx += pos[i * 3]; cy += pos[i * 3 + 1]; cz += pos[i * 3 + 2] }
        cx /= n; cy /= n; cz /= n
        var s = 0f
        for (i in 0 until n) {
            val dx = pos[i * 3] - cx; val dy = pos[i * 3 + 1] - cy; val dz = pos[i * 3 + 2] - cz
            s += sqrt(dx * dx + dy * dy + dz * dz)
        }
        return s / n
    }

    /** Mean distance between two layouts of the same points (how much the map moved). */
    fun meanDisplacement(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        val n = a.size / 3
        if (n == 0) return 0f
        var s = 0f
        for (i in 0 until n) {
            val dx = a[i * 3] - b[i * 3]; val dy = a[i * 3 + 1] - b[i * 3 + 1]; val dz = a[i * 3 + 2] - b[i * 3 + 2]
            s += sqrt(dx * dx + dy * dy + dz * dz)
        }
        return s / n
    }
}

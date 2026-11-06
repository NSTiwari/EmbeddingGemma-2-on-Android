package app.embeddingspace.explorer.domain.cluster

/** What the app knows about one cluster apart from its members. */
data class ClusterInfo(
    val id: Int,
    val name: String,
    /** True when the name is a weak guess (audio-only clusters, or a low match). */
    val guess: Boolean,
    val colorIndex: Int,
    val words: List<String> = emptyList(),
)

/** Keeps cluster ids steady across re-clustering so names and colours do not shuffle. */
object ClusterStabiliser {
    /**
     * Maps freshly computed labels (0..k-1) to ids. A new cluster takes over an old id when most of its members
     * (or most of the old cluster) carry that id. Everything else gets a new id from [freshId].
     */
    fun match(oldIds: IntArray, newLabels: IntArray, freshId: () -> Int): IntArray {
        require(oldIds.size == newLabels.size)
        val k = (newLabels.maxOrNull() ?: -1) + 1
        val overlap = HashMap<Pair<Int, Int>, Int>()
        val newSize = IntArray(k)
        val oldSize = HashMap<Int, Int>()
        for (i in newLabels.indices) {
            newSize[newLabels[i]]++
            if (oldIds[i] >= 0) {
                oldSize[oldIds[i]] = (oldSize[oldIds[i]] ?: 0) + 1
                val key = newLabels[i] to oldIds[i]
                overlap[key] = (overlap[key] ?: 0) + 1
            }
        }
        val mapping = IntArray(k) { -1 }
        val usedOld = HashSet<Int>()
        for ((key, count) in overlap.entries.sortedByDescending { it.value }) {
            val (nl, old) = key
            if (mapping[nl] >= 0 || old in usedOld) continue
            val strong = count * 2 >= newSize[nl] || count * 2 >= (oldSize[old] ?: Int.MAX_VALUE)
            if (strong) { mapping[nl] = old; usedOld.add(old) }
        }
        for (c in 0 until k) if (mapping[c] < 0) mapping[c] = freshId()
        return IntArray(newLabels.size) { mapping[newLabels[it]] }
    }
}

/** Hands out cluster colours so that clusters sitting next to each other get different hues. */
object ClusterColors {
    /**
     * [centres] maps cluster id to its centre in the layout (x, y, z). Clusters keep a colour they already have in
     * [existing]; the rest pick the palette entry least used by their three nearest coloured neighbours.
     */
    fun assign(centres: Map<Int, FloatArray>, existing: Map<Int, Int>, paletteSize: Int): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for ((id, c) in existing) if (id in centres && c in 0 until paletteSize) out[id] = c
        val todo = centres.keys.filter { it !in out }.sorted()
        for (id in todo) {
            val me = centres.getValue(id)
            val near = out.keys.sortedBy { dist(me, centres.getValue(it)) }.take(3)
            val banned = near.map { out.getValue(it) }.toSet()
            val usage = IntArray(paletteSize)
            for (c in out.values) usage[c]++
            val pick = (0 until paletteSize).filter { it !in banned }.minByOrNull { usage[it] * 100 + it }
                ?: (0 until paletteSize).minByOrNull { usage[it] }!!
            out[id] = pick
        }
        return out
    }

    private fun dist(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return dx * dx + dy * dy + dz * dz
    }
}

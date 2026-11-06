package app.embeddingspace.explorer.domain.search

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.space.JoinThresholds
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.vec.Vectors
import kotlin.math.sqrt

/** What the Tiles view shows. All indices point into the space entries. */
sealed interface TilesResult {
    /** The best cluster for the query: its members, most similar first. */
    data class Match(val clusterId: Int, val items: List<Int>) : TilesResult

    /** Nothing close enough to the query. */
    data object NoMatch : TilesResult

    /** No query: every item grouped by cluster, one group after the other. */
    data class Groups(val groups: List<Group>) : TilesResult
}

data class Group(val clusterId: Int, val items: List<Int>)

/**
 * The retrieval view: type something and see only the cluster that fits best, as plain tiles ranked by similarity.
 * A match has to clear the same bars as joining a cluster (centred cosine, text against the item's type), so a
 * word the map knows nothing about gives no match instead of the least bad cluster.
 */
object TilesLogic {
    /** Score of every item for a text query: centred cosine and the z-score inside its media type (as in [SearchRank]). */
    class Scores(val sims: FloatArray, val z: FloatArray)

    fun scores(space: Space, queryRaw: FloatArray): Scores {
        val n = space.size
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
        return Scores(sims, z)
    }

    /** The cluster of the best item that clears its bar, with all members ranked by score. */
    fun forQuery(space: Space, queryRaw: FloatArray, thresholds: JoinThresholds = JoinThresholds()): TilesResult {
        if (space.size == 0) return TilesResult.NoMatch
        val s = scores(space, queryRaw)
        val qualifying = (0 until space.size).filter { s.sims[it] >= thresholds.forPair(MediaType.TEXT, space.entries[it].type) }
        val best = qualifying.maxByOrNull { s.z[it] } ?: return TilesResult.NoMatch
        val cluster = space.entries[best].clusterId
        val members = (0 until space.size).filter { space.entries[it].clusterId == cluster }
        return TilesResult.Match(cluster, members.sortedWith(compareByDescending<Int> { s.z[it] }.thenBy { it }))
    }

    /**
     * Every item grouped by cluster: the biggest cluster first, photos and videos before sounds and notes inside a
     * group, so the grid starts with pictures.
     */
    fun groups(space: Space): TilesResult.Groups {
        val byCluster = space.entries.indices.groupBy { space.entries[it].clusterId }
        fun rank(i: Int) = when (space.entries[i].type) {
            MediaType.IMAGE, MediaType.VIDEO -> 0
            MediaType.TEXT -> 1
            MediaType.AUDIO -> 2
        }
        val groups = byCluster.entries
            .sortedWith(compareByDescending<Map.Entry<Int, List<Int>>> { it.value.size }.thenBy { it.key })
            .map { (id, items) -> Group(id, items.sortedWith(compareBy<Int> { rank(it) }.thenBy { it })) }
        return TilesResult.Groups(groups)
    }

    /** Number of grid columns for a width in dp: two on a phone, three on wider screens. */
    fun columns(widthDp: Int): Int = if (widthDp < 480) 2 else 3
}

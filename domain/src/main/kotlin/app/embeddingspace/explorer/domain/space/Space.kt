package app.embeddingspace.explorer.domain.space

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Neighbor
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.Placement
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.vec.ModalityMeans
import app.embeddingspace.explorer.domain.vec.Vectors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The whole map in memory: raw vectors, per-modality means, centred vectors (cached), 3D positions and
 * cluster ids. Every neighbour, threshold and placement decision here uses centred vectors.
 */
class Space(val dim: Int = 256, val config: SpaceConfig = SpaceConfig()) {
    val entries = ArrayList<SpaceEntry>()
    val means = ModalityMeans(dim)

    private var centred = ArrayList<FloatArray?>()
    private var cachedVersion = -1
    private var addsSinceRecompute = 0
    private var clusterCounter = 0

    val size: Int get() = entries.size

    fun indexOf(id: String): Int = entries.indexOfFirst { it.id == id }

    fun nextClusterId(): Int = clusterCounter++

    /** Keeps the id counter above every id already in use. */
    fun reserveClusterIds(upTo: Int) {
        if (upTo >= clusterCounter) clusterCounter = upTo + 1
    }

    /** Adds an entry as it is (used when loading from storage). No placement is done. */
    fun addExisting(e: SpaceEntry) {
        require(e.raw.size == dim) { "vector has ${e.raw.size} numbers, expected $dim" }
        entries.add(e)
        centred.add(null)
        means.add(e.type, e.raw)
        reserveClusterIds(e.clusterId)
        if (++addsSinceRecompute >= config.meansRecomputeEvery) recomputeMeans()
    }

    fun remove(id: String): Boolean {
        val i = indexOf(id)
        if (i < 0) return false
        val e = entries.removeAt(i)
        centred.removeAt(i)
        means.remove(e.type, e.raw)
        return true
    }

    /** Removes every entry of one origin (the demo items, or everything the user added). Returns how many went. */
    fun removeOrigin(origin: Origin): Int {
        val ids = entries.filter { it.origin == origin }.map { it.id }
        for (id in ids) remove(id)
        recomputeMeans()
        return ids.size
    }

    fun countOrigin(origin: Origin): Int = entries.count { it.origin == origin }

    /** Periodic clean rebuild of the running means. */
    fun recomputeMeans() {
        means.recompute(entries.map { it.type to it.raw })
        addsSinceRecompute = 0
    }

    private fun refreshCache() {
        if (cachedVersion != means.version) {
            for (i in centred.indices) centred[i] = null
            cachedVersion = means.version
        }
    }

    /** The centred, unit length vector of entry [i]. */
    fun centredOf(i: Int): FloatArray {
        refreshCache()
        centred[i]?.let { return it }
        val c = means.centre(entries[i].type, entries[i].raw)
        centred[i] = c
        return c
    }

    fun centredAll(): Array<FloatArray> = Array(entries.size) { centredOf(it) }

    fun similarity(i: Int, j: Int): Float = Vectors.dot(centredOf(i), centredOf(j))

    /** Best matches of an already centred query. [exclude] hides one index, [accept] filters by type. */
    fun nearest(query: FloatArray, k: Int, exclude: Int = -1, accept: ((MediaType) -> Boolean)? = null): List<Neighbor> {
        if (entries.isEmpty() || k <= 0) return emptyList()
        val sims = FloatArray(entries.size) { i ->
            if (i == exclude || (accept != null && !accept(entries[i].type))) Float.NEGATIVE_INFINITY
            else Vectors.dot(query, centredOf(i))
        }
        return Vectors.topK(sims, k, -1).filter { sims[it] > Float.NEGATIVE_INFINITY }.map { Neighbor(it, sims[it]) }
    }

    fun nearestTo(i: Int, k: Int, type: MediaType? = null): List<Neighbor> =
        nearest(centredOf(i), k, exclude = i, accept = type?.let { t -> { m: MediaType -> m == t } })

    /** For one item: its closest [perType] neighbours of every other media type. */
    fun neighborsByOtherType(i: Int, perType: Int = 3): Map<MediaType, List<Neighbor>> {
        val own = entries[i].type
        val out = LinkedHashMap<MediaType, List<Neighbor>>()
        for (t in MediaType.entries) {
            if (t == own) continue
            val hits = nearestTo(i, perType, t)
            if (hits.isNotEmpty()) out[t] = hits
        }
        return out
    }

    /**
     * Decides where a new vector goes and which cluster it joins, without adding it.
     * Position: similarity-weighted average of the nearest neighbours' positions (spike rule).
     * If nothing is similar enough the item starts its own island, set a little apart.
     */
    fun place(type: MediaType, raw: FloatArray, id: String = ""): Placement {
        val c = means.centre(type, raw)
        val hits = nearest(c, config.placeK)
        if (hits.isEmpty()) {
            return Placement(0f, 0f, 0f, emptyList(), 0f, clusterCounter, true, type == MediaType.AUDIO)
        }
        val best = hits[0].sim
        val qualifying = hits.filter {
            val other = entries[it.index].type
            val margin = if (type != MediaType.AUDIO && other == MediaType.AUDIO) config.audioEvidenceMargin else 0f
            it.sim >= config.thresholds.forPair(type, other) + margin
        }
        val tentative = type == MediaType.AUDIO
        var wx = 0.0
        var wy = 0.0
        var wz = 0.0
        var wsum = 0.0
        for (h in hits) {
            val w = exp(((h.sim - best) / config.placeTau).toDouble())
            val e = entries[h.index]
            wx += w * e.x; wy += w * e.y; wz += w * e.z; wsum += w
        }
        if (qualifying.isNotEmpty()) {
            val votes = HashMap<Int, Double>()
            for (h in qualifying) {
                val cid = entries[h.index].clusterId
                votes[cid] = (votes[cid] ?: 0.0) + exp(((h.sim - best) / config.placeTau).toDouble())
            }
            val winner = votes.maxByOrNull { it.value }!!.key
            return Placement((wx / wsum).toFloat(), (wy / wsum).toFloat(), (wz / wsum).toFloat(), hits, best, winner, false, tentative)
        }
        val island = islandPosition(hits[0].index, id)
        return Placement(island[0], island[1], island[2], hits, best, clusterCounter, true, tentative)
    }

    /** Places the vector, adds it to the space and returns the placement. New clusters get a fresh id. */
    fun insert(id: String, type: MediaType, raw: FloatArray, origin: Origin = Origin.USER): Placement {
        val p = place(type, raw, id)
        val cid = if (p.startedNewCluster) nextClusterId() else p.clusterId
        val e = SpaceEntry(id, type, raw, origin, p.x, p.y, p.z, cid, p.tentative)
        addExisting(e)
        return p.copy(clusterId = cid)
    }

    /** Typical layout distance from a point to its closest other point (sampled). Always above zero. */
    fun medianNearestDistance(): Float {
        if (entries.size < 2) return 1f
        val step = maxOf(1, entries.size / 150)
        val ds = ArrayList<Float>()
        var i = 0
        while (i < entries.size) {
            var best = Float.MAX_VALUE
            val a = entries[i]
            for (j in entries.indices) {
                if (j == i) continue
                val b = entries[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dz = a.z - b.z
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d < best) best = d
            }
            ds.add(best)
            i += step
        }
        ds.sort()
        return maxOf(ds[ds.size / 2], 1e-3f)
    }

    private fun islandPosition(nearIndex: Int, id: String): FloatArray {
        val near = entries[nearIndex]
        var cx = 0f
        var cy = 0f
        var cz = 0f
        for (e in entries) { cx += e.x; cy += e.y; cz += e.z }
        cx /= entries.size; cy /= entries.size; cz /= entries.size
        var dx = near.x - cx
        var dy = near.y - cy
        var dz = near.z - cz
        var len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-4f) {
            // every point sits on one spot, so pick a direction from the id to keep it stable
            val h = id.hashCode()
            dx = ((h and 0xff) - 127.5f); dy = (((h shr 8) and 0xff) - 127.5f); dz = (((h shr 16) and 0xff) - 127.5f)
            len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-3f)
        }
        val gap = medianNearestDistance() * 3f
        val out = floatArrayOf(near.x + dx / len * gap, near.y + dy / len * gap, near.z + dz / len * gap)
        var tries = 0
        while (tries < 6 && nearestPointDistance(out) < gap * 0.6f) {
            out[0] += dx / len * gap * 0.5f; out[1] += dy / len * gap * 0.5f; out[2] += dz / len * gap * 0.5f
            tries++
        }
        return out
    }

    private fun nearestPointDistance(p: FloatArray): Float {
        var best = Float.MAX_VALUE
        for (e in entries) {
            val d = abs(e.x - p[0]) + abs(e.y - p[1]) + abs(e.z - p[2])
            if (d < best) best = d
        }
        return best
    }

    /** All positions as one flat array x0,y0,z0,x1,... */
    fun positions(): FloatArray {
        val out = FloatArray(entries.size * 3)
        for (i in entries.indices) {
            out[i * 3] = entries[i].x; out[i * 3 + 1] = entries[i].y; out[i * 3 + 2] = entries[i].z
        }
        return out
    }

    fun setPositions(p: FloatArray) {
        require(p.size == entries.size * 3) { "need ${entries.size * 3} numbers, got ${p.size}" }
        for (i in entries.indices) {
            entries[i].x = p[i * 3]; entries[i].y = p[i * 3 + 1]; entries[i].z = p[i * 3 + 2]
        }
    }

    fun setClusters(labels: IntArray) {
        require(labels.size == entries.size)
        for (i in entries.indices) entries[i].clusterId = labels[i]
        reserveClusterIds(labels.maxOrNull() ?: -1)
    }

    fun countOf(type: MediaType): Int = entries.count { it.type == type }
}

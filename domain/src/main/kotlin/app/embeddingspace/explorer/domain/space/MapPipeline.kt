package app.embeddingspace.explorer.domain.space

import app.embeddingspace.explorer.domain.cluster.ClusterColors
import app.embeddingspace.explorer.domain.cluster.ClusterInfo
import app.embeddingspace.explorer.domain.cluster.ClusterNamer
import app.embeddingspace.explorer.domain.cluster.ClusterSearch
import app.embeddingspace.explorer.domain.cluster.ClusterStabiliser
import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.layout.UmapParams

/** The heavy steps of the map, glued together: layout, clustering and naming. Used by the app and the demo generator. */
object MapPipeline {
    /** Fits the 3D layout on centred vectors. With [seeded] the current positions are the starting point. */
    fun layout(
        space: Space,
        params: UmapParams = UmapParams(),
        seeded: Boolean = false,
        onProgress: ((Float) -> Boolean)? = null,
    ): FloatArray {
        val init = if (seeded && space.size > 3) space.positions() else null
        return UmapLite(params).fit(space.centredAll(), init, onProgress)
    }

    /** Spreads the clusters apart (see [app.embeddingspace.explorer.domain.layout.LayoutShape.separate]) and centres the map on the origin. */
    fun separate(space: Space, factor: Float = 1.5f) {
        if (space.size < 2) return
        val labels = IntArray(space.size) { space.entries[it].clusterId }
        val spread = app.embeddingspace.explorer.domain.layout.LayoutShape.separate(space.positions(), labels, factor)
        // a small map also gets its points settled so a handful of big cards do not pile on each other
        if (space.size <= 40) {
            var r = 0f
            for (i in 0 until space.size) r = maxOf(r, kotlin.math.sqrt(spread[i * 3] * spread[i * 3] + spread[i * 3 + 1] * spread[i * 3 + 1] + spread[i * 3 + 2] * spread[i * 3 + 2]))
            app.embeddingspace.explorer.domain.layout.LayoutShape.spaceOut(spread, 0.42f * r)
        }
        space.setPositions(spread)
    }

    /** Cluster centres in layout space, by cluster id. */
    fun centres(space: Space): Map<Int, FloatArray> {
        val sums = HashMap<Int, FloatArray>()
        val counts = HashMap<Int, Int>()
        for (e in space.entries) {
            val s = sums.getOrPut(e.clusterId) { FloatArray(3) }
            s[0] += e.x; s[1] += e.y; s[2] += e.z
            counts[e.clusterId] = (counts[e.clusterId] ?: 0) + 1
        }
        for ((id, s) in sums) { val c = counts.getValue(id); s[0] /= c; s[1] /= c; s[2] /= c }
        return sums
    }

    /**
     * Gives every cluster in the space an info record. Clusters that already have one keep it, unless their id is in
     * [rename]. Clusters with no members are dropped. Colours are chosen so neighbours differ.
     */
    fun describe(
        space: Space,
        namer: ClusterNamer?,
        previous: Map<Int, ClusterInfo>,
        paletteSize: Int = 12,
        rename: Set<Int> = emptySet(),
    ): Map<Int, ClusterInfo> {
        val ids = space.entries.map { it.clusterId }.toSet()
        val centres = centres(space)
        val colors = ClusterColors.assign(centres, previous.mapValues { it.value.colorIndex }.filterKeys { it in ids }, paletteSize)
        val byId = space.entries.indices.groupBy { space.entries[it].clusterId }
        val sizeOrder = ids.sortedByDescending { byId[it]?.size ?: 0 }
        val used = HashSet<String>()
        val out = LinkedHashMap<Int, ClusterInfo>()
        // keep existing names first so new clusters avoid them
        for (id in sizeOrder) previous[id]?.takeIf { id !in rename }?.let { used.add(it.name) }
        for (id in sizeOrder) {
            val old = previous[id]
            if (old != null && id !in rename) {
                out[id] = old.copy(colorIndex = colors.getValue(id))
                continue
            }
            val members = byId.getValue(id).map { space.entries[it].type to space.centredOf(it) }
            val res = namer?.name(members, used)
            val name = res?.label ?: "Cluster ${id + 1}"
            used.add(name)
            out[id] = ClusterInfo(id, name, res?.guess ?: true, colors.getValue(id), res?.words ?: emptyList())
        }
        return out
    }

    /** Full re-clustering. Ids stay stable where the membership overlaps. Returns the new infos. */
    fun recluster(
        space: Space,
        namer: ClusterNamer?,
        previous: Map<Int, ClusterInfo>,
        paletteSize: Int = 12,
    ): Map<Int, ClusterInfo> {
        if (space.size == 0) return emptyMap()
        val outcome = ClusterSearch.best(space.centredAll())
        val old = IntArray(space.size) { space.entries[it].clusterId }
        val ids = ClusterStabiliser.match(old, outcome.labels) { space.nextClusterId() }
        space.setClusters(ids)
        val newIds = ids.toSet()
        // a matched cluster whose members changed a lot gets a new name too
        val rename = newIds.filter { id -> id !in previous || membershipChanged(old, ids, id) }.toSet()
        return describe(space, namer, previous.filterKeys { it in newIds }, paletteSize, rename)
    }

    private fun membershipChanged(old: IntArray, now: IntArray, id: Int): Boolean {
        var before = 0
        var after = 0
        var both = 0
        for (i in now.indices) {
            val b = old[i] == id
            val a = now[i] == id
            if (b) before++
            if (a) after++
            if (a && b) both++
        }
        return both * 4 < maxOf(before, after) * 3
    }
}

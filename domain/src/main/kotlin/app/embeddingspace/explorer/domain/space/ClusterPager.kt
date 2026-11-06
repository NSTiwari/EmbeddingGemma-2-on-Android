package app.embeddingspace.explorer.domain.space

/**
 * The order and index rules for the preview pager. Tapping a card opens a pager over its whole cluster:
 * the tapped item first, then the rest, most similar first. Similarities are centred cosine.
 */
object ClusterPager {
    /** Pages are capped so a huge cluster does not make the sheet heavy. */
    const val MAX_PAGES = 60

    /**
     * Space indices of everything in the cluster of [tapped], the tapped item at position 0, then the others by
     * falling similarity to it. Ties keep the lower index first.
     */
    fun order(space: Space, tapped: Int, maxPages: Int = MAX_PAGES): List<Int> {
        val cluster = space.entries[tapped].clusterId
        val others = space.entries.indices.filter { it != tapped && space.entries[it].clusterId == cluster }
        val sims = HashMap<Int, Float>()
        for (i in others) sims[i] = space.similarity(tapped, i)
        val sorted = others.sortedWith(compareByDescending<Int> { sims.getValue(it) }.thenBy { it })
        return (listOf(tapped) + sorted).take(maxPages)
    }

    /** The page to show after the page at [index] was deleted from a pager of [count] pages. Null means it is now empty. */
    fun indexAfterDelete(count: Int, index: Int): Int? {
        if (count <= 1) return null
        return if (index >= count - 1) count - 2 else index
    }

    /** The small "3 / 12" position text. Null for a single page, where no pager chrome is shown. */
    fun position(index: Int, count: Int): String? = if (count <= 1) null else "${index + 1} / $count"
}

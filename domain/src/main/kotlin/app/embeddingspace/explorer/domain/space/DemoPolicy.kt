package app.embeddingspace.explorer.domain.space

/**
 * Rules for the demo items. They are put on the map once, on the very first run. After that they never come back,
 * not on restart and not on an app update, even if the user deleted them all.
 */
object DemoPolicy {
    /** Seed the demo only on a fresh install: never seeded before and nothing stored. */
    fun shouldSeed(seededFlag: Boolean, storedItems: Int): Boolean = !seededFlag && storedItems == 0

    /** An install made before the flag existed already has items, so it counts as seeded. */
    fun shouldMarkSeeded(seededFlag: Boolean, storedItems: Int): Boolean = !seededFlag && storedItems > 0

    /** The hint under the search bar shows while demo items exist, until the user dismisses it. */
    fun hintVisible(demoItems: Int, dismissed: Boolean): Boolean = demoItems > 0 && !dismissed
}

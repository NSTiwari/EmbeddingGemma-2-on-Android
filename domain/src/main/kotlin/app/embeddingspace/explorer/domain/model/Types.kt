package app.embeddingspace.explorer.domain.model

/** The four kinds of things the map can hold. */
enum class MediaType(val key: String) {
    IMAGE("image"), AUDIO("audio"), VIDEO("video"), TEXT("text");

    /** Video frames are images, so a video is centred with the image mean. */
    val centringGroup: MediaType get() = if (this == VIDEO) IMAGE else this

    companion object {
        fun fromKey(k: String): MediaType = entries.firstOrNull { it.key == k } ?: error("unknown media type: $k")
    }
}

/** Where an item came from. Demo and user items live in the same map. */
enum class Origin { DEMO, USER }

/** A point in the map with everything the maths needs. The raw vector is never overwritten. */
class SpaceEntry(
    val id: String,
    val type: MediaType,
    val raw: FloatArray,
    val origin: Origin = Origin.USER,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var clusterId: Int = -1,
    var tentative: Boolean = false,
)

/** One hit of a nearest-neighbour query. [index] points into the space entries. */
data class Neighbor(val index: Int, val sim: Float)

/** What happened when an item was dropped into the map. */
data class Placement(
    val x: Float,
    val y: Float,
    val z: Float,
    val neighbors: List<Neighbor>,
    val bestSim: Float,
    val clusterId: Int,
    val startedNewCluster: Boolean,
    val tentative: Boolean,
)

package app.embeddingspace.explorer.data

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin

/** Everything the screens need to know about one item. Immutable. */
data class ItemUi(
    val id: String,
    val type: MediaType,
    val title: String,
    val origin: Origin,
    val clusterId: Int,
    val tentative: Boolean = false,
    val thumbPath: String? = null,
    val mediaPath: String? = null,
    val text: String? = null,
    val creator: String? = null,
    val license: String? = null,
    val licenseUrl: String? = null,
    val sourceUrl: String? = null,
    val durationMs: Long = 0L,
    val createdAt: Long = 0L,
)

/** A cluster as the map and the preview line need it: a name and a colour. */
data class ClusterUi(
    val id: Int,
    val name: String,
    val guess: Boolean,
    val colorIndex: Int,
)

/** What the map draws: items, their positions (x,y,z each) and the clusters (for colours). */
class MapSnapshot(
    val items: List<ItemUi>,
    val positions: FloatArray,
    val clusters: List<ClusterUi>,
    val version: Long = 0L,
) {
    val size: Int get() = items.size

    fun indexOf(id: String): Int = items.indexOfFirst { it.id == id }

    fun cluster(id: Int): ClusterUi? = clusters.firstOrNull { it.id == id }
}

/** A neighbour of the selected item, shown as a small chip. */
data class NeighborUi(val item: ItemUi, val simPercent: Int, val likely: Boolean)

/** The selected item with its cluster and nearest neighbours of the other media types. */
data class DetailUi(
    val item: ItemUi,
    val cluster: ClusterUi?,
    val neighbors: List<NeighborUi>,
)

/** Result of dropping a new item into the map, for the toast chip and the arrival animation. */
data class LandedUi(
    val item: ItemUi,
    val clusterName: String,
    val newCluster: Boolean,
    val links: List<LinkUi>,
)

data class LinkUi(val itemId: String, val simPercent: Int)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The two ways to look at the map: the 3D space, or a plain grid of search results. */
enum class ViewMode { SPACE, TILES }

/** One tile of the grid: the item and the colour of its cluster. */
data class TileUi(val item: ItemUi, val colorIndex: Int)

/** What the Tiles view shows. */
sealed interface TilesUi {
    /** The best cluster for the query, its members ranked by similarity. */
    data class Match(val clusterName: String, val tiles: List<TileUi>) : TilesUi

    /** Nothing close enough to the query. */
    data object NoMatch : TilesUi

    /** No query: all items grouped by cluster. */
    data class Groups(val groups: List<List<TileUi>>) : TilesUi
}

/** User settings, persisted with DataStore. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val viewMode: ViewMode = ViewMode.SPACE,
    val demoHintDismissed: Boolean = false,
    val additionsSinceRelayout: Int = 0,
)

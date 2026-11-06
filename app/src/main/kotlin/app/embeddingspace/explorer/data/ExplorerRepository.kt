package app.embeddingspace.explorer.data

import android.content.Context
import app.embeddingspace.explorer.data.db.ClusterEntity
import app.embeddingspace.explorer.data.db.ExplorerDb
import app.embeddingspace.explorer.data.db.ItemEntity
import app.embeddingspace.explorer.domain.cluster.ClusterInfo
import app.embeddingspace.explorer.domain.cluster.ClusterNamer
import app.embeddingspace.explorer.domain.cluster.Vocabulary
import app.embeddingspace.explorer.domain.insights.Insights
import app.embeddingspace.explorer.domain.io.DemoManifest
import app.embeddingspace.explorer.domain.io.FloatMatrix
import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.search.SearchRank
import app.embeddingspace.explorer.domain.search.SearchResult
import app.embeddingspace.explorer.domain.search.TilesLogic
import app.embeddingspace.explorer.domain.search.TilesResult
import app.embeddingspace.explorer.domain.space.ClusterPager
import app.embeddingspace.explorer.domain.space.DemoPolicy
import app.embeddingspace.explorer.domain.space.MapPipeline
import app.embeddingspace.explorer.domain.space.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Owns the in-memory map (the domain [Space]) and keeps Room in step with it. All access goes through [lock]. */
class ExplorerRepository(private val context: Context, private val db: ExplorerDb, private val settings: SettingsStore) {
    var space = Space()
        private set
    private val lock = Mutex()
    private val metas = HashMap<String, ItemUi>()
    private var clusterInfos: Map<Int, ClusterInfo> = emptyMap()
    private var version = 0L
    private var vocab: Vocabulary? = null

    val demoDir: File get() = File(context.filesDir, "demo")

    /**
     * Loads everything from Room into memory. The demo goes on the map once, on a fresh install, and never again:
     * the "seeded" flag outlives restarts and updates, so deleted demo items stay deleted.
     */
    suspend fun load() = lock.withLock {
        withContext(Dispatchers.Default) {
            val seeded = settings.demoSeeded()
            val stored = db.items().count()
            if (DemoPolicy.shouldSeed(seeded, stored)) seedDemo()
            if (!seeded) settings.setDemoSeeded()
            fillFromDb()
        }
    }

    private suspend fun fillFromDb() {
        space = Space()
        metas.clear()
        for (e in db.items().all()) {
            val entry = SpaceEntry(
                e.id, MediaType.fromKey(e.type), FloatMatrix.fromBlob(e.vec),
                if (e.source == "demo") Origin.DEMO else Origin.USER, e.x, e.y, e.z, e.clusterId, e.tentative,
            )
            space.addExisting(entry)
            metas[e.id] = e.toUi()
        }
        space.recomputeMeans()
        clusterInfos = db.clusters().all().associate {
            it.id to ClusterInfo(it.id, it.name, it.guess, it.colorIndex, it.words.split(',').filter { w -> w.isNotBlank() })
        }
        version++
    }

    private fun ItemEntity.toUi() = ItemUi(
        id, MediaType.fromKey(type), title, if (source == "demo") Origin.DEMO else Origin.USER, clusterId, tentative,
        thumbPath, mediaPath, text, creator, license, licenseUrl, sourceUrl, durationMs, createdAt,
    )

    // ---------------------------------------------------------------- demo

    private fun readAsset(name: String): ByteArray = context.assets.open("demo/$name").use { it.readBytes() }

    fun loadDemoManifest(): DemoManifest = DemoManifest.parse(String(readAsset("demo.json")))

    private suspend fun seedDemo() {
        val manifest = loadDemoManifest()
        val vecs = FloatMatrix.read(readAsset("vectors.bin"), manifest.dim)
        demoDir.mkdirs()
        val entities = manifest.items.mapIndexed { i, it ->
            val media = it.media?.let { rel -> copyAsset(rel) }
            val thumb = it.thumb?.let { rel -> copyAsset(rel) }
            ItemEntity(
                id = it.id, source = "demo", type = it.type, title = it.title, text = it.text, mediaPath = media,
                thumbPath = thumb, creator = it.creator, license = it.license, licenseUrl = it.licenseUrl,
                sourceUrl = it.sourceUrl, concept = it.concept, durationMs = if (it.type == "audio") 10_000L else 0L,
                createdAt = i.toLong(), vec = FloatMatrix.toBlob(vecs[i]), x = it.x, y = it.y, z = it.z, clusterId = it.cluster,
            )
        }
        db.items().insertAll(entities)
        db.clusters().replaceAll(manifest.clusters.map { ClusterEntity(it.id, it.name, it.guess, it.color, it.words.joinToString(",")) })
    }

    private fun copyAsset(rel: String): String {
        val out = File(demoDir, rel)
        if (!out.exists() || out.length() == 0L) {
            out.parentFile?.mkdirs()
            context.assets.open("demo/$rel").use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
        return out.absolutePath
    }

    // ---------------------------------------------------------------- vocabulary

    private fun vocabulary(): Vocabulary {
        vocab?.let { return it }
        val v = Vocabulary.parse(String(readAsset("vocab.txt")), readAsset("vocab.bin"), 256)
        vocab = v
        return v
    }

    private fun namer(): ClusterNamer = ClusterNamer(vocabulary(), space.means.mean(MediaType.TEXT))

    /** Raw vector of a vocabulary word, or null. Lets search work for known words without the model. */
    fun vocabVector(word: String): FloatArray? {
        val v = vocabulary()
        val i = v.words.indexOf(word.trim().lowercase())
        return if (i >= 0) v.vectors[i] else null
    }

    // ---------------------------------------------------------------- reading

    private fun itemUi(i: Int): ItemUi {
        val e = space.entries[i]
        return (metas[e.id] ?: error("no meta for ${e.id}")).copy(clusterId = e.clusterId, tentative = e.tentative)
    }

    private fun clusterUi(id: Int): ClusterUi? = clusterInfos[id]?.let { ClusterUi(it.id, it.name, it.guess, it.colorIndex) }

    suspend fun snapshot(): MapSnapshot = lock.withLock {
        val present = space.entries.map { it.clusterId }.toSet()
        MapSnapshot(List(space.size) { itemUi(it) }, space.positions(), present.mapNotNull { clusterUi(it) }, version)
    }

    suspend fun detail(id: String): DetailUi? = lock.withLock {
        val i = space.indexOf(id)
        if (i < 0) return@withLock null
        val links = Insights.crossLinks(space, i, 3)
        DetailUi(
            itemUi(i), clusterUi(space.entries[i].clusterId),
            links.map { NeighborUi(itemUi(it.index), (it.sim * 100).toInt().coerceIn(0, 100), it.likely) },
        )
    }

    private fun detailAt(k: Int): DetailUi {
        val links = Insights.crossLinks(space, k, 3)
        return DetailUi(
            itemUi(k), clusterUi(space.entries[k].clusterId),
            links.map { NeighborUi(itemUi(it.index), (it.sim * 100).toInt().coerceIn(0, 100), it.likely) },
        )
    }

    /** Every item of the cluster of [id], the tapped one first and then the most similar. One page each. */
    suspend fun pager(id: String): List<DetailUi> = lock.withLock {
        val i = space.indexOf(id)
        if (i < 0) return@withLock emptyList()
        ClusterPager.order(space, i).map { detailAt(it) }
    }

    /** One page for each of [ids], in that order. Used to swipe through the tiles on screen. */
    suspend fun pagesFor(ids: List<String>): List<DetailUi> = lock.withLock {
        ids.mapNotNull { id -> space.indexOf(id).takeIf { it >= 0 }?.let { detailAt(it) } }
    }

    private fun tile(i: Int) = TileUi(itemUi(i), clusterInfos[space.entries[i].clusterId]?.colorIndex ?: 0)

    /**
     * The Tiles view. With a query vector: the best cluster and its members, or no match. Without one: everything
     * grouped by cluster.
     */
    suspend fun tiles(query: FloatArray?): TilesUi = lock.withLock {
        val r = if (query == null) TilesLogic.groups(space) else TilesLogic.forQuery(space, query)
        when (r) {
            is TilesResult.Match -> TilesUi.Match(clusterInfos[r.clusterId]?.name ?: "", r.items.map { tile(it) })
            TilesResult.NoMatch -> TilesUi.NoMatch
            is TilesResult.Groups -> TilesUi.Groups(r.groups.map { g -> g.items.map { tile(it) } })
        }
    }

    /** Glow strengths per item index (same order as the snapshot) and the best hit id. */
    suspend fun search(query: FloatArray): Pair<SearchResult, List<String>> = lock.withLock {
        val r = SearchRank.search(space, query)
        r to r.hits.map { space.entries[it.index].id }
    }

    suspend fun idAt(index: Int): String? = lock.withLock { space.entries.getOrNull(index)?.id }

    suspend fun demoCount(): Int = lock.withLock { space.countOrigin(Origin.DEMO) }

    suspend fun userItemCount(): Int = lock.withLock { space.countOrigin(Origin.USER) }

    // ---------------------------------------------------------------- writing

    /** Drops a new, already embedded item into the map. */
    suspend fun addItem(
        id: String, type: MediaType, title: String, raw: FloatArray, text: String? = null, mediaPath: String? = null,
        thumbPath: String? = null, durationMs: Long = 0L,
    ): LandedUi = lock.withLock {
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val p = space.insert(id, type, raw, Origin.USER)
            val ui = ItemUi(id, type, title, Origin.USER, p.clusterId, p.tentative, thumbPath, mediaPath, text, null, null, null, null, durationMs, now)
            metas[id] = ui
            if (p.startedNewCluster || clusterInfos[p.clusterId] == null) {
                clusterInfos = MapPipeline.describe(space, namer(), clusterInfos, PALETTE, setOf(p.clusterId))
            }
            db.items().insert(
                ItemEntity(
                    id = id, source = "user", type = type.key, title = title, text = text, mediaPath = mediaPath, thumbPath = thumbPath,
                    durationMs = durationMs, createdAt = now, vec = FloatMatrix.toBlob(raw), x = p.x, y = p.y, z = p.z,
                    clusterId = p.clusterId, tentative = p.tentative,
                ),
            )
            persistClusters()
            version++
            val name = clusterInfos[p.clusterId]?.name ?: ""
            LandedUi(ui, name, p.startedNewCluster, p.neighbors.take(3).map { LinkUi(space.entries[it.index].id, (it.sim * 100).toInt().coerceIn(0, 100)) })
        }
    }

    suspend fun delete(id: String) = lock.withLock {
        withContext(Dispatchers.Default) {
            val meta = metas.remove(id)
            space.remove(id)
            db.items().delete(id)
            if (meta?.origin == Origin.USER) {
                meta.mediaPath?.let { File(it).delete() }
                meta.thumbPath?.let { File(it).delete() }
            }
            pruneClusters()
            version++
        }
    }

    /** Permanently removes every demo item and its files. It never comes back (see [load]). */
    suspend fun deleteDemo() = lock.withLock {
        withContext(Dispatchers.Default) {
            space.removeOrigin(Origin.DEMO)
            metas.entries.removeAll { it.value.origin == Origin.DEMO }
            db.items().deleteBySource("demo")
            demoDir.deleteRecursively()
            pruneClusters()
            version++
        }
    }

    /** Permanently removes everything the user added, with its files and the waiting queue. */
    suspend fun deleteUserItems() = lock.withLock {
        withContext(Dispatchers.Default) {
            for (u in metas.values.filter { it.origin == Origin.USER }) {
                u.mediaPath?.let { File(it).delete() }
                u.thumbPath?.let { File(it).delete() }
            }
            space.removeOrigin(Origin.USER)
            metas.entries.removeAll { it.value.origin == Origin.USER }
            db.items().deleteBySource("user")
            db.pending().deleteAll()
            pruneClusters()
            version++
        }
    }

    private suspend fun pruneClusters() {
        val alive = space.entries.map { it.clusterId }.toSet()
        clusterInfos = clusterInfos.filterKeys { it in alive }
        persistClusters()
    }

    /**
     * Refits the whole layout on the current vectors (seeded with the old positions so the map keeps its shape),
     * then re-clusters. Returns false if [onProgress] cancelled it.
     */
    suspend fun relayout(onProgress: (Float) -> Boolean): Boolean {
        val (ids, x, init) = lock.withLock {
            Triple(space.entries.map { it.id }, space.centredAll(), space.positions())
        }
        if (ids.size < 4) return true
        var cancelled = false
        val pos = withContext(Dispatchers.Default) {
            UmapLite().fit(x, init) { p -> onProgress(p).also { ok -> if (!ok) cancelled = true } }
        }
        if (cancelled) return false
        lock.withLock {
            withContext(Dispatchers.Default) {
                val byId = HashMap<String, Int>()
                ids.forEachIndexed { i, id -> byId[id] = i }
                for (e in space.entries) {
                    val i = byId[e.id] ?: continue
                    e.x = pos[i * 3]; e.y = pos[i * 3 + 1]; e.z = pos[i * 3 + 2]
                }
                clusterInfos = MapPipeline.recluster(space, namer(), clusterInfos, PALETTE)
                MapPipeline.separate(space)
                for (e in space.entries) metas[e.id]?.let { metas[e.id] = it.copy(clusterId = e.clusterId) }
                persistAll()
                version++
            }
        }
        return true
    }

    private suspend fun persistClusters() {
        db.clusters().replaceAll(clusterInfos.values.map { ClusterEntity(it.id, it.name, it.guess, it.colorIndex, it.words.joinToString(",")) })
    }

    private suspend fun persistAll() {
        db.items().updatePlacements(
            space.entries.map { e ->
                ItemEntity(id = e.id, source = "", type = "", title = "", vec = ByteArray(0), x = e.x, y = e.y, z = e.z, clusterId = e.clusterId, tentative = e.tentative)
            },
        )
        persistClusters()
    }

    companion object {
        const val PALETTE = 12
    }
}

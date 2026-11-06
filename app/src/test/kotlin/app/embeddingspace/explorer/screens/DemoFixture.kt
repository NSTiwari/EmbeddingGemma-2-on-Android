package app.embeddingspace.explorer.screens

import app.cash.paparazzi.DeviceConfig
import app.embeddingspace.explorer.data.ClusterUi
import app.embeddingspace.explorer.data.DetailUi
import app.embeddingspace.explorer.data.ItemUi
import app.embeddingspace.explorer.data.MapSnapshot
import app.embeddingspace.explorer.data.NeighborUi
import app.embeddingspace.explorer.domain.audio.Wav
import app.embeddingspace.explorer.domain.audio.Waveform
import app.embeddingspace.explorer.domain.insights.Insights
import app.embeddingspace.explorer.domain.io.DemoManifest
import app.embeddingspace.explorer.domain.io.FloatMatrix
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.space.ClusterPager
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.ui.map.MapScene
import app.embeddingspace.explorer.ui.media.ThumbCache
import com.android.resources.Density
import java.io.File

/** 720x1600 at 320 dpi, which is 360x800 dp. The reference phone. */
val PHONE_720 = DeviceConfig.PIXEL_5.copy(screenWidth = 720, screenHeight = 1600, xdpi = 320, ydpi = 320, density = Density.XHIGH)

/** Landscape: the reference phone turned on its side. */
val PHONE_LANDSCAPE = DeviceConfig.PIXEL_5.copy(screenWidth = 720, screenHeight = 1600, xdpi = 320, ydpi = 320, density = Density.XHIGH, orientation = com.android.resources.ScreenOrientation.LANDSCAPE)

/** The phone from the brief at 1080x2340, 480 dpi. */
val PHONE_1080 = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 2340, xdpi = 480, ydpi = 480, density = Density.XXHIGH)

/** The real demo data (vectors, layout, clusters, media) read from the shipped assets, never typed by hand. */
object DemoFixture {
    val dir = File("src/main/assets/demo")
    val manifest: DemoManifest by lazy { DemoManifest.parse(File(dir, "demo.json").readText()) }
    private val vecs by lazy { FloatMatrix.read(File(dir, "vectors.bin").readBytes(), manifest.dim) }

    private fun path(rel: String?): String? = rel?.let { File(dir, it).absolutePath }

    val items: List<ItemUi> by lazy {
        manifest.items.map {
            ItemUi(
                it.id, MediaType.fromKey(it.type), it.title, Origin.DEMO, it.cluster, thumbPath = path(it.thumb), mediaPath = path(it.media),
                text = it.text, creator = it.creator, license = it.license, licenseUrl = it.licenseUrl, sourceUrl = it.sourceUrl,
                durationMs = if (it.type == "audio") 10_000L else 0L,
            )
        }
    }

    val clusters: List<ClusterUi> by lazy {
        manifest.clusters.map { ClusterUi(it.id, it.name, it.guess, it.color) }
    }

    val snapshot: MapSnapshot by lazy {
        val pos = FloatArray(items.size * 3)
        manifest.items.forEachIndexed { i, it -> pos[i * 3] = it.x; pos[i * 3 + 1] = it.y; pos[i * 3 + 2] = it.z }
        MapSnapshot(items, pos, clusters)
    }

    val space: Space by lazy {
        Space(manifest.dim).also { s ->
            manifest.items.forEachIndexed { i, it ->
                s.addExisting(SpaceEntry(it.id, MediaType.fromKey(it.type), vecs[i], Origin.DEMO, it.x, it.y, it.z, it.cluster))
            }
        }
    }

    /** Loads every thumbnail into the cache, so the map can draw them on the first frame. */
    fun preloadThumbs() {
        for (it in items) it.thumbPath?.let { p -> ThumbCache.loadCard(p); ThumbCache.loadSquare(p, 160); ThumbCache.loadSquare(p, 128) }
        for (it in items) if (it.type == MediaType.AUDIO) it.mediaPath?.let { p -> app.embeddingspace.explorer.ui.media.WaveCache.load(p) }
    }

    fun scene(): MapScene {
        preloadThumbs()
        return MapScene().also { it.apply(snapshot, animate = false) }
    }

    /** A map with nothing on it, like after everything was deleted. */
    fun emptyScene(): MapScene = MapScene().also { it.apply(MapSnapshot(emptyList(), FloatArray(0), emptyList()), animate = false) }

    /** The pager for the card [id]: its whole cluster, the tapped one first, then the most similar. */
    fun pages(id: String): List<DetailUi> = ClusterPager.order(space, space.indexOf(id)).map { detail(items[it].id) }

    fun item(id: String): ItemUi = items.first { it.id == id }

    fun detail(id: String): DetailUi {
        val it = item(id)
        val i = space.indexOf(id)
        val links = Insights.crossLinks(space, i, 3)
        return DetailUi(
            it, clusters.firstOrNull { c -> c.id == it.clusterId },
            links.map { l -> NeighborUi(items[l.index], (l.sim * 100).toInt().coerceIn(0, 100), l.likely) },
        )
    }

    private val vocab by lazy {
        app.embeddingspace.explorer.domain.cluster.Vocabulary.parse(File(dir, "vocab.txt").readText(), File(dir, "vocab.bin").readBytes(), manifest.dim)
    }

    private fun tile(i: Int) = app.embeddingspace.explorer.data.TileUi(items[i], clusters.firstOrNull { it.id == items[i].clusterId }?.colorIndex ?: 0)

    /** What the Tiles view shows for a typed word, or for no query (all items grouped) when [word] is null. */
    fun tiles(word: String?): app.embeddingspace.explorer.data.TilesUi {
        preloadThumbs()
        val r = if (word == null) {
            app.embeddingspace.explorer.domain.search.TilesLogic.groups(space)
        } else {
            val v = vocab.vectors[vocab.words.indexOf(word)]
            app.embeddingspace.explorer.domain.search.TilesLogic.forQuery(space, v)
        }
        return when (r) {
            is app.embeddingspace.explorer.domain.search.TilesResult.Match ->
                app.embeddingspace.explorer.data.TilesUi.Match(clusters.first { it.id == r.clusterId }.name, r.items.map { tile(it) })
            app.embeddingspace.explorer.domain.search.TilesResult.NoMatch -> app.embeddingspace.explorer.data.TilesUi.NoMatch
            is app.embeddingspace.explorer.domain.search.TilesResult.Groups ->
                app.embeddingspace.explorer.data.TilesUi.Groups(r.groups.map { g -> g.items.map { tile(it) } })
        }
    }

    fun peaks(id: String, bars: Int = 72): FloatArray {
        val wav = Wav.parse(File(item(id).mediaPath!!).readBytes())!!
        return Waveform.peaks(wav.samples, bars)
    }
}

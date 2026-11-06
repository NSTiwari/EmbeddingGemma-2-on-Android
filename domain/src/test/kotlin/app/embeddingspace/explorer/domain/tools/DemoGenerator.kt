package app.embeddingspace.explorer.domain.tools

import app.embeddingspace.explorer.domain.cluster.ClusterNamer
import app.embeddingspace.explorer.domain.cluster.Vocabulary
import app.embeddingspace.explorer.domain.io.DemoCluster
import app.embeddingspace.explorer.domain.io.DemoManifest
import app.embeddingspace.explorer.domain.io.FloatMatrix
import app.embeddingspace.explorer.domain.layout.LayoutMetrics
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.space.MapPipeline
import app.embeddingspace.explorer.domain.space.Space
import java.io.File

/**
 * Builds the shipped demo map: UMAP-lite layout, clusters and names from the precomputed vectors.
 * Run with: gradlew :domain:generateDemo
 */
fun main(args: Array<String>) {
    val dir = File(args[0])
    val base = DemoManifest.parse(File(dir, "demo_base.json").readText())
    val vecs = FloatMatrix.read(File(dir, "vectors.bin").readBytes(), base.dim)
    val vocab = Vocabulary.parse(File(dir, "vocab.txt").readText(), File(dir, "vocab.bin").readBytes(), base.dim)
    val space = Space(base.dim)
    base.items.forEachIndexed { i, it ->
        space.addExisting(SpaceEntry(it.id, MediaType.fromKey(it.type), vecs[i], Origin.DEMO))
    }
    val pos = MapPipeline.layout(space)
    space.setPositions(pos)
    val namer = ClusterNamer(vocab, space.means.mean(MediaType.TEXT))
    val infos = MapPipeline.recluster(space, namer, emptyMap())
    // clusters pushed apart so the map reads as separate groups, centred on the origin
    MapPipeline.separate(space)
    val concepts = base.items.map { it.concept }
    println("layout 5-NN same concept: %.3f".format(LayoutMetrics.knnSameLabel(pos, concepts)))
    println("clusters: ${infos.size}, purity %.3f".format(LayoutMetrics.purity(IntArray(space.size) { space.entries[it].clusterId }, concepts)))
    val items = base.items.mapIndexed { i, it ->
        it.copy(x = space.entries[i].x, y = space.entries[i].y, z = space.entries[i].z, cluster = space.entries[i].clusterId)
    }
    val clusters = infos.values.sortedBy { it.id }.map { DemoCluster(it.id, it.name, it.guess, it.colorIndex, it.words) }
    for (c in clusters) {
        val members = items.filter { it.cluster == c.id }
        println("  ${c.id} ${c.name} guess=${c.guess} n=${members.size} mix=${members.groupingBy { it.type }.eachCount()} concepts=${members.map { it.concept }.distinct()}")
    }
    File(dir, "demo.json").writeText(DemoManifest.encode(DemoManifest(base.dim, items, clusters)))
    println("wrote demo.json")
}

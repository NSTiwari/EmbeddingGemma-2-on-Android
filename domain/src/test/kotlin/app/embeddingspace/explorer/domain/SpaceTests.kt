package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.layout.UmapParams
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.space.JoinThresholds
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.space.SpaceConfig
import app.embeddingspace.explorer.domain.vec.Vectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class JoinThresholdsTest {
    private val t = JoinThresholds()

    @Test fun imageToImageUsesTheImageBar() = assertEquals(0.30f, t.forPair(MediaType.IMAGE, MediaType.IMAGE), 0f)

    @Test fun imageToTextUsesTheImageBar() = assertEquals(0.30f, t.forPair(MediaType.IMAGE, MediaType.TEXT), 0f)

    @Test fun textToTextIsStricter() = assertEquals(0.45f, t.forPair(MediaType.TEXT, MediaType.TEXT), 0f)

    @Test fun anythingWithAudioUsesTheLowBar() {
        assertEquals(0.20f, t.forPair(MediaType.AUDIO, MediaType.TEXT), 0f)
        assertEquals(0.20f, t.forPair(MediaType.IMAGE, MediaType.AUDIO), 0f)
        assertEquals(0.20f, t.forPair(MediaType.AUDIO, MediaType.AUDIO), 0f)
    }

    @Test fun videoCountsAsImage() = assertEquals(0.30f, t.forPair(MediaType.VIDEO, MediaType.TEXT), 0f)

    @Test fun shiftedMovesAllBars() {
        val s = t.shifted(0.1f)
        assertEquals(0.40f, s.imageLike, 1e-6f)
        assertEquals(0.55f, s.textText, 1e-6f)
        assertEquals(0.30f, s.audio, 1e-6f)
    }
}

class SpaceTest {
    private fun small(): Space {
        val s = Space(4, SpaceConfig(meansRecomputeEvery = 1000))
        // three text items near a, three near b, positions along x
        val a = floatArrayOf(1f, 0.1f, 0f, 0f)
        val b = floatArrayOf(0f, 0.1f, 1f, 0f)
        for (k in 0 until 3) s.addExisting(SpaceEntry("a$k", MediaType.TEXT, Vectors.normalized(a.copyOf().also { it[3] = k * 0.05f }), Origin.USER, -5f + k * 0.1f, 0f, 0f, 0))
        for (k in 0 until 3) s.addExisting(SpaceEntry("b$k", MediaType.TEXT, Vectors.normalized(b.copyOf().also { it[3] = k * 0.05f }), Origin.USER, 5f + k * 0.1f, 0f, 0f, 1))
        return s
    }

    @Test fun addExistingCountsAndIndexes() {
        val s = small()
        assertEquals(6, s.size)
        assertEquals(3, s.indexOf("b0"))
        assertEquals(-1, s.indexOf("nope"))
    }

    @Test(expected = IllegalArgumentException::class) fun wrongDimensionIsRejected() {
        small().addExisting(SpaceEntry("x", MediaType.TEXT, FloatArray(7)))
    }

    @Test fun centredVectorsAreUnitLength() {
        val s = small()
        for (i in 0 until s.size) assertEquals(1f, Vectors.norm(s.centredOf(i)), 1e-4f)
    }

    @Test fun centredCacheRefreshesWhenMeansChange() {
        val s = small()
        val before = s.centredOf(0).copyOf()
        s.addExisting(SpaceEntry("far", MediaType.TEXT, Vectors.normalized(floatArrayOf(-1f, 0f, -1f, 1f))))
        val after = s.centredOf(0)
        assertTrue(before.indices.any { kotlin.math.abs(before[it] - after[it]) > 1e-4f })
    }

    @Test fun nearestFindsTheSameGroupFirst() {
        val s = small()
        val hits = s.nearestTo(0, 2)
        assertTrue(hits.all { s.entries[it.index].id.startsWith("a") })
        assertTrue(hits[0].sim >= hits[1].sim)
    }

    @Test fun nearestExcludesTheItemItself() {
        val s = small()
        assertTrue(s.nearestTo(2, 5).none { it.index == 2 })
    }

    @Test fun nearestCanFilterByType() {
        val s = small()
        s.addExisting(SpaceEntry("img", MediaType.IMAGE, Vectors.normalized(floatArrayOf(1f, 0.1f, 0f, 0.2f))))
        val hits = s.nearestTo(0, 3, MediaType.IMAGE)
        assertEquals(1, hits.size)
        assertEquals("img", s.entries[hits[0].index].id)
    }

    @Test fun neighboursByOtherTypeSkipsOwnType() {
        val s = small()
        s.addExisting(SpaceEntry("img", MediaType.IMAGE, Vectors.normalized(floatArrayOf(1f, 0f, 0f, 0.2f))))
        val m = s.neighborsByOtherType(0, 3)
        assertFalse(MediaType.TEXT in m.keys)
        assertTrue(MediaType.IMAGE in m.keys)
    }

    @Test fun removeDropsTheEntryAndUpdatesMeans() {
        val s = small()
        val n = s.means.count(MediaType.TEXT)
        assertTrue(s.remove("a1"))
        assertEquals(n - 1, s.means.count(MediaType.TEXT))
        assertFalse(s.remove("a1"))
        assertEquals(5, s.size)
    }

    @Test fun positionsRoundTrip() {
        val s = small()
        val p = s.positions()
        p[0] = 9f
        s.setPositions(p)
        assertEquals(9f, s.entries[0].x, 0f)
    }

    @Test fun clusterIdsNeverRepeat() {
        val s = small()
        val a = s.nextClusterId()
        val b = s.nextClusterId()
        assertTrue(a >= 2)
        assertEquals(a + 1, b)
    }

    @Test fun similarItemJoinsTheClusterAndLandsNearItsNeighbours() {
        val s = small()
        val p = s.insert("new", MediaType.TEXT, Vectors.normalized(floatArrayOf(1f, 0.1f, 0f, 0.02f)))
        assertFalse(p.startedNewCluster)
        assertEquals(0, p.clusterId)
        assertTrue("x=${p.x}", p.x < -4f)
        assertEquals(7, s.size)
    }

    @Test fun unrelatedItemStartsANewIslandAwayFromTheOthers() {
        val s = small()
        val p = s.insert("odd", MediaType.TEXT, Vectors.normalized(floatArrayOf(-1f, -1f, -1f, 3f)))
        assertTrue(p.startedNewCluster)
        assertTrue(p.clusterId >= 2)
        val nearest = s.entries.filter { it.id != "odd" }.minOf { e ->
            val dx = e.x - p.x; val dy = e.y - p.y; val dz = e.z - p.z
            sqrt(dx * dx + dy * dy + dz * dz)
        }
        assertTrue("distance to nearest point $nearest", nearest > 0.2f)
    }

    @Test fun audioPlacementsAreTentative() {
        val s = small()
        val p = s.insert("snd", MediaType.AUDIO, Vectors.normalized(floatArrayOf(1f, 0.1f, 0f, 0f)))
        assertTrue(p.tentative)
        assertTrue(s.entries.last().tentative)
    }

    @Test fun textPlacementsAreNotTentative() {
        val s = small()
        assertFalse(s.place(MediaType.TEXT, floatArrayOf(1f, 0f, 0f, 0f)).tentative)
    }

    @Test fun placeOnEmptySpaceStartsACluster() {
        val p = Space(4).place(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f, 0f))
        assertTrue(p.startedNewCluster)
        assertEquals(0f, p.x, 0f)
    }

    @Test fun placeDoesNotAddTheItem() {
        val s = small()
        s.place(MediaType.TEXT, floatArrayOf(1f, 0f, 0f, 0f))
        assertEquals(6, s.size)
    }

    @Test fun meansRecomputeEveryNAdds() {
        val s = Space(4, SpaceConfig(meansRecomputeEvery = 3))
        repeat(7) { s.addExisting(SpaceEntry("t$it", MediaType.TEXT, Vectors.normalized(floatArrayOf(1f, it * 0.1f, 0f, 0f)))) }
        val live = s.means.mean(MediaType.TEXT)!!
        s.recomputeMeans()
        val clean = s.means.mean(MediaType.TEXT)!!
        for (i in live.indices) assertEquals(clean[i], live[i], 1e-4f)
    }

    @Test fun medianNearestDistanceIsPositive() {
        assertTrue(small().medianNearestDistance() > 0f)
        assertEquals(1f, Space(4).medianNearestDistance(), 0f)
    }
}

/** The placement rule and the join threshold, checked on the spike's real vectors. */
class PlacementVsSpikeTest {
    private val k = 5

    /** Held-out items placed by the kNN rule into a layout fitted without them (train-only centring), like the spike's 5-fold test. */
    private fun outOfSample(): Triple<Double, Double, Double> {
        val n = TestData.manifest.items.size
        val folds = 5
        val order = (0 until n).sortedBy { (it * 2654435761L % 1000).toInt() }
        var placed = 0.0; var oracle = 0.0; var count = 0
        for (f in 0 until folds) {
            val test = order.filterIndexed { i, _ -> i % folds == f }.toSet()
            val train = (0 until n).filter { it !in test }
            val space = Space(256)
            for (i in train) space.addExisting(SpaceEntry(TestData.manifest.items[i].id, TestData.types[i], TestData.vectors[i]))
            val pos = UmapLite(UmapParams(orient = false)).fit(space.centredAll())
            space.setPositions(pos)
            val trainConcepts = train.map { TestData.concepts[it] }
            for (i in test) {
                val p = space.place(TestData.types[i], TestData.vectors[i])
                val d = FloatArray(space.size) { j ->
                    val e = space.entries[j]
                    val dx = e.x - p.x; val dy = e.y - p.y; val dz = e.z - p.z
                    dx * dx + dy * dy + dz * dz
                }
                val near = (0 until space.size).sortedBy { d[it] }.take(k)
                placed += near.count { trainConcepts[it] == TestData.concepts[i] }.toDouble() / k
                val c = space.means.centre(TestData.types[i], TestData.vectors[i])
                val hi = space.nearest(c, k)
                oracle += hi.count { trainConcepts[it.index] == TestData.concepts[i] }.toDouble() / k
                count++
            }
        }
        return Triple(placed / count, oracle / count, count.toDouble())
    }

    @Test fun placementMatchesTheHighDimensionalUpperBound() {
        val (placed, oracle, _) = outOfSample()
        println("placement same-concept share %.3f, 5-NN in 256-d %.3f (spike: 0.636 vs 0.649)".format(placed, oracle))
        assertTrue("placed $placed vs oracle $oracle", placed >= oracle - 0.15)
        assertTrue("placed $placed", placed >= 0.40)
    }

    @Test fun placementWeightsFavourTheClosestNeighbour() {
        val w = listOf(0.60f, 0.50f, 0.30f).map { exp(((it - 0.60f) / 0.05f).toDouble()) }
        assertTrue(w[0] > w[1] && w[1] > w[2])
        assertTrue(w[2] < 0.01)
    }

    /** Items whose concept is present should join, items whose concept was removed should start a new island. */
    @Test fun joinRuleSeparatesKnownFromNewConcepts() {
        val n = TestData.manifest.items.size
        var joinKnown = 0; var knownTotal = 0; var newOk = 0; var newTotal = 0
        for (i in 0 until n) {
            val type = TestData.types[i]
            if (type == MediaType.AUDIO || type == MediaType.VIDEO) continue
            val concept = TestData.concepts[i]
            val sameConcept = (0 until n).filter { it != i && TestData.concepts[it] == concept }
            if (sameConcept.isEmpty()) continue
            fun build(excluded: Set<Int>): Space {
                val s = Space(256)
                TestData.manifest.items.forEachIndexed { j, it ->
                    if (j !in excluded) s.addExisting(SpaceEntry(it.id, TestData.types[j], TestData.vectors[j], Origin.DEMO, it.x, it.y, it.z, it.cluster))
                }
                return s
            }
            val known = build(setOf(i)).place(type, TestData.vectors[i])
            knownTotal++; if (!known.startedNewCluster) joinKnown++
            val fresh = build(sameConcept.toSet() + i).place(type, TestData.vectors[i])
            newTotal++; if (fresh.startedNewCluster) newOk++
        }
        println("known joined %d/%d, new island %d/%d".format(joinKnown, knownTotal, newOk, newTotal))
        assertTrue("known ${joinKnown.toDouble() / knownTotal}", joinKnown.toDouble() / knownTotal >= 0.75)
        assertTrue("new ${newOk.toDouble() / newTotal}", newOk.toDouble() / newTotal >= 0.6)
    }

    @Test fun insertAssignsStableClusterOfTheClosestConcept() {
        val s = TestData.space()
        val i = TestData.manifest.items.indexOfFirst { it.id == "demo-img-train_0" }
        val old = s.entries[i].clusterId
        val raw = s.entries[i].raw
        s.remove("demo-img-train_0")
        val p = s.insert("again", MediaType.IMAGE, raw)
        assertFalse(p.startedNewCluster)
        assertEquals(old, p.clusterId)
        assertNotEquals(-1, p.clusterId)
    }
}

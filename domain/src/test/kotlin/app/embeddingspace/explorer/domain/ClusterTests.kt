package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.cluster.ClusterColors
import app.embeddingspace.explorer.domain.cluster.ClusterInfo
import app.embeddingspace.explorer.domain.cluster.ClusterNamer
import app.embeddingspace.explorer.domain.cluster.ClusterSearch
import app.embeddingspace.explorer.domain.cluster.ClusterStabiliser
import app.embeddingspace.explorer.domain.cluster.KMeans
import app.embeddingspace.explorer.domain.cluster.Vocabulary
import app.embeddingspace.explorer.domain.io.FloatMatrix
import app.embeddingspace.explorer.domain.layout.LayoutMetrics
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.space.MapPipeline
import app.embeddingspace.explorer.domain.vec.Vectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KMeansTest {
    @Test fun recoversWellSeparatedBlobs() {
        val (x, y) = TestData.blobs(4, 15)
        val r = KMeans.run(x, 4)
        assertEquals(1.0, LayoutMetrics.purity(r.labels, y.map { it.toString() }), 1e-9)
    }

    @Test fun everyPointGetsALabelInRange() {
        val (x, _) = TestData.blobs(3, 10)
        val r = KMeans.run(x, 5)
        assertTrue(r.labels.all { it in 0 until 5 })
        assertEquals(5, r.centroids.size)
    }

    @Test fun centroidsAreUnitLength() {
        val (x, _) = TestData.blobs(3, 10)
        for (c in KMeans.run(x, 3).centroids) assertEquals(1f, Vectors.norm(c), 1e-4f)
    }

    @Test fun kIsClampedToTheNumberOfPoints() {
        val (x, _) = TestData.blobs(1, 4)
        assertEquals(4, KMeans.run(x, 50).centroids.size)
    }

    @Test fun sameSeedGivesTheSameResult() {
        val (x, _) = TestData.blobs(4, 10)
        assertTrue(KMeans.run(x, 4, 3).labels.contentEquals(KMeans.run(x, 4, 3).labels))
    }

    @Test fun duplicatePointsDoNotBreakEmptyClusterHandling() {
        val v = TestData.unit(8, 0)
        val x = Array(6) { v.copyOf() }
        val r = KMeans.run(x, 3)
        assertEquals(6, r.labels.size)
    }
}

class ClusterSearchTest {
    @Test fun silhouetteIsHighForSeparatedBlobs() {
        val (x, y) = TestData.blobs(4, 12)
        val s = ClusterSearch.silhouette(ClusterSearch.similarityMatrix(x), y)
        assertTrue("silhouette $s", s > 0.4f)
    }

    @Test fun silhouetteIsLowForShuffledLabels() {
        val (x, y) = TestData.blobs(4, 12)
        val shuffled = IntArray(y.size) { (it * 7) % 4 }
        val good = ClusterSearch.silhouette(ClusterSearch.similarityMatrix(x), y)
        val bad = ClusterSearch.silhouette(ClusterSearch.similarityMatrix(x), shuffled)
        assertTrue(bad < good - 0.2f)
    }

    @Test fun silhouetteOfOneClusterIsZero() {
        val (x, _) = TestData.blobs(2, 5)
        assertEquals(0f, ClusterSearch.silhouette(ClusterSearch.similarityMatrix(x), IntArray(x.size)), 0f)
    }

    @Test fun smallMapsTryEveryK() {
        assertEquals((2..20).toList(), ClusterSearch.candidates(100, 20))
    }

    @Test fun bigMapsTryASpreadOfK() {
        val c = ClusterSearch.candidates(2000, 60)
        assertTrue(c.size < 30 && c.first() == 2 && c.last() == 60)
    }

    @Test fun bestFindsRoughlyTheRightNumberOfClusters() {
        val (x, _) = TestData.blobs(6, 14, noise = 0.2f)
        val out = ClusterSearch.best(x, maxK = 20)
        assertTrue("k=${out.k}", out.k in 4..9)
    }

    @Test fun veryFewPointsStillCluster() {
        val (x, _) = TestData.blobs(1, 3)
        assertEquals(3, ClusterSearch.best(x).labels.size)
    }

    @Test fun singletonsAreMergedIntoTheClosestCluster() {
        val (x, _) = TestData.blobs(3, 6)
        val labels = IntArray(x.size) { if (it == 0) 3 else it / 6 }
        val cents = Array(4) { c -> Vectors.normalizeInPlace(Vectors.mean(x.indices.filter { labels[it] == c }.map { x[it] })) }
        val merged = ClusterSearch.mergeSingletons(x, labels, cents)
        assertEquals(3, merged.toSet().size)
        assertEquals(merged[1], merged[0])
    }

    @Test fun demoClustersAreMostlyPure() {
        val s = TestData.space()
        val p = LayoutMetrics.purity(IntArray(s.size) { s.entries[it].clusterId }, TestData.concepts)
        assertTrue("purity $p", p >= 0.8)
    }
}

class StabiliserTest {
    @Test fun matchingClustersKeepTheirOldIds() {
        val old = intArrayOf(7, 7, 7, 9, 9, 9)
        val now = intArrayOf(1, 1, 1, 0, 0, 0)
        val ids = ClusterStabiliser.match(old, now) { 100 }
        assertEquals(listOf(7, 7, 7, 9, 9, 9), ids.toList())
    }

    @Test fun anAbsorbedClusterGetsAFreshId() {
        val old = intArrayOf(7, 7, 7, 9, 9, 9)
        val now = intArrayOf(0, 0, 0, 0, 1, 1)
        var next = 100
        val ids = ClusterStabiliser.match(old, now) { next++ }
        assertEquals(7, ids[0])
        assertTrue(ids[5] != 7)
    }

    @Test fun newItemsWithoutAnIdFormAFreshCluster() {
        val old = intArrayOf(-1, -1, -1)
        val ids = ClusterStabiliser.match(old, intArrayOf(0, 0, 1)) { 50 }
        assertEquals(3, ids.size)
    }

    @Test fun twoNewClustersNeverShareAnOldId() {
        val old = intArrayOf(4, 4, 4, 4)
        var next = 10
        val ids = ClusterStabiliser.match(old, intArrayOf(0, 0, 1, 1)) { next++ }
        assertNotEquals(ids[0], ids[3])
    }
}

class ClusterColorsTest {
    private val centres = mapOf(1 to floatArrayOf(0f, 0f, 0f), 2 to floatArrayOf(1f, 0f, 0f), 3 to floatArrayOf(2f, 0f, 0f), 4 to floatArrayOf(50f, 0f, 0f))

    @Test fun existingColoursAreKept() {
        val out = ClusterColors.assign(centres, mapOf(1 to 5), 16)
        assertEquals(5, out[1])
    }

    @Test fun neighboursGetDifferentColours() {
        val out = ClusterColors.assign(centres, emptyMap(), 16)
        assertTrue(out[1] != out[2] && out[2] != out[3] && out[1] != out[3])
    }

    @Test fun everyClusterGetsAColourInRange() {
        val out = ClusterColors.assign(centres, emptyMap(), 6)
        assertEquals(4, out.size)
        assertTrue(out.values.all { it in 0 until 6 })
    }

    @Test fun staleColoursOutsideThePaletteAreReplaced() {
        val out = ClusterColors.assign(centres, mapOf(1 to 99), 16)
        assertTrue(out[1] in 0 until 16)
    }
}

class MapPipelineTest {
    @Test fun describeNamesEveryClusterAndPicksColours() {
        val s = TestData.space()
        val namer = ClusterNamer(TestData.vocab, s.means.mean(MediaType.TEXT))
        val infos = MapPipeline.describe(s, namer, emptyMap())
        assertEquals(s.entries.map { it.clusterId }.toSet(), infos.keys)
        assertTrue(infos.values.all { it.name.isNotBlank() && it.colorIndex in 0 until 16 })
    }

    @Test fun describeKeepsExistingNamesUnlessAskedToRename() {
        val s = TestData.space()
        val namer = ClusterNamer(TestData.vocab, s.means.mean(MediaType.TEXT))
        val first = MapPipeline.describe(s, namer, emptyMap())
        val id = first.keys.first()
        val prev = first.toMutableMap().also { it[id] = it.getValue(id).copy(name = "Mine") }
        assertEquals("Mine", MapPipeline.describe(s, namer, prev)[id]!!.name)
        assertNotEquals("Mine", MapPipeline.describe(s, namer, prev, rename = setOf(id))[id]!!.name)
    }

    @Test fun reclusterKeepsMostIdsStable() {
        val s = TestData.space()
        val namer = ClusterNamer(TestData.vocab, s.means.mean(MediaType.TEXT))
        val before = s.entries.map { it.clusterId }.toSet()
        val infos = MapPipeline.recluster(s, namer, MapPipeline.describe(s, namer, emptyMap()))
        val kept = infos.keys.count { it in before }
        assertTrue("kept $kept of ${infos.size}", kept >= infos.size / 2)
    }

    @Test fun centresAreTheMeanOfTheMembers() {
        val s = TestData.space()
        val c = MapPipeline.centres(s)
        val id = s.entries[0].clusterId
        val members = s.entries.filter { it.clusterId == id }
        assertEquals(members.map { it.x }.average().toFloat(), c.getValue(id)[0], 1e-4f)
    }

    @Test fun emptySpaceHasNoClusters() {
        assertTrue(MapPipeline.recluster(app.embeddingspace.explorer.domain.space.Space(8), null, emptyMap()).isEmpty())
    }
}

class NamingTest {
    private val synonyms = mapOf(
        "beach_waves" to setOf("beach", "wave", "waves", "sea", "ocean", "shore", "surf", "coast"),
        "rain" to setOf("rain", "rainfall", "raindrops", "drizzle", "wet", "storm"),
        "city_traffic" to setOf("traffic", "car", "cars", "city", "street", "road", "vehicle", "urban"),
        "dog" to setOf("dog", "puppy", "pet", "canine", "bark"),
        "birdsong" to setOf("bird", "birds", "birdsong", "songbird", "singing", "chirping"),
        "forest" to setOf("forest", "woods", "trees", "woodland", "nature", "jungle"),
        "campfire" to setOf("campfire", "fire", "bonfire", "flames", "camping", "fireplace"),
        "train" to setOf("train", "railway", "locomotive", "railroad", "rail", "station"),
        "typing_keyboard" to setOf("keyboard", "typing", "computer", "keys", "typewriter"),
        "cooking_kitchen" to setOf("cooking", "kitchen", "chef", "food", "frying", "meal", "cook"),
        "thunderstorm" to setOf("thunderstorm", "thunder", "lightning", "storm"),
        "crowd_cheering" to setOf("crowd", "cheering", "applause", "audience", "stadium", "fans", "cheer"),
        "piano" to setOf("piano", "keyboard", "music", "pianist"),
    )

    private val space = TestData.space()
    private val namer = ClusterNamer(TestData.vocab, space.means.mean(MediaType.TEXT))

    private fun members(concept: String, types: Set<MediaType>) =
        space.entries.indices.filter { TestData.concepts[it] == concept && space.entries[it].type in types }
            .map { space.entries[it].type to space.centredOf(it) }

    @Test fun vocabularyHasThreeHundredWordsAndMatchingVectors() {
        assertEquals(301, TestData.vocab.size)
        assertEquals(256, TestData.vocab.vectors[0].size)
    }

    @Test fun centredVocabularyIsUnitLength() {
        for (v in TestData.vocab.centred(space.means.mean(MediaType.TEXT))) assertEquals(1f, Vectors.norm(v), 1e-3f)
    }

    @Test fun mixedClustersNameCorrectlyMostOfTheTime() {
        var hit = 0
        for ((concept, ok) in synonyms) {
            val m = members(concept, setOf(MediaType.IMAGE, MediaType.AUDIO, MediaType.TEXT))
            val top = namer.rank(namer.centroid(m), 1)[0].first
            if (top in ok) hit++
        }
        println("mixed centroid top-1: $hit/13 (spike 0.92)")
        assertTrue("hit $hit", hit >= 10)
    }

    @Test fun imageClustersNameCorrectlyMostOfTheTime() {
        var hit = 0
        for ((concept, ok) in synonyms) {
            val m = members(concept, setOf(MediaType.IMAGE))
            if (m.isEmpty()) continue
            if (namer.rank(namer.centroid(m), 1)[0].first in ok) hit++
        }
        println("image centroid top-1: $hit/13 (spike 0.92)")
        assertTrue("hit $hit", hit >= 9)
    }

    @Test fun audioOnlyClustersAreWeakerButTopThreeOftenHit() {
        var top3 = 0
        var total = 0
        for ((concept, ok) in synonyms) {
            val m = members(concept, setOf(MediaType.AUDIO))
            if (m.isEmpty()) continue
            total++
            if (namer.rank(namer.centroid(m), 3).any { it.first in ok }) top3++
        }
        println("audio centroid top-3: $top3/$total (spike 0.77)")
        assertTrue("top3 $top3 of $total", top3.toDouble() / total >= 0.5)
    }

    @Test fun audioOnlyClustersAreMarkedAsAGuessWithThreeWords() {
        val r = namer.name(members("dog", setOf(MediaType.AUDIO)))
        assertTrue(r.guess)
        assertEquals(3, r.label.split(" / ").size)
    }

    @Test fun imageClustersAreNotAGuessAndHaveOneWord() {
        val r = namer.name(members("train", setOf(MediaType.IMAGE, MediaType.TEXT)))
        assertFalse(r.guess)
        assertFalse(r.label.contains("/"))
        assertTrue(r.label.first().isUpperCase())
    }

    @Test fun aTakenNameGetsAnotherLabel() {
        val m = members("train", setOf(MediaType.IMAGE, MediaType.TEXT))
        val first = namer.name(m)
        val second = namer.name(m, setOf(first.label))
        assertNotEquals(first.label, second.label)
    }

    @Test fun lowConfidenceBecomesAGuess() {
        val strict = ClusterNamer(TestData.vocab, space.means.mean(MediaType.TEXT), minConfidence = 5f)
        assertTrue(strict.name(members("train", setOf(MediaType.IMAGE))).guess)
    }

    @Test fun vocabularyParseRejectsMismatchedSizes() {
        val bin = FloatMatrix.write(arrayOf(FloatArray(4) { 1f }))
        try {
            Vocabulary.parse("a b", bin, 4)
            org.junit.Assert.fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("words"))
        }
    }

    @Test fun clusterInfoCopiesKeepWords() {
        val c = ClusterInfo(1, "A", false, 2, listOf("a", "b"))
        assertEquals(listOf("a", "b"), c.copy(name = "B").words)
    }
}

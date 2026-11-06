package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.layout.LayoutMetrics
import app.embeddingspace.explorer.domain.layout.LayoutShape
import app.embeddingspace.explorer.domain.layout.Pca
import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.layout.UmapParams
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.vec.Vectors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class UmapLiteTest {
    @Test fun curveFitMatchesTheKnownValuesForMinDistPointOne() {
        val (a, b) = UmapLite(UmapParams(minDist = 0.1f)).fitAB()
        assertEquals(1.577f, a, 0.06f)
        assertEquals(0.895f, b, 0.03f)
    }

    @Test fun biggerMinDistGivesASmallerA() {
        val a1 = UmapLite(UmapParams(minDist = 0.1f)).fitAB().first
        val a2 = UmapLite(UmapParams(minDist = 0.5f)).fitAB().first
        assertTrue(a2 < a1)
    }

    @Test fun knnReturnsSortedNeighboursWithoutSelf() {
        val (x, _) = TestData.blobs(3, 8)
        val knn = UmapLite().knn(x, 5)
        for (i in x.indices) {
            assertEquals(5, knn.index[i].size)
            assertFalse(i in knn.index[i])
            for (p in 1 until 5) assertTrue(knn.dist[i][p] >= knn.dist[i][p - 1] - 1e-6f)
        }
    }

    @Test fun knnClampsKToTheNumberOfPoints() {
        val (x, _) = TestData.blobs(1, 4)
        assertEquals(3, UmapLite().knn(x, 10).index[0].size)
    }

    @Test fun smoothKnnMembershipsSumToLog2K() {
        val (x, _) = TestData.blobs(3, 12)
        val u = UmapLite()
        val knn = u.knn(x, 8)
        val (rho, sigma) = u.smoothKnnDist(knn.dist, 8)
        for (i in x.indices) {
            var s = 0.0
            for (d in knn.dist[i]) s += if (d - rho[i] > 0) kotlin.math.exp(-(d - rho[i]) / sigma[i].toDouble()) else 1.0
            assertEquals(ln(8.0) / ln(2.0), s, 0.01)
        }
    }

    @Test fun fuzzyGraphWeightsAreProbabilitiesWithoutDuplicates() {
        val (x, _) = TestData.blobs(3, 10)
        val g = UmapLite().fuzzyGraph(UmapLite().knn(x, 6))
        assertTrue(g.size > 0)
        val seen = HashSet<Pair<Int, Int>>()
        for (e in 0 until g.size) {
            assertTrue(g.weight[e] > 0f && g.weight[e] <= 1.0001f)
            assertTrue(seen.add(minOf(g.head[e], g.tail[e]) to maxOf(g.head[e], g.tail[e])))
        }
    }

    @Test fun fitGivesThreeNumbersPerPoint() {
        val (x, _) = TestData.blobs(3, 10)
        assertEquals(x.size * 3, UmapLite().fit(x).size)
    }

    @Test fun fitIsDeterministic() {
        val (x, _) = TestData.blobs(3, 10)
        assertArrayEquals(UmapLite().fit(x), UmapLite().fit(x), 0f)
    }

    @Test fun anotherSeedGivesAnotherLayout() {
        val (x, _) = TestData.blobs(3, 10)
        assertFalse(UmapLite(UmapParams(seed = 1)).fit(x).contentEquals(UmapLite(UmapParams(seed = 2)).fit(x)))
    }

    @Test fun tinyInputsDoNotCrash() {
        val (x, _) = TestData.blobs(1, 6)
        assertEquals(0, UmapLite().fit(emptyArray()).size)
        assertEquals(3, UmapLite().fit(arrayOf(x[0])).size)
        assertEquals(6, UmapLite().fit(arrayOf(x[0], x[1])).size)
        assertEquals(9, UmapLite().fit(arrayOf(x[0], x[1], x[2])).size)
    }

    @Test fun layoutIsCentredOnZeroWithoutOrienting() {
        val (x, _) = TestData.blobs(3, 10)
        val p = UmapLite(UmapParams(orient = false)).fit(x)
        var sx = 0f
        for (i in 0 until x.size) sx += p[i * 3]
        assertEquals(0f, sx / x.size, 1e-3f)
    }

    @Test fun separatedBlobsStayTogetherInThreeD() {
        val (x, y) = TestData.blobs(4, 12)
        val p = UmapLite().fit(x)
        val share = LayoutMetrics.knnSameLabel(p, y.map { it.toString() })
        assertTrue("share $share", share >= 0.9)
    }

    @Test fun progressIsReportedAndCanCancel() {
        val (x, _) = TestData.blobs(3, 12)
        var calls = 0
        UmapLite().fit(x) { calls++; true }
        assertTrue(calls > 2)
        var stopped = 0
        UmapLite().fit(x) { stopped++; stopped < 2 }
        assertTrue(stopped <= 3)
    }

    @Test fun seededRefitKeepsTheShape() {
        val (x, _) = TestData.blobs(4, 12)
        val first = UmapLite().fit(x)
        val again = UmapLite().fit(x, init = first)
        val moved = LayoutMetrics.meanDisplacement(first, again)
        assertTrue("moved $moved of radius ${LayoutMetrics.meanRadius(first)}", moved < LayoutMetrics.meanRadius(first) * 0.6f)
    }

    @Test fun refitWithTheWrongSizedSeedFallsBackToPca() {
        val (x, _) = TestData.blobs(3, 8)
        assertEquals(x.size * 3, UmapLite().fit(x, init = FloatArray(5)).size)
    }

    /** The key validation: same-concept share among the 5 nearest layout neighbours, spike numbers 0.76 (UMAP) and 0.54 (PCA). */
    @Test fun layoutQualityMatchesThePythonSpike() {
        val idx = TestData.spikeSubset()
        val space = Space(256)
        for (i in idx) space.addExisting(SpaceEntry(TestData.manifest.items[i].id, TestData.types[i], TestData.vectors[i]))
        val x = space.centredAll()
        val labels = idx.map { TestData.concepts[it] }
        val umap = LayoutMetrics.knnSameLabel(UmapLite(UmapParams(orient = false)).fit(x), labels)
        val pca = LayoutMetrics.knnSameLabel(Pca.project(x, 3), labels)
        println("same-concept share of 5 layout neighbours: UMAP-lite %.3f (spike UMAP 0.760), PCA %.3f (spike PCA 0.537), n=%d".format(umap, pca, idx.size))
        assertTrue("umap $umap", umap >= 0.76 - 0.08)
        assertTrue("umap $umap vs pca $pca", umap >= pca + 0.08)
    }

    @Test fun layoutQualityHoldsOnTheFullDemoSetToo() {
        val space = TestData.space()
        val p = UmapLite().fit(space.centredAll())
        val share = LayoutMetrics.knnSameLabel(p, TestData.concepts)
        assertTrue("share $share", share >= 0.65)
    }

    @Test fun shippedDemoLayoutIsGood() {
        val s = TestData.space()
        assertTrue(LayoutMetrics.knnSameLabel(s.positions(), TestData.concepts) >= 0.65)
    }

    @Test fun layoutOfFifteenHundredPointsIsFastEnough() {
        val (x, _) = TestData.blobs(30, 50, dim = 64, noise = 0.35f, seed = 11)
        val t0 = System.nanoTime()
        val p = UmapLite().fit(x)
        val ms = (System.nanoTime() - t0) / 1e6
        println("UMAP-lite on %d points: %.0f ms (JVM, one thread)".format(x.size, ms))
        assertEquals(x.size * 3, p.size)
        assertTrue("took $ms ms", ms < 60_000)
    }
}

class PcaTest {
    private fun line(n: Int): Array<FloatArray> = Array(n) { i -> floatArrayOf(i * 1f, i * 0.2f + (i % 3) * 0.01f, (i % 5) * 0.05f) }

    @Test fun firstAxisCapturesTheLongSpread() {
        val p = Pca.project(line(30), 3)
        fun varOf(c: Int): Float {
            val m = (0 until 30).map { p[it * 3 + c] }.average().toFloat()
            return (0 until 30).map { (p[it * 3 + c] - m) * (p[it * 3 + c] - m) }.sum()
        }
        assertTrue(varOf(0) > varOf(1) * 5)
        assertTrue(varOf(1) >= varOf(2) - 1e-3f)
    }

    @Test fun projectionIsDeterministic() = assertArrayEquals(Pca.project(line(20)), Pca.project(line(20)), 0f)

    @Test fun emptyInputGivesEmptyOutput() = assertEquals(0, Pca.project(emptyArray()).size)

    @Test fun scaleToMaxSetsTheLargestCoordinate() {
        val p = Pca.scaleToMax(floatArrayOf(1f, -4f, 2f), 10f)
        assertEquals(10f, p.maxOf { abs(it) }, 1e-5f)
    }

    @Test fun componentsAreUncorrelated() {
        val p = Pca.project(TestData.blobs(3, 10).first, 3)
        val n = p.size / 3
        var dot = 0f
        for (i in 0 until n) dot += p[i * 3] * p[i * 3 + 1]
        assertEquals(0f, dot / n, 0.05f)
    }
}

class LayoutShapeTest {
    @Test fun eigenOfADiagonalMatrixIsItsDiagonalSorted() {
        val (vals, _) = LayoutShape.eigen3(arrayOf(doubleArrayOf(2.0, 0.0, 0.0), doubleArrayOf(0.0, 5.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0)))
        assertArrayEquals(doubleArrayOf(5.0, 2.0, 1.0), vals, 1e-9)
    }

    @Test fun eigenvectorsSatisfyAv_equals_lambdaV() {
        val m = arrayOf(doubleArrayOf(4.0, 1.0, 0.5), doubleArrayOf(1.0, 3.0, 0.2), doubleArrayOf(0.5, 0.2, 1.0))
        val (vals, vecs) = LayoutShape.eigen3(m)
        for (c in 0 until 3) {
            for (r in 0 until 3) {
                var av = 0.0
                for (k in 0 until 3) av += m[r][k] * vecs[c][k]
                assertEquals(vals[c] * vecs[c][r], av, 1e-8)
            }
        }
    }

    @Test fun eigenvectorsAreOrthonormal() {
        val (_, v) = LayoutShape.eigen3(arrayOf(doubleArrayOf(4.0, 1.0, 0.5), doubleArrayOf(1.0, 3.0, 0.2), doubleArrayOf(0.5, 0.2, 1.0)))
        for (a in 0 until 3) for (b in 0 until 3) {
            var d = 0.0
            for (k in 0 until 3) d += v[a][k] * v[b][k]
            assertEquals(if (a == b) 1.0 else 0.0, d, 1e-8)
        }
    }

    private fun cloud(): FloatArray {
        val r = UmapLite.Rng(3)
        return FloatArray(300) { i -> (r.nextFloat() - 0.5f) * (if (i % 3 == 0) 10f else if (i % 3 == 1) 4f else 1f) + 100f }
    }

    @Test fun orientPutsTheLongestSpreadOnY() {
        val o = LayoutShape.orient(cloud(), equalise = 0f)
        fun sd(c: Int): Float {
            val n = o.size / 3
            var m = 0f
            for (i in 0 until n) m += o[i * 3 + c]
            m /= n
            var s = 0f
            for (i in 0 until n) s += (o[i * 3 + c] - m) * (o[i * 3 + c] - m)
            return sqrt(s / n)
        }
        assertTrue(sd(1) > sd(0) && sd(0) > sd(2))
    }

    @Test fun orientWithoutEqualisingPreservesDistances() {
        val p = cloud()
        val o = LayoutShape.orient(p, equalise = 0f, portraitStretch = 1f)
        fun d(a: FloatArray, i: Int, j: Int): Float {
            var s = 0f
            for (k in 0 until 3) s += (a[i * 3 + k] - a[j * 3 + k]).let { it * it }
            return sqrt(s)
        }
        for ((i, j) in listOf(0 to 1, 5 to 17, 30 to 80)) assertEquals(d(p, i, j), d(o, i, j), 1e-3f)
    }

    @Test fun equalisingMakesTheSpreadsCloser() {
        fun ratio(a: FloatArray): Float {
            val n = a.size / 3
            val s = FloatArray(3)
            for (c in 0 until 3) {
                var m = 0f
                for (i in 0 until n) m += a[i * 3 + c]
                m /= n
                for (i in 0 until n) s[c] += (a[i * 3 + c] - m) * (a[i * 3 + c] - m)
            }
            return sqrt(s.max() / s.min())
        }
        val raw = LayoutShape.orient(cloud(), 0f, 1f)
        val even = LayoutShape.orient(cloud(), 0.6f, 1f)
        assertTrue(ratio(even) < ratio(raw))
    }

    @Test fun portraitStretchWidensTheVerticalSpread() {
        fun spreadY(a: FloatArray) = (0 until a.size / 3).map { kotlin.math.abs(a[it * 3 + 1]) }.average()
        assertTrue(spreadY(LayoutShape.orient(cloud(), 0.6f, 1.5f)) > spreadY(LayoutShape.orient(cloud(), 0.6f, 1f)) * 1.4)
    }

    @Test fun orientCentresTheCloud() {
        val o = LayoutShape.orient(cloud())
        for (c in 0 until 3) {
            var m = 0f
            for (i in 0 until o.size / 3) m += o[i * 3 + c]
            assertEquals(0f, m / (o.size / 3), 1e-3f)
        }
    }

    @Test fun tinyLayoutsAreReturnedAsCopies() {
        val p = floatArrayOf(1f, 2f, 3f)
        val o = LayoutShape.orient(p)
        assertArrayEquals(p, o, 0f)
        assertNotEquals(System.identityHashCode(p), System.identityHashCode(o))
    }
}

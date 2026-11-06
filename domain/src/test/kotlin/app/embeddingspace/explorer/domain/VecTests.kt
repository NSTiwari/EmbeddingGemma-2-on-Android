package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.vec.ModalityMeans
import app.embeddingspace.explorer.domain.vec.Vectors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VectorsTest {
    @Test fun dotOfOrthogonalIsZero() = assertEquals(0f, Vectors.dot(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0f)

    @Test fun normOfThreeFour() = assertEquals(5f, Vectors.norm(floatArrayOf(3f, 4f)), 1e-6f)

    @Test fun normalizeGivesUnitLength() {
        val v = Vectors.normalized(floatArrayOf(3f, 4f, 12f))
        assertEquals(1f, Vectors.norm(v), 1e-6f)
    }

    @Test fun normalizeKeepsZeroVectorZero() {
        val v = Vectors.normalized(FloatArray(4))
        assertTrue(v.all { it == 0f })
    }

    @Test fun cosineOfSameDirectionIsOne() = assertEquals(1f, Vectors.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-6f)

    @Test fun cosineOfOppositeIsMinusOne() = assertEquals(-1f, Vectors.cosine(floatArrayOf(1f, 2f), floatArrayOf(-1f, -2f)), 1e-6f)

    @Test fun cosineWithZeroVectorIsZero() = assertEquals(0f, Vectors.cosine(FloatArray(3), floatArrayOf(1f, 0f, 0f)), 0f)

    @Test fun truncateKeepsPrefixAndRenormalises() {
        val full = Vectors.normalized(floatArrayOf(1f, 1f, 1f, 1f))
        val t = Vectors.truncate(full, 2)
        assertEquals(2, t.size)
        assertEquals(1f, Vectors.norm(t), 1e-6f)
        assertEquals(t[0], t[1], 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class) fun truncateRejectsTooBigDim() {
        Vectors.truncate(floatArrayOf(1f, 2f), 5)
    }

    @Test fun truncatedDemoVectorsAreStillUnit() {
        for (v in TestData.vectors) assertEquals(1f, Vectors.norm(v), 1e-4f)
    }

    @Test fun meanOfRows() {
        val m = Vectors.mean(listOf(floatArrayOf(1f, 3f), floatArrayOf(3f, 5f)))
        assertArrayEquals(floatArrayOf(2f, 4f), m, 1e-6f)
    }

    @Test fun topKOrdersBestFirst() {
        val idx = Vectors.topK(floatArrayOf(0.1f, 0.9f, 0.5f, 0.7f), 3)
        assertArrayEquals(intArrayOf(1, 3, 2), idx)
    }

    @Test fun topKSkipsGivenIndex() {
        val idx = Vectors.topK(floatArrayOf(0.1f, 0.9f, 0.5f), 2, skip = 1)
        assertArrayEquals(intArrayOf(2, 0), idx)
    }

    @Test fun topKWithKLargerThanSizeReturnsAll() {
        assertEquals(3, Vectors.topK(floatArrayOf(1f, 2f, 3f), 10).size)
    }

    @Test fun distanceIsEuclidean() = assertEquals(5f, Vectors.distance(floatArrayOf(0f, 0f), floatArrayOf(3f, 4f)), 1e-6f)
}

class ModalityMeansTest {
    private fun means() = ModalityMeans(3, minCount = 2)

    @Test fun emptyMeanIsNull() = assertNull(means().mean(MediaType.IMAGE))

    @Test fun runningMeanOfTwo() {
        val m = means()
        m.add(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f))
        m.add(MediaType.IMAGE, floatArrayOf(0f, 1f, 0f))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0f), m.mean(MediaType.IMAGE)!!, 1e-6f)
        assertEquals(2, m.count(MediaType.IMAGE))
    }

    @Test fun modalitiesAreSeparate() {
        val m = means()
        m.add(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f))
        m.add(MediaType.AUDIO, floatArrayOf(0f, 0f, 1f))
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), m.mean(MediaType.AUDIO)!!, 1e-6f)
    }

    @Test fun videoSharesTheImageMean() {
        val m = means()
        m.add(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f))
        m.add(MediaType.VIDEO, floatArrayOf(0f, 1f, 0f))
        assertEquals(2, m.count(MediaType.IMAGE))
        assertArrayEquals(m.mean(MediaType.IMAGE)!!, m.mean(MediaType.VIDEO)!!, 0f)
    }

    @Test fun removeUndoesAdd() {
        val m = means()
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        m.add(MediaType.TEXT, a); m.add(MediaType.TEXT, b)
        m.remove(MediaType.TEXT, b)
        assertArrayEquals(a, m.mean(MediaType.TEXT)!!, 1e-6f)
    }

    @Test fun removingLastItemClearsTheMean() {
        val m = means()
        m.add(MediaType.TEXT, floatArrayOf(1f, 0f, 0f))
        m.remove(MediaType.TEXT, floatArrayOf(1f, 0f, 0f))
        assertNull(m.mean(MediaType.TEXT))
    }

    @Test fun versionBumpsOnEveryChange() {
        val m = means()
        val v0 = m.version
        m.add(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f))
        assertTrue(m.version > v0)
    }

    @Test fun recomputeMatchesRunningMean() {
        val m = means()
        val rows = listOf(floatArrayOf(1f, 2f, 3f), floatArrayOf(3f, 2f, 1f), floatArrayOf(0f, 1f, 5f))
        rows.forEach { m.add(MediaType.IMAGE, it) }
        val before = m.mean(MediaType.IMAGE)!!
        m.recompute(rows.map { MediaType.IMAGE to it })
        assertArrayEquals(before, m.mean(MediaType.IMAGE)!!, 1e-5f)
    }

    @Test fun centreSubtractsTheMeanAndNormalises() {
        val m = means()
        m.add(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f))
        m.add(MediaType.IMAGE, floatArrayOf(0f, 1f, 0f))
        val c = m.centre(MediaType.IMAGE, floatArrayOf(1f, 0f, 0f))
        assertEquals(1f, Vectors.norm(c), 1e-5f)
        assertTrue(c[0] > 0 && c[1] < 0)
    }

    @Test fun centreWithTooFewItemsOnlyNormalises() {
        val m = means()
        m.add(MediaType.IMAGE, floatArrayOf(2f, 0f, 0f))
        val c = m.centre(MediaType.IMAGE, floatArrayOf(2f, 0f, 0f))
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), c, 1e-6f)
    }

    @Test fun centredVectorEqualToMeanDoesNotCollapse() {
        val m = means()
        m.add(MediaType.TEXT, floatArrayOf(1f, 0f, 0f))
        m.add(MediaType.TEXT, floatArrayOf(1f, 0f, 0f))
        val c = m.centre(MediaType.TEXT, floatArrayOf(1f, 0f, 0f))
        assertEquals(1f, Vectors.norm(c), 1e-5f)
    }

    /** The modality gap: raw vectors of one type sit together, centring flips that around. */
    @Test fun centringRemovesTheModalityGapOnDemoData() {
        val types = TestData.types
        val m = ModalityMeans(256)
        TestData.vectors.forEachIndexed { i, v -> m.add(types[i], v) }
        val img = types.indices.filter { types[it] == MediaType.IMAGE }
        val aud = types.indices.filter { types[it] == MediaType.AUDIO }
        fun meanCross(f: (Int) -> FloatArray): Double {
            var s = 0.0; var n = 0
            for (i in img) for (j in aud) { s += Vectors.dot(f(i), f(j)); n++ }
            return s / n
        }
        fun meanWithin(list: List<Int>, f: (Int) -> FloatArray): Double {
            var s = 0.0; var n = 0
            for (a in list) for (b in list) if (a != b) { s += Vectors.dot(f(a), f(b)); n++ }
            return s / n
        }
        // after centring, the cross-modal similarity is no longer far below the within-modal one relative to the scale
        val rawCross = meanCross { TestData.vectors[it] }
        val cenCross = meanCross { m.centre(types[it], TestData.vectors[it]) }
        assertTrue("raw cross $rawCross should be high, centred cross $cenCross near zero", rawCross > 0.5 && abs(cenCross) < 0.15)
    }
}

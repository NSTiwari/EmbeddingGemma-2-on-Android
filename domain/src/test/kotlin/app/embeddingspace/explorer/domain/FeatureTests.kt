package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.camera.Projection
import app.embeddingspace.explorer.domain.camera.Camera3D
import app.embeddingspace.explorer.domain.insights.Insights
import app.embeddingspace.explorer.domain.io.DemoManifest
import app.embeddingspace.explorer.domain.io.FloatMatrix
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.SpaceEntry
import app.embeddingspace.explorer.domain.queue.Job
import app.embeddingspace.explorer.domain.queue.JobKind
import app.embeddingspace.explorer.domain.queue.QueueLogic
import app.embeddingspace.explorer.domain.queue.Stage
import app.embeddingspace.explorer.domain.search.SearchRank
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.vec.Vectors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    @Test fun crossLinksMarkAudioAsLikely() {
        val s = TestData.space()
        val i = s.indexOf("demo-aud-birdsong_0")
        val links = Insights.crossLinks(s, i, 3)
        assertTrue(links.isNotEmpty())
        assertTrue(links.all { it.likely })
        assertTrue(links.none { it.type == MediaType.AUDIO })
    }

    @Test fun crossLinksOfAnImageToTextAreNotMarkedLikely() {
        val s = TestData.space()
        val links = Insights.crossLinks(s, s.indexOf("demo-img-train_0"), 3)
        assertTrue(links.filter { it.type == MediaType.TEXT }.all { !it.likely })
    }

    @Test fun demoImageLinksToItsOwnConceptNote() {
        val s = TestData.space()
        val i = s.indexOf("demo-img-train_0")
        val best = Insights.crossLinks(s, i, 3).first { it.type == MediaType.TEXT }
        assertEquals("train", TestData.concepts[best.index])
    }

    @Test fun linksAreLimitedPerType() {
        val s = TestData.space()
        val links = Insights.crossLinks(s, s.indexOf("demo-img-train_0"), 2)
        assertTrue(links.groupBy { it.type }.values.all { it.size <= 2 })
    }
}

class QueueLogicTest {
    private fun job(id: String, at: Long, kind: JobKind = JobKind.PHOTO, stage: Stage = Stage.QUEUED) = Job(id, kind, "src", id, stage, createdAt = at)

    @Test fun enqueueIgnoresTheSameIdTwice() {
        val l = QueueLogic.enqueue(QueueLogic.enqueue(emptyList(), job("a", 1)), job("a", 2))
        assertEquals(1, l.size)
    }

    @Test fun nextIsTheOldestWaitingJob() {
        val l = listOf(job("b", 5), job("a", 1), job("c", 3, stage = Stage.EMBED))
        assertEquals("a", QueueLogic.next(l)!!.id)
    }

    @Test fun nextIsNullWhenNothingWaits() {
        assertNull(QueueLogic.next(listOf(job("a", 1, stage = Stage.DONE), job("b", 2, stage = Stage.FAILED))))
    }

    @Test fun advanceSetsTheStageAndClearsTheError() {
        val l = QueueLogic.advance(QueueLogic.fail(listOf(job("a", 1)), "a", "boom"), "a", Stage.EMBED)
        assertEquals(Stage.EMBED, l[0].stage)
        assertNull(l[0].error)
    }

    @Test fun failCountsAttempts() {
        val l = QueueLogic.fail(listOf(job("a", 1)), "a", "boom")
        assertEquals(1, l[0].attempts)
        assertEquals("boom", l[0].error)
    }

    @Test fun retryPutsAFailedJobBackInLine() {
        val l = QueueLogic.retry(QueueLogic.fail(listOf(job("a", 1)), "a", "x"), "a")
        assertEquals(Stage.QUEUED, l[0].stage)
    }

    @Test fun retryStopsAfterTheAttemptLimit() {
        var l = listOf(job("a", 1))
        repeat(QueueLogic.MAX_ATTEMPTS) { l = QueueLogic.retry(QueueLogic.fail(l, "a", "x"), "a") }
        assertEquals(Stage.FAILED, l[0].stage)
    }

    @Test fun restoreAfterRestartRewindsRunningJobsOnly() {
        val l = QueueLogic.restoreAfterRestart(listOf(job("a", 1, stage = Stage.EMBED), job("b", 2, stage = Stage.FAILED), job("c", 3, stage = Stage.PLACE)))
        assertEquals(listOf(Stage.QUEUED, Stage.FAILED, Stage.QUEUED), l.map { it.stage })
    }

    @Test fun progressRisesWithTheStages() {
        val p = listOf(Stage.QUEUED, Stage.PREPARE, Stage.EMBED, Stage.PLACE, Stage.DONE).map { Job("x", JobKind.NOTE, "", stage = it).progress }
        assertEquals(p.sorted(), p)
        assertEquals(1f, p.last(), 0f)
    }

    @Test fun overallProgressAveragesTheJobs() {
        assertEquals(0.5f, QueueLogic.overallProgress(listOf(job("a", 1, stage = Stage.DONE), job("b", 2))), 1e-6f)
        assertEquals(1f, QueueLogic.overallProgress(emptyList()), 0f)
    }

    @Test fun pendingCountIgnoresFinishedJobs() {
        assertEquals(1, QueueLogic.pendingCount(listOf(job("a", 1), job("b", 2, stage = Stage.DONE))))
    }

    @Test fun clearFinishedDropsDoneJobs() {
        assertEquals(listOf("b"), QueueLogic.clearFinished(listOf(job("a", 1, stage = Stage.DONE), job("b", 2))).map { it.id })
    }

    @Test fun onlyVideoIsFlaggedSlow() {
        assertTrue(JobKind.VIDEO.slow)
        assertFalse(JobKind.PHOTO.slow || JobKind.NOTE.slow || JobKind.AUDIO_FILE.slow)
    }

    @Test fun keysRoundTrip() {
        for (k in JobKind.entries) assertEquals(k, JobKind.fromKey(k.key))
        for (s in Stage.entries) assertEquals(s, Stage.fromKey(s.key))
    }
}

class IoTest {
    @Test fun floatMatrixRoundTrips() {
        val rows = arrayOf(floatArrayOf(1f, -2.5f, 3f), floatArrayOf(0f, 0.125f, 9f))
        val back = FloatMatrix.read(FloatMatrix.write(rows), 3)
        assertEquals(2, back.size)
        assertArrayEquals(rows[1], back[1], 0f)
    }

    @Test fun blobRoundTrips() {
        val v = floatArrayOf(0.5f, 0.25f, -1f, 2f)
        assertArrayEquals(v, FloatMatrix.fromBlob(FloatMatrix.toBlob(v)), 0f)
    }

    @Test(expected = IllegalArgumentException::class) fun partialRowsAreRejected() {
        FloatMatrix.read(ByteArray(10), 4)
    }

    @Test fun demoManifestHasEveryItemAndAMatchingVector() {
        assertEquals(97, TestData.manifest.items.size)
        assertEquals(TestData.manifest.items.size, TestData.vectors.size)
    }

    @Test fun demoManifestHasTheExpectedMix() {
        val c = TestData.manifest.items.groupingBy { it.type }.eachCount()
        assertEquals(25, c["image"]); assertEquals(25, c["audio"]); assertEquals(2, c["video"]); assertEquals(45, c["text"])
    }

    @Test fun everyMediaItemHasACreditAndLicence() {
        for (i in TestData.manifest.items.filter { it.type != "text" }) {
            assertTrue(i.id, i.license.isNotBlank() && i.licenseUrl.isNotBlank() && i.media != null)
        }
    }

    @Test fun everyDemoClusterIdIsDefined() {
        val ids = TestData.manifest.clusters.map { it.id }.toSet()
        assertTrue(TestData.manifest.items.all { it.cluster in ids })
    }

    @Test fun manifestEncodeThenParseKeepsTheData() {
        val m = DemoManifest.parse(DemoManifest.encode(TestData.manifest))
        assertEquals(TestData.manifest.items.size, m.items.size)
        assertEquals(TestData.manifest.clusters.first().name, m.clusters.first().name)
    }

    @Test fun parserIgnoresUnknownFields() {
        val m = DemoManifest.parse("""{"dim":4,"extra":1,"items":[{"id":"a","type":"text","title":"t","future":true}]}""")
        assertEquals("a", m.items[0].id)
    }

    @Test fun unknownMediaTypeIsAnError() {
        try { MediaType.fromKey("hologram"); org.junit.Assert.fail() } catch (e: IllegalStateException) { assertNotNull(e.message) }
    }
}

class SearchRankTest {
    private val space = TestData.space()

    private fun searchWord(w: String) = SearchRank.search(space, TestData.vocab.vectors[TestData.vocab.words.indexOf(w)])

    @Test fun beachFindsBeachItems() {
        val r = searchWord("beach")
        val top = r.hits.take(5).map { TestData.concepts[it.index] }
        assertTrue(top.toString(), "beach_waves" in top)
    }

    @Test fun trainFindsTheTrainPhotoAmongTheTopHits() {
        val r = searchWord("train")
        assertTrue(r.hits.take(6).any { space.entries[it.index].id == "demo-img-train_0" || space.entries[it.index].id == "demo-img-train_1" })
    }

    @Test fun matchedItemsGlowAndOthersDoNot() {
        val r = searchWord("piano")
        assertTrue(r.glow.count { it > 0f } in 3..24)
        assertEquals(1f, r.glow.max(), 1e-6f)
        assertTrue(r.glow.any { it == 0f })
    }

    @Test fun hitsAreRankedByScore() {
        val r = searchWord("dog")
        for (i in 1 until r.hits.size) assertTrue(r.hits[i].score <= r.hits[i - 1].score)
    }

    @Test fun anEmptySpaceGivesNoHits() {
        val r = SearchRank.search(Space(256), FloatArray(256) { 0.1f })
        assertTrue(r.hits.isEmpty())
        assertNull(r.best)
    }
}

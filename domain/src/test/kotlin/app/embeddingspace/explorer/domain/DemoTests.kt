package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.space.DemoPolicy
import app.embeddingspace.explorer.domain.space.Space
import app.embeddingspace.explorer.domain.vec.Vectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPolicyTest {
    @Test fun aFreshInstallSeedsTheDemo() = assertTrue(DemoPolicy.shouldSeed(seededFlag = false, storedItems = 0))

    @Test fun neverSeedsAgainOnceTheFlagIsSet() {
        assertFalse(DemoPolicy.shouldSeed(seededFlag = true, storedItems = 0))
        assertFalse(DemoPolicy.shouldSeed(seededFlag = true, storedItems = 50))
    }

    @Test fun anEmptyMapAfterDeletingEverythingStaysEmpty() {
        assertFalse(DemoPolicy.shouldSeed(true, 0))
    }

    @Test fun anOlderInstallWithItemsIsMarkedAsSeeded() {
        assertTrue(DemoPolicy.shouldMarkSeeded(false, 97))
        assertFalse(DemoPolicy.shouldMarkSeeded(false, 0))
        assertFalse(DemoPolicy.shouldMarkSeeded(true, 97))
    }

    @Test fun anOlderInstallIsNeverSeededAgain() {
        assertFalse(DemoPolicy.shouldSeed(false, 97))
    }

    @Test fun theHintShowsOnlyWhileDemoItemsExistAndNotDismissed() {
        assertTrue(DemoPolicy.hintVisible(97, false))
        assertFalse(DemoPolicy.hintVisible(97, true))
        assertFalse(DemoPolicy.hintVisible(0, false))
    }
}

class DeleteDemoTest {
    private fun mixed(): Space {
        val s = TestData.space()
        val at = { id: String -> TestData.vectors[TestData.manifest.items.indexOfFirst { it.id == id }] }
        s.insert("mine-1", MediaType.TEXT, at("demo-txt-train-0"), Origin.USER)
        s.insert("mine-2", MediaType.IMAGE, at("demo-img-train_0"), Origin.USER)
        return s
    }

    @Test fun deletingTheDemoKeepsEveryUserItem() {
        val s = mixed()
        assertEquals(97, s.removeOrigin(Origin.DEMO))
        assertEquals(listOf("mine-1", "mine-2"), s.entries.map { it.id })
        assertEquals(0, s.countOrigin(Origin.DEMO))
    }

    @Test fun deletingWhatTheUserAddedKeepsTheDemo() {
        val s = mixed()
        assertEquals(2, s.removeOrigin(Origin.USER))
        assertEquals(97, s.size)
    }

    @Test fun deletingEverythingLeavesAnEmptySpace() {
        val s = mixed()
        s.removeOrigin(Origin.DEMO); s.removeOrigin(Origin.USER)
        assertEquals(0, s.size)
        assertEquals(0, s.means.count(MediaType.TEXT))
    }

    @Test fun meansAreRebuiltFromTheItemsThatRemain() {
        val s = mixed()
        s.removeOrigin(Origin.DEMO)
        val mean = s.means.mean(MediaType.TEXT)!!
        val only = s.entries.first { it.type == MediaType.TEXT }.raw
        for (i in mean.indices) assertEquals(only[i], mean[i], 1e-5f)
    }

    @Test fun removingAnOriginThatIsNotThereDoesNothing() {
        val s = TestData.space()
        assertEquals(0, s.removeOrigin(Origin.USER))
        assertEquals(97, s.size)
    }

    @Test fun aNewItemCanStillBePlacedAfterTheDemoIsGone() {
        val s = mixed()
        s.removeOrigin(Origin.DEMO)
        s.insert("again", MediaType.TEXT, Vectors.normalized(FloatArray(256) { (it % 7) - 3f }), Origin.USER)
        assertEquals(3, s.size)
    }

    @Test fun aSingleItemCanBeDeletedAndTheRestStays() {
        val s = TestData.space()
        assertTrue(s.remove("demo-img-train_0"))
        assertEquals(96, s.size)
    }
}

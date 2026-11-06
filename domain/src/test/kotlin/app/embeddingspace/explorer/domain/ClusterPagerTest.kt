package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.domain.space.ClusterPager
import app.embeddingspace.explorer.domain.space.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterPagerTest {
    private val space = TestData.space()

    private fun idx(id: String) = space.indexOf(id)

    @Test fun theTappedItemIsFirst() {
        val i = idx("demo-img-train_0")
        assertEquals(i, ClusterPager.order(space, i).first())
    }

    @Test fun everyPageIsInTheSameCluster() {
        val i = idx("demo-img-train_0")
        val cluster = space.entries[i].clusterId
        assertTrue(ClusterPager.order(space, i).all { space.entries[it].clusterId == cluster })
    }

    @Test fun theRestFollowsFromMostToLeastSimilar() {
        val i = idx("demo-img-train_0")
        val order = ClusterPager.order(space, i)
        val sims = order.drop(1).map { space.similarity(i, it) }
        assertEquals(sims.sortedDescending(), sims)
    }

    @Test fun everyMemberOfTheClusterAppearsOnce() {
        val i = idx("demo-img-train_0")
        val cluster = space.entries[i].clusterId
        val order = ClusterPager.order(space, i)
        assertEquals(space.entries.count { it.clusterId == cluster }, order.size)
        assertEquals(order.size, order.toSet().size)
    }

    @Test fun aClusterOfOneItemHasOnePageAndNoChrome() {
        val s = Space(256)
        s.insert("only", MediaType.TEXT, TestData.vectors[0], Origin.USER)
        val order = ClusterPager.order(s, 0)
        assertEquals(listOf(0), order)
        assertNull(ClusterPager.position(0, order.size))
    }

    @Test fun pagesAreCapped() {
        val i = idx("demo-img-train_0")
        assertTrue(ClusterPager.order(space, i, maxPages = 3).size <= 3)
    }

    @Test fun positionTextIsOneBased() {
        assertEquals("3 / 12", ClusterPager.position(2, 12))
        assertEquals("1 / 2", ClusterPager.position(0, 2))
    }

    @Test fun deletingTheMiddlePageShowsTheNextOne() {
        assertEquals(2, ClusterPager.indexAfterDelete(5, 2))
    }

    @Test fun deletingTheLastPageShowsThePreviousOne() {
        assertEquals(3, ClusterPager.indexAfterDelete(5, 4))
    }

    @Test fun deletingTheFirstPageStaysAtTheStart() {
        assertEquals(0, ClusterPager.indexAfterDelete(5, 0))
    }

    @Test fun deletingTheOnlyPageEmptiesThePager() {
        assertNull(ClusterPager.indexAfterDelete(1, 0))
    }

    @Test fun deletingFromTwoPagesLeavesOne() {
        assertEquals(0, ClusterPager.indexAfterDelete(2, 1))
        assertEquals(0, ClusterPager.indexAfterDelete(2, 0))
    }
}

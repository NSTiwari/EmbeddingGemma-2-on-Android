package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.search.TilesLogic
import app.embeddingspace.explorer.domain.search.TilesResult
import app.embeddingspace.explorer.domain.space.JoinThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {
    private val space = TestData.space()
    private fun word(w: String) = TestData.vocab.vectors[TestData.vocab.words.indexOf(w)]

    @Test fun aKnownWordPicksTheClusterOfItsTopMatch() {
        val r = TilesLogic.forQuery(space, word("train")) as TilesResult.Match
        val trainCluster = space.entries[space.indexOf("demo-img-train_0")].clusterId
        assertEquals(trainCluster, r.clusterId)
    }

    @Test fun onlyMembersOfThatClusterAreShown() {
        val r = TilesLogic.forQuery(space, word("piano")) as TilesResult.Match
        assertTrue(r.items.all { space.entries[it].clusterId == r.clusterId })
        assertEquals(space.entries.count { it.clusterId == r.clusterId }, r.items.size)
    }

    @Test fun membersAreRankedByScoreInsideTheCluster() {
        val q = word("dog")
        val r = TilesLogic.forQuery(space, q) as TilesResult.Match
        val s = TilesLogic.scores(space, q)
        val zs = r.items.map { s.z[it] }
        assertEquals(zs.sortedDescending(), zs)
    }

    @Test fun theBestMatchComesFirstWhenItIsInTheCluster() {
        val q = word("beach")
        val r = TilesLogic.forQuery(space, q) as TilesResult.Match
        val s = TilesLogic.scores(space, q)
        val bestInCluster = r.items.maxByOrNull { s.z[it] }!!
        assertEquals(bestInCluster, r.items.first())
    }

    @Test fun differentWordsCanGiveDifferentClusters() {
        val a = (TilesLogic.forQuery(space, word("train")) as TilesResult.Match).clusterId
        val b = (TilesLogic.forQuery(space, word("piano")) as TilesResult.Match).clusterId
        assertTrue(a != b)
    }

    @Test fun aQueryThatClearsNoBarGivesNoMatch() {
        val strict = JoinThresholds(imageLike = 1.5f, textText = 1.5f, audio = 1.5f)
        assertEquals(TilesResult.NoMatch, TilesLogic.forQuery(space, word("train"), strict))
    }

    @Test fun anEmptySpaceGivesNoMatch() {
        assertEquals(TilesResult.NoMatch, TilesLogic.forQuery(app.embeddingspace.explorer.domain.space.Space(256), word("train")))
    }

    @Test fun theBarsAreTheSameOnesUsedForJoiningAClusterWithTextAgainstText() {
        val t = JoinThresholds()
        assertEquals(0.45f, t.forPair(MediaType.TEXT, MediaType.TEXT), 0f)
        assertEquals(0.30f, t.forPair(MediaType.TEXT, MediaType.IMAGE), 0f)
        assertEquals(0.20f, t.forPair(MediaType.TEXT, MediaType.AUDIO), 0f)
    }

    @Test fun withoutAQueryEveryItemShowsUpOnceInSomeGroup() {
        val g = TilesLogic.groups(space)
        val all = g.groups.flatMap { it.items }
        assertEquals(space.size, all.size)
        assertEquals(space.size, all.toSet().size)
    }

    @Test fun groupsFollowTheClusters() {
        for (g in TilesLogic.groups(space).groups) assertTrue(g.items.all { space.entries[it].clusterId == g.clusterId })
    }

    @Test fun theBiggestClusterComesFirst() {
        val sizes = TilesLogic.groups(space).groups.map { it.items.size }
        assertEquals(sizes.sortedDescending(), sizes)
    }

    @Test fun picturesComeBeforeSoundsAndNotesInsideAGroup() {
        for (g in TilesLogic.groups(ShippedDemo.space()).groups) {
            val ranks = g.items.map { i ->
                when (ShippedDemo.space().entries[i].type) { MediaType.IMAGE, MediaType.VIDEO -> 0; MediaType.TEXT -> 1; MediaType.AUDIO -> 2 }
            }
            assertEquals(ranks.sorted(), ranks)
        }
    }

    @Test fun theShippedDemoGroupsIntoItsSixClusters() {
        assertEquals(6, TilesLogic.groups(ShippedDemo.space()).groups.size)
    }

    @Test fun twoColumnsOnAPhoneThreeOnAWiderScreen() {
        assertEquals(2, TilesLogic.columns(360))
        assertEquals(2, TilesLogic.columns(479))
        assertEquals(3, TilesLogic.columns(600))
    }
}

package io.github.manxisuo.spriglauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingEngineTest {
    private val day = 86_400_000L
    private fun entry(id: String, pkg: String = id, label: String = id) =
        LaunchableEntry(id, pkg, id, 0, label)

    @Test fun halfLifeIsSevenDays() {
        val now = 20 * day + day / 2
        val points = listOf(UsagePoint("app", 13, 1))
        assertEquals(0.5, RankingEngine.score(points, now), 0.000001)
    }

    @Test fun pinnedExcludedAndDuplicatePackagesAreRemoved() {
        val entries = listOf(entry("pinned"), entry("hidden"), entry("a1", "same", "A"), entry("a2", "same", "B"), entry("free"))
        val result = RankingEngine.rank(RankingInput(entries, listOf("pinned"), setOf("hidden"), emptyList(), emptyList(), day))
        assertEquals(listOf("a1", "free"), result.map { it.entry.id })
    }

    @Test fun previousOrderWinsScoreTie() {
        val entries = listOf(entry("a"), entry("b"))
        val result = RankingEngine.rank(RankingInput(entries, emptyList(), emptySet(), emptyList(), listOf("b", "a"), day))
        assertEquals(listOf("b", "a"), result.map { it.entry.id })
    }

    @Test fun higherScoreWinsAndWindowInputIsDeterministic() {
        val entries = listOf(entry("a"), entry("b"))
        val usage = listOf(UsagePoint("b", 9, 2), UsagePoint("a", 9, 1))
        val result = RankingEngine.rank(RankingInput(entries, emptyList(), emptySet(), usage, emptyList(), 10 * day + day / 2))
        assertEquals("b", result.first().entry.id)
        assertTrue(result.first().score > result.last().score)
    }
}

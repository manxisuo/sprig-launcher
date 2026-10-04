package io.github.manxisuo.spriglauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExtractorTest {
    private fun event(at: Long, pkg: String, type: Int) = RawUsageEvent(at, pkg, type, "$at|$pkg|$type")

    @Test fun activitySwitchInsideSamePackageCountsOnce() {
        val events = listOf(
            event(1_000, "a", SessionExtractor.TYPE_RESUMED),
            event(2_000, "a", SessionExtractor.TYPE_PAUSED),
            event(2_100, "a", SessionExtractor.TYPE_RESUMED),
        )
        val result = SessionExtractor.extract(events, SessionParserState(), setOf("a"), "self")
        assertEquals(listOf("a" to 1_000L), result.sessions)
    }

    @Test fun crossPackageStartsDistinctSessions() {
        val result = SessionExtractor.extract(
            listOf(event(1_000, "a", 1), event(5_000, "b", 1)),
            SessionParserState(), setOf("a", "b"), "self",
        )
        assertEquals(listOf("a" to 1_000L, "b" to 5_000L), result.sessions)
    }

    @Test fun validCrossPackageReturnStartsANewSession() {
        val result = SessionExtractor.extract(
            listOf(event(1_000, "a", 1), event(5_000, "b", 1), event(20_000, "a", 1)),
            SessionParserState(), setOf("a", "b"), "self",
        )
        assertEquals(listOf("a" to 1_000L, "b" to 5_000L, "a" to 20_000L), result.sessions)
    }

    @Test fun stateCarriesAcrossCollectionBatches() {
        val first = SessionExtractor.extract(
            listOf(event(1_000, "a", 1), event(2_000, "a", SessionExtractor.TYPE_PAUSED)),
            SessionParserState(), setOf("a"), "self",
        )
        val second = SessionExtractor.extract(listOf(event(20_000, "a", 1)), first.state, setOf("a"), "self")
        assertEquals(1, first.sessions.size)
        assertEquals(0, second.sessions.size)
    }

    @Test fun ownAndNonLaunchablePackagesAreIgnored() {
        val result = SessionExtractor.extract(
            listOf(event(1_000, "self", 1), event(2_000, "system", 1)),
            SessionParserState(), emptySet(), "self",
        )
        assertEquals(emptyList<Pair<String, Long>>(), result.sessions)
    }
}

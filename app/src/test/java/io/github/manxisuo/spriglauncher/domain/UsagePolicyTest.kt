package io.github.manxisuo.spriglauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UsagePolicyTest {
    @Test fun sourceSwitchNeverAddsLauncherAndSystemCounts() {
        assertEquals(UsageSource.LAUNCHER, UsagePolicy.activeSource(false))
        assertEquals(UsageSource.SYSTEM, UsagePolicy.activeSource(true))
    }

    @Test fun clearBaselinePreventsImportingOlderEvents() {
        val start = UsagePolicy.importStart(now = 1_000_000, cursorAt = 900_000, clearedAt = 950_000, windowMs = 500_000, overlapMs = 120_000)
        assertEquals(950_000, start)
    }

    @Test fun clockRollbackNeverCreatesAnInvertedQuery() {
        val start = UsagePolicy.importStart(now = 500_000, cursorAt = 900_000, clearedAt = 0, windowMs = 500_000, overlapMs = 120_000)
        assertEquals(500_000, start)
    }

    @Test fun snapshotIsStableWithinLocalDayAndRepublishedOnSourceChange() {
        val zone = ZoneId.of("Asia/Shanghai")
        val morning = 1_728_028_800_000L
        assertFalse(UsagePolicy.shouldPublishSnapshot(morning, UsageSource.LAUNCHER, UsageSource.LAUNCHER, morning + 3_600_000, zone))
        assertTrue(UsagePolicy.shouldPublishSnapshot(morning, UsageSource.LAUNCHER, UsageSource.SYSTEM, morning + 3_600_000, zone))
        assertTrue(UsagePolicy.shouldPublishSnapshot(morning, UsageSource.LAUNCHER, UsageSource.LAUNCHER, morning + 86_400_000, zone))
    }
}

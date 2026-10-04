package io.github.manxisuo.spriglauncher.domain

import java.time.Instant
import java.time.ZoneId

object UsagePolicy {
    fun activeSource(hasSystemAccess: Boolean): UsageSource =
        if (hasSystemAccess) UsageSource.SYSTEM else UsageSource.LAUNCHER

    fun importStart(
        now: Long,
        cursorAt: Long,
        clearedAt: Long,
        windowMs: Long,
        overlapMs: Long,
    ): Long {
        val floor = maxOf(clearedAt, now - windowMs)
        val overlapStart = (cursorAt - overlapMs).coerceAtMost(now)
        return maxOf(floor, overlapStart)
    }

    fun shouldPublishSnapshot(
        snapshotAt: Long?,
        snapshotSource: UsageSource?,
        activeSource: UsageSource,
        now: Long,
        zoneId: ZoneId,
    ): Boolean {
        if (snapshotAt == null || snapshotSource != activeSource) return true
        val snapshotDay = Instant.ofEpochMilli(snapshotAt).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return snapshotDay != today
    }
}

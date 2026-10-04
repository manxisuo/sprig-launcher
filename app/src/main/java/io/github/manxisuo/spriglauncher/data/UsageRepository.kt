package io.github.manxisuo.spriglauncher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.room.withTransaction
import io.github.manxisuo.spriglauncher.domain.RawUsageEvent
import io.github.manxisuo.spriglauncher.domain.RankingEngine
import io.github.manxisuo.spriglauncher.domain.SessionExtractor
import io.github.manxisuo.spriglauncher.domain.SessionParserState
import io.github.manxisuo.spriglauncher.domain.UsageSource
import io.github.manxisuo.spriglauncher.domain.UsagePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset

class UsageRepository(private val context: Context, private val database: AppDatabase) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)
    val usage: Flow<List<UsageDailyBucketEntity>> = database.dao().observeUsage()

    suspend fun recordLauncherRequest(packageName: String, profile: Long, at: Long = System.currentTimeMillis()) {
        database.withTransaction { increment(profile, packageName, UsageSource.LAUNCHER, at) }
    }

    suspend fun importSystemEvents(allowedPackages: Set<String>, now: Long = System.currentTimeMillis()): ImportResult =
        withContext(Dispatchers.IO) {
            val windowMs = RankingEngine.WINDOW_DAYS * DAY_MS
            database.withTransaction {
                val dao = database.dao()
                val old = dao.checkpoint() ?: UsageCheckpointEntity()
                val floor = maxOf(old.clearedAt, now - windowMs)
                val start = UsagePolicy.importStart(now, old.cursorAt, old.clearedAt, windowMs, OVERLAP_MS)
                val events = mutableListOf<RawUsageEvent>()
                val stream = manager.queryEvents(start, now)
                val value = UsageEvents.Event()
                while (stream.hasNextEvent()) {
                    stream.getNextEvent(value)
                    val mapped = when (value.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> SessionExtractor.TYPE_RESUMED
                        UsageEvents.Event.ACTIVITY_PAUSED -> SessionExtractor.TYPE_PAUSED
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> SessionExtractor.TYPE_SCREEN_OFF
                        else -> null
                    }
                    if (mapped != null && value.timeStamp >= floor) {
                        val id = "${value.timeStamp}|${value.packageName}|${value.className}|$mapped"
                        if (dao.markProcessed(ProcessedUsageEventEntity(id, value.timeStamp)) != -1L) {
                            events += RawUsageEvent(value.timeStamp, value.packageName ?: "", mapped, id)
                        }
                    }
                }
                val parsed = SessionExtractor.extract(
                    events,
                    SessionParserState(old.foregroundPackage, old.lastSessionPackage, old.lastSessionAt),
                    allowedPackages,
                    context.packageName,
                )
                parsed.sessions.forEach { (pkg, at) -> increment(0, pkg, UsageSource.SYSTEM, at) }
                val coverage = old.coverageStartAt.takeIf { it > 0 } ?: start
                dao.saveCheckpoint(
                    old.copy(
                        cursorAt = now,
                        foregroundPackage = parsed.state.foregroundPackage,
                        lastSessionPackage = parsed.state.lastSessionPackage,
                        lastSessionAt = parsed.state.lastSessionAt,
                        coverageStartAt = coverage,
                    )
                )
                dao.pruneUsage(utcDay(now - windowMs))
                dao.pruneProcessed(now - DETAIL_RETENTION_MS)
                ImportResult(parsed.sessions.size, coverage, events.isNotEmpty())
            }
        }

    suspend fun clearLearning(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val dao = database.dao()
        dao.clearUsage()
        dao.clearProcessed()
        dao.deleteRanking()
        dao.saveCheckpoint(UsageCheckpointEntity(cursorAt = now, coverageStartAt = now, clearedAt = now))
    }

    suspend fun checkpoint(): UsageCheckpointEntity? = database.dao().checkpoint()

    private suspend fun increment(profile: Long, packageName: String, source: UsageSource, at: Long) {
        val dao = database.dao()
        val day = utcDay(at)
        val old = dao.bucket(profile, packageName, source.name, day)
        dao.saveBucket(
            old?.copy(count = old.count + 1, lastUsedAt = maxOf(old.lastUsedAt, at))
                ?: UsageDailyBucketEntity(profile, packageName, source.name, day, 1, at)
        )
    }

    data class ImportResult(val sessionsAdded: Int, val coverageStartAt: Long, val hadEvents: Boolean)

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val OVERLAP_MS = 120_000L
        private const val DETAIL_RETENTION_MS = 3 * DAY_MS
        fun utcDay(at: Long): Long = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    }
}

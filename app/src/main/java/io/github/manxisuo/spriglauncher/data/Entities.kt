package io.github.manxisuo.spriglauncher.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "app_preferences", primaryKeys = ["entryId"])
data class AppPreferenceEntity(
    val entryId: String,
    val packageName: String,
    val pinnedOrder: Int? = null,
    val excluded: Boolean = false,
)

@Entity(tableName = "usage_daily", primaryKeys = ["profile", "packageName", "source", "utcDay"])
data class UsageDailyBucketEntity(
    val profile: Long,
    val packageName: String,
    val source: String,
    val utcDay: Long,
    val count: Int,
    val lastUsedAt: Long,
)

@Entity(tableName = "usage_checkpoint", primaryKeys = ["id"])
data class UsageCheckpointEntity(
    val id: Int = 1,
    val cursorAt: Long = 0,
    val foregroundPackage: String? = null,
    val lastSessionPackage: String? = null,
    val lastSessionAt: Long = 0,
    val coverageStartAt: Long = 0,
    val clearedAt: Long = 0,
)

@Entity(tableName = "ranking_snapshot", primaryKeys = ["entryId"])
data class RankingSnapshotEntity(val entryId: String, val rank: Int, val updatedAt: Long, val source: String)

@Entity(tableName = "processed_usage_events", indices = [Index("timestamp")], primaryKeys = ["stableId"])
data class ProcessedUsageEventEntity(val stableId: String, val timestamp: Long)

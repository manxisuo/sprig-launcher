package io.github.manxisuo.spriglauncher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SprigDao {
    @Query("SELECT * FROM app_preferences")
    fun observePreferences(): Flow<List<AppPreferenceEntity>>

    @Query("SELECT * FROM app_preferences")
    suspend fun preferences(): List<AppPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(value: AppPreferenceEntity)

    @Query("SELECT * FROM usage_daily WHERE source = :source AND utcDay >= :minDay")
    suspend fun usage(source: String, minDay: Long): List<UsageDailyBucketEntity>

    @Query("SELECT * FROM usage_daily WHERE packageName = :packageName AND source = :source AND utcDay >= :minDay")
    suspend fun packageUsage(packageName: String, source: String, minDay: Long): List<UsageDailyBucketEntity>

    @Query("SELECT * FROM usage_daily")
    fun observeUsage(): Flow<List<UsageDailyBucketEntity>>

    @Query("SELECT * FROM usage_daily WHERE profile = :profile AND packageName = :packageName AND source = :source AND utcDay = :day")
    suspend fun bucket(profile: Long, packageName: String, source: String, day: Long): UsageDailyBucketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBucket(value: UsageDailyBucketEntity)

    @Query("SELECT * FROM usage_checkpoint WHERE id = 1")
    suspend fun checkpoint(): UsageCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckpoint(value: UsageCheckpointEntity)

    @Query("SELECT * FROM ranking_snapshot ORDER BY rank")
    fun observeRanking(): Flow<List<RankingSnapshotEntity>>

    @Query("SELECT * FROM ranking_snapshot ORDER BY rank")
    suspend fun ranking(): List<RankingSnapshotEntity>

    @Query("DELETE FROM ranking_snapshot")
    suspend fun deleteRanking()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRanking(values: List<RankingSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markProcessed(value: ProcessedUsageEventEntity): Long

    @Query("DELETE FROM processed_usage_events WHERE timestamp < :before")
    suspend fun pruneProcessed(before: Long)

    @Query("DELETE FROM usage_daily WHERE utcDay < :minDay")
    suspend fun pruneUsage(minDay: Long)

    @Query("DELETE FROM usage_daily")
    suspend fun clearUsage()

    @Query("DELETE FROM processed_usage_events")
    suspend fun clearProcessed()
}

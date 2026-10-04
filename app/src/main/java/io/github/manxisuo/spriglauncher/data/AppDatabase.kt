package io.github.manxisuo.spriglauncher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppPreferenceEntity::class, UsageDailyBucketEntity::class, UsageCheckpointEntity::class,
        RankingSnapshotEntity::class, ProcessedUsageEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SprigDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "sprig-launcher.db",
        ).build()
    }
}

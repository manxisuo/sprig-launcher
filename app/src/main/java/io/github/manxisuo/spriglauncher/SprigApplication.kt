package io.github.manxisuo.spriglauncher

import android.app.Application
import io.github.manxisuo.spriglauncher.data.AppDatabase
import io.github.manxisuo.spriglauncher.data.PreferenceRepository
import io.github.manxisuo.spriglauncher.data.SettingsRepository
import io.github.manxisuo.spriglauncher.data.UsageRepository
import io.github.manxisuo.spriglauncher.platform.AppCatalog
import io.github.manxisuo.spriglauncher.platform.BackgroundImageLoader
import io.github.manxisuo.spriglauncher.platform.SystemAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SprigApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var database: AppDatabase
    lateinit var catalog: AppCatalog
    lateinit var preferences: PreferenceRepository
    lateinit var usage: UsageRepository
    lateinit var settings: SettingsRepository
    lateinit var systemAccess: SystemAccess
    lateinit var backgroundImageLoader: BackgroundImageLoader

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.create(this)
        catalog = AppCatalog(this)
        preferences = PreferenceRepository(database)
        usage = UsageRepository(this, database)
        settings = SettingsRepository(this)
        systemAccess = SystemAccess(this)
        backgroundImageLoader = BackgroundImageLoader(this)
        requestCatalogRefresh()
    }

    fun requestCatalogRefresh() { scope.launch { catalog.refresh() } }
}

package io.github.manxisuo.spriglauncher.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.github.manxisuo.spriglauncher.SprigApplication
import io.github.manxisuo.spriglauncher.data.AppPreferenceEntity
import io.github.manxisuo.spriglauncher.data.BackgroundMode
import io.github.manxisuo.spriglauncher.data.BackgroundSettings
import io.github.manxisuo.spriglauncher.data.RankingSnapshotEntity
import io.github.manxisuo.spriglauncher.data.ThemeMode
import io.github.manxisuo.spriglauncher.domain.LaunchableEntry
import io.github.manxisuo.spriglauncher.domain.RankingEngine
import io.github.manxisuo.spriglauncher.domain.RankingInput
import io.github.manxisuo.spriglauncher.domain.UsagePoint
import io.github.manxisuo.spriglauncher.domain.UsageSource
import io.github.manxisuo.spriglauncher.domain.UsagePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class Screen { HOME, ALL_APPS, SETTINGS }

data class LauncherUiState(
    val screen: Screen = Screen.HOME,
    val entries: List<LaunchableEntry> = emptyList(),
    val icons: Map<String, Bitmap> = emptyMap(),
    val pinned: List<LaunchableEntry> = emptyList(),
    val frequent: List<LaunchableEntry> = emptyList(),
    val preferences: List<AppPreferenceEntity> = emptyList(),
    val rankingUpdatedAt: Long? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val backgroundMode: BackgroundMode = BackgroundMode.DEFAULT,
    val backgroundColorArgb: Long = 0xFFF3F0E8,
    val backgroundImage: Bitmap? = null,
    val backgroundImageUri: String? = null,
    val usageAccess: Boolean = false,
    val homeRoleHeld: Boolean = false,
    val coverageStartAt: Long? = null,
    val status: String? = null,
    val learning: Boolean = true,
)

data class AppStats(
    val source: UsageSource,
    val sevenDayCount: Int,
    val lastUsedAt: Long?,
    val score: Double,
    val coverageStartAt: Long?,
    val rankingUpdatedAt: Long?,
)

class MainViewModel(private val app: SprigApplication) : ViewModel() {
    private val _state = MutableStateFlow(LauncherUiState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()
    private var entries = emptyList<LaunchableEntry>()
    private var icons = emptyMap<String, Bitmap>()
    private var preferences = emptyList<AppPreferenceEntity>()
    private var usage = emptyList<io.github.manxisuo.spriglauncher.data.UsageDailyBucketEntity>()
    private var ranking = emptyList<RankingSnapshotEntity>()
    private var theme = ThemeMode.SYSTEM
    private var background = BackgroundSettings()
    private var backgroundImage: Bitmap? = null
    private var lastSyncAt = 0L

    init {
        viewModelScope.launch { app.catalog.entries.collectLatest { entries = it; rebuild() } }
        viewModelScope.launch { app.catalog.icons.collectLatest { icons = it; rebuild() } }
        viewModelScope.launch { app.preferences.preferences.collectLatest { preferences = it; rebuild() } }
        viewModelScope.launch { app.usage.usage.collectLatest { usage = it; rebuild() } }
        viewModelScope.launch { app.database.dao().observeRanking().collectLatest { ranking = it; rebuild() } }
        viewModelScope.launch { app.settings.theme.collectLatest { theme = it; rebuild() } }
        viewModelScope.launch {
            app.settings.background.collectLatest { value ->
                background = value
                backgroundImage = if (value.mode == BackgroundMode.IMAGE && value.imageUri != null) {
                    app.backgroundImageLoader.load(value.imageUri)
                } else null
                if (value.mode == BackgroundMode.IMAGE && backgroundImage == null) {
                    setStatus("背景图片无法读取，请重新选择")
                }
                rebuild()
            }
        }
    }

    fun onResume(forceSync: Boolean = false) = viewModelScope.launch {
        app.catalog.refresh()
        val access = app.systemAccess.hasUsageAccess()
        val now = System.currentTimeMillis()
        if (access && (forceSync || now - lastSyncAt >= 60_000)) {
            val result = runCatching { app.usage.importSystemEvents(entries.map { it.packageName }.toSet(), now) }
            lastSyncAt = now
            result.onFailure { setStatus("使用记录暂时不可用，已保留现有数据") }
        }
        val updated = ranking.firstOrNull()?.updatedAt
        val source = UsagePolicy.activeSource(access)
        val snapshotSource = ranking.firstOrNull()?.source?.let { runCatching { UsageSource.valueOf(it) }.getOrNull() }
        if (UsagePolicy.shouldPublishSnapshot(updated, snapshotSource, source, now, ZoneId.systemDefault())) publishRanking()
        rebuild()
    }

    fun navigate(screen: Screen) { _state.value = _state.value.copy(screen = screen) }
    fun back(): Boolean = when (_state.value.screen) {
        Screen.HOME -> false
        else -> { navigate(Screen.HOME); true }
    }

    fun launch(entry: LaunchableEntry) = viewModelScope.launch {
        val result = app.catalog.launch(entry)
        if (result.isSuccess) app.usage.recordLauncherRequest(entry.packageName, entry.userSerial)
        else {
            setStatus("无法启动 ${entry.label}，应用列表已刷新")
            app.catalog.refresh()
        }
    }

    fun togglePinned(entry: LaunchableEntry) = viewModelScope.launch { app.preferences.togglePinned(entry); publishRanking() }
    fun toggleExcluded(entry: LaunchableEntry) = viewModelScope.launch { app.preferences.toggleExcluded(entry); publishRanking() }
    fun movePinned(entry: LaunchableEntry, delta: Int) = viewModelScope.launch { app.preferences.movePinned(entry.id, delta) }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { app.settings.setTheme(value) }
    fun useDefaultBackground() = viewModelScope.launch { app.settings.useDefaultBackground() }
    fun useColorBackground(colorArgb: Long) = viewModelScope.launch { app.settings.useColorBackground(colorArgb) }
    fun useImageBackground(uri: String) = viewModelScope.launch { app.settings.useImageBackground(uri) }
    fun clearLearning() = viewModelScope.launch { app.usage.clearLearning(); publishRanking(); setStatus("学习数据已清空，固定与排除设置已保留") }
    fun manualResort() = viewModelScope.launch { publishRanking(); setStatus("常用区已重新排序") }
    fun homeRoleRequest(): Intent? = app.systemAccess.homeRoleRequest()
    fun homeSettingsIntent(): Intent = app.systemAccess.homeSettingsIntent()
    fun usageSettingsIntent(): Intent = app.systemAccess.usageSettingsIntent()
    fun appDetailsIntent(packageName: String): Intent = app.systemAccess.appDetailsIntent(packageName)
    fun clockIntent(): Intent = app.systemAccess.clockIntent()
    fun calendarIntent(): Intent = app.systemAccess.calendarIntent()
    fun externalLaunchFailed(label: String) { setStatus("未找到可打开的${label}应用") }
    fun backgroundImagePermissionFailed() { setStatus("无法保留图片读取权限，请换一张图片重试") }
    fun consumeStatus() { _state.value = _state.value.copy(status = null) }
    fun statsFor(entry: LaunchableEntry): AppStats {
        val source = UsagePolicy.activeSource(app.systemAccess.hasUsageAccess())
        val now = System.currentTimeMillis()
        val today = io.github.manxisuo.spriglauncher.data.UsageRepository.utcDay(now)
        val values = usage.filter { it.packageName == entry.packageName && it.source == source.name }
        return AppStats(
            source = source,
            sevenDayCount = values.filter { it.utcDay >= today - 6 }.sumOf { it.count },
            lastUsedAt = values.maxOfOrNull { it.lastUsedAt },
            score = RankingEngine.score(values.map { UsagePoint(it.packageName, it.utcDay, it.count) }, now),
            coverageStartAt = _state.value.coverageStartAt,
            rankingUpdatedAt = _state.value.rankingUpdatedAt,
        )
    }

    private suspend fun publishRanking() {
        val source = UsagePolicy.activeSource(app.systemAccess.hasUsageAccess())
        val now = System.currentTimeMillis()
        val minDay = io.github.manxisuo.spriglauncher.data.UsageRepository.utcDay(now - RankingEngine.WINDOW_DAYS * 86_400_000L)
        val values = app.database.dao().usage(source.name, minDay).map { UsagePoint(it.packageName, it.utcDay, it.count) }
        val currentPreferences = app.database.dao().preferences()
        val pinned = currentPreferences.filter { it.pinnedOrder != null }.sortedBy { it.pinnedOrder }.map { it.entryId }
        val excluded = currentPreferences.filter { it.excluded }.map { it.packageName }.toSet()
        val result = RankingEngine.rank(RankingInput(entries, pinned, excluded, values, ranking.map { it.entryId }, now))
        app.database.withTransaction {
            app.database.dao().deleteRanking()
            app.database.dao().saveRanking(result.mapIndexed { index, value -> RankingSnapshotEntity(value.entry.id, index, now, source.name) })
        }
    }

    private fun rebuild() {
        val pinnedIds = preferences.filter { it.pinnedOrder != null }.sortedBy { it.pinnedOrder }.map { it.entryId }
        val byId = entries.associateBy { it.id }
        val frequent = ranking.mapNotNull { byId[it.entryId] }.filterNot { it.id in pinnedIds }.take(12)
        val source = UsagePolicy.activeSource(app.systemAccess.hasUsageAccess())
        val hasUsage = usage.any { it.source == source.name && it.count > 0 }
        viewModelScope.launch {
            val checkpoint = app.usage.checkpoint()
            _state.value = _state.value.copy(
                entries = entries,
                icons = icons,
                pinned = pinnedIds.mapNotNull(byId::get),
                frequent = frequent,
                preferences = preferences,
                rankingUpdatedAt = ranking.firstOrNull()?.updatedAt,
                themeMode = theme,
                backgroundMode = background.mode,
                backgroundColorArgb = background.colorArgb,
                backgroundImage = backgroundImage,
                backgroundImageUri = background.imageUri,
                usageAccess = app.systemAccess.hasUsageAccess(),
                homeRoleHeld = app.systemAccess.isHomeRoleHeld(),
                coverageStartAt = checkpoint?.coverageStartAt?.takeIf { it > 0 },
                learning = !hasUsage,
            )
        }
    }

    private fun setStatus(message: String) { _state.value = _state.value.copy(status = message) }

    class Factory(private val app: SprigApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
    }
}

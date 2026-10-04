package io.github.manxisuo.spriglauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme: Flow<ThemeMode> = context.settingsDataStore.data.map { values ->
        runCatching { ThemeMode.valueOf(values[themeKey] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    suspend fun setTheme(mode: ThemeMode) = context.settingsDataStore.edit { it[themeKey] = mode.name }
}

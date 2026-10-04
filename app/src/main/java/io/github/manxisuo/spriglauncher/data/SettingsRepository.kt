package io.github.manxisuo.spriglauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class BackgroundMode { DEFAULT, COLOR, IMAGE }

data class BackgroundSettings(
    val mode: BackgroundMode = BackgroundMode.DEFAULT,
    val colorArgb: Long = 0xFFF3F0E8,
    val imageUri: String? = null,
)

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val backgroundModeKey = stringPreferencesKey("background_mode")
    private val backgroundColorKey = longPreferencesKey("background_color")
    private val backgroundImageKey = stringPreferencesKey("background_image")
    val theme: Flow<ThemeMode> = context.settingsDataStore.data.map { values ->
        runCatching { ThemeMode.valueOf(values[themeKey] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    val background: Flow<BackgroundSettings> = context.settingsDataStore.data.map { values ->
        BackgroundSettings(
            mode = runCatching {
                BackgroundMode.valueOf(values[backgroundModeKey] ?: BackgroundMode.DEFAULT.name)
            }.getOrDefault(BackgroundMode.DEFAULT),
            colorArgb = values[backgroundColorKey] ?: 0xFFF3F0E8,
            imageUri = values[backgroundImageKey],
        )
    }
    suspend fun setTheme(mode: ThemeMode) = context.settingsDataStore.edit { it[themeKey] = mode.name }
    suspend fun useDefaultBackground() = context.settingsDataStore.edit {
        it[backgroundModeKey] = BackgroundMode.DEFAULT.name
    }
    suspend fun useColorBackground(colorArgb: Long) = context.settingsDataStore.edit {
        it[backgroundModeKey] = BackgroundMode.COLOR.name
        it[backgroundColorKey] = colorArgb
    }
    suspend fun useImageBackground(uri: String) = context.settingsDataStore.edit {
        it[backgroundModeKey] = BackgroundMode.IMAGE.name
        it[backgroundImageKey] = uri
    }
}

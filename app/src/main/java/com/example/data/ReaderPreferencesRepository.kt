package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.HardwareTierPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fluxPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fluxpdf_preferences",
)

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    PAPER_LIGHT("Archival Paper"),
    OBSIDIAN_DARK("Obsidian Dark"),
}

enum class ReadingColorMode(val label: String, val shortLabel: String) {
    ORIGINAL("Original Paper", "Paper"),
    SEPIA("Warm Sepia", "Sepia"),
    MIDNIGHT("Midnight Ink", "Midnight"),
    OLED_DARK("OLED True Black", "OLED"),
    HIGH_CONTRAST("High Contrast", "Contrast"),
}

enum class ScrollMode(val label: String) {
    VERTICAL_CONTINUOUS("Vertical Scroll"),
    HORIZONTAL_PAGED("Single Page"),
    DOUBLE_PAGE_SPREAD("Two-Page Spread"),
}

data class ReaderPreferences(
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val readingColorMode: ReadingColorMode = ReadingColorMode.ORIGINAL,
    val scrollMode: ScrollMode = ScrollMode.VERTICAL_CONTINUOUS,
    val autoCropMargins: Boolean = false,
    val keepScreenOn: Boolean = false,
    val showTelemetryHud: Boolean = false,
    val volumeKeyNavigation: Boolean = true,
    val reflowFontScale: Float = 1.05f,
    val hardwareTierOverride: HardwareTierPreference = HardwareTierPreference.AUTO,
    val libraryGridView: Boolean = true,
)

class ReaderPreferencesRepository(private val context: Context) {

    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme_mode")
        val READING_COLOR = stringPreferencesKey("reading_color_mode")
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")
        val AUTO_CROP = booleanPreferencesKey("auto_crop_margins")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TELEMETRY_HUD = booleanPreferencesKey("show_telemetry_hud")
        val VOLUME_NAV = booleanPreferencesKey("volume_key_nav")
        val REFLOW_SCALE = floatPreferencesKey("reflow_font_scale")
        val HARDWARE_TIER = stringPreferencesKey("hardware_tier_override")
        val LIBRARY_GRID = booleanPreferencesKey("library_grid_view")
    }

    val preferencesFlow: Flow<ReaderPreferences> = context.fluxPrefsDataStore.data.map { p ->
        ReaderPreferences(
            appThemeMode = p[Keys.APP_THEME]?.let { name ->
                AppThemeMode.entries.firstOrNull { it.name == name }
            } ?: AppThemeMode.SYSTEM,
            readingColorMode = p[Keys.READING_COLOR]?.let { name ->
                ReadingColorMode.entries.firstOrNull { it.name == name }
            } ?: ReadingColorMode.ORIGINAL,
            scrollMode = p[Keys.SCROLL_MODE]?.let { name ->
                ScrollMode.entries.firstOrNull { it.name == name }
            } ?: ScrollMode.VERTICAL_CONTINUOUS,
            autoCropMargins = p[Keys.AUTO_CROP] ?: false,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            showTelemetryHud = p[Keys.TELEMETRY_HUD] ?: false,
            volumeKeyNavigation = p[Keys.VOLUME_NAV] ?: true,
            reflowFontScale = (p[Keys.REFLOW_SCALE] ?: 1.05f).coerceIn(0.8f, 1.85f),
            hardwareTierOverride = p[Keys.HARDWARE_TIER]?.let { name ->
                HardwareTierPreference.entries.firstOrNull { it.name == name }
            } ?: HardwareTierPreference.AUTO,
            libraryGridView = p[Keys.LIBRARY_GRID] ?: true,
        )
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.fluxPrefsDataStore.edit { it[Keys.APP_THEME] = mode.name }
    }

    suspend fun setReadingColorMode(mode: ReadingColorMode) {
        context.fluxPrefsDataStore.edit { it[Keys.READING_COLOR] = mode.name }
    }

    suspend fun setScrollMode(mode: ScrollMode) {
        context.fluxPrefsDataStore.edit { it[Keys.SCROLL_MODE] = mode.name }
    }

    suspend fun setAutoCropMargins(enabled: Boolean) {
        context.fluxPrefsDataStore.edit { it[Keys.AUTO_CROP] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.fluxPrefsDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setShowTelemetryHud(enabled: Boolean) {
        context.fluxPrefsDataStore.edit { it[Keys.TELEMETRY_HUD] = enabled }
    }

    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        context.fluxPrefsDataStore.edit { it[Keys.VOLUME_NAV] = enabled }
    }

    suspend fun setReflowFontScale(scale: Float) {
        context.fluxPrefsDataStore.edit { it[Keys.REFLOW_SCALE] = scale.coerceIn(0.8f, 1.85f) }
    }

    suspend fun setHardwareTierOverride(tier: HardwareTierPreference) {
        context.fluxPrefsDataStore.edit { it[Keys.HARDWARE_TIER] = tier.name }
    }

    suspend fun setLibraryGridView(grid: Boolean) {
        context.fluxPrefsDataStore.edit { it[Keys.LIBRARY_GRID] = grid }
    }
}

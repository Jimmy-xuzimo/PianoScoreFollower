package com.pianoscorefollower.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Persists the appearance choices made on the settings screen.
 *
 * The values are backed by Compose state rather than plain fields so that flipping a
 * switch recomposes the theme without the activity having to restart.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val modeState = mutableStateOf(readEnum(KEY_THEME_MODE, ThemeMode.System))
    private val paletteState = mutableStateOf(readEnum(KEY_PALETTE, ThemePalette.Dynamic))

    val themeMode: ThemeMode get() = modeState.value

    val palette: ThemePalette get() = paletteState.value

    fun setThemeMode(mode: ThemeMode) {
        if (modeState.value == mode) return
        modeState.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setPalette(palette: ThemePalette) {
        if (paletteState.value == palette) return
        paletteState.value = palette
        prefs.edit().putString(KEY_PALETTE, palette.name).apply()
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T {
        val stored = prefs.getString(key, null) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == stored } ?: fallback
    }

    private companion object {
        const val PREFS_NAME = "piano_follower_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PALETTE = "theme_palette"
    }
}

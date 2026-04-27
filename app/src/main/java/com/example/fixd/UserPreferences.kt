package com.example.fixd

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object UserPreferences {
    private const val PREFS_NAME = "fixd_user_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_SEED_COLOR = "theme_seed_color"
    private const val KEY_LAST_DESTINATION_PREFIX = "last_destination_"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    fun isGoogleUser(emailProviders: List<String>): Boolean {
        return emailProviders.contains("google.com")
    }

    fun applyThemeMode(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun saveThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, THEME_SYSTEM)
            ?: THEME_SYSTEM
    }

    fun saveThemeSeedColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_SEED_COLOR, color)
            .apply()
    }

    fun getThemeSeedColor(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_SEED_COLOR, ThemePaletteManager.DEFAULT_SEED_COLOR)
    }

    fun saveLastDestination(context: Context, userId: String, destination: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_LAST_DESTINATION_PREFIX$userId", destination)
            .apply()
    }

    fun getLastDestination(context: Context, userId: String): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_LAST_DESTINATION_PREFIX$userId", null)
    }

    fun applyTheme(context: Context) {
        applyThemeMode(getThemeMode(context))
    }

    fun isDarkMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}

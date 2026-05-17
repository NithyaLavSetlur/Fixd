package com.example.fixd

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object UserPreferences {
    private const val PREFS_NAME = "fixd_user_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_SEED_COLOR = "theme_seed_color"
    private const val KEY_LAST_DESTINATION_PREFIX = "last_destination_"
    private const val KEY_DISPLAYED_PROBLEMS = "displayed_problem_names"
    private const val KEY_SKIPPED_CHALLENGE_TASKS_PREFIX = "skipped_challenge_tasks_"
    private const val KEY_TILE_SWIPE_LEFT_ACTION = "tile_swipe_left_action"
    private const val KEY_TILE_SWIPE_RIGHT_ACTION = "tile_swipe_right_action"
    private const val KEY_LAUNCHER_ICON_ID = "launcher_icon_id"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val TILE_SWIPE_NONE = "none"
    const val TILE_SWIPE_OPEN = "open"
    const val TILE_SWIPE_PREVIOUS = "previous"
    const val TILE_SWIPE_NEXT = "next"

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
        prefs(context)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun getThemeMode(context: Context): String {
        return prefs(context)
            .getString(KEY_THEME_MODE, THEME_SYSTEM)
            ?: THEME_SYSTEM
    }

    fun saveThemeSeedColor(context: Context, color: Int) {
        prefs(context)
            .edit()
            .putInt(KEY_THEME_SEED_COLOR, color)
            .apply()
    }

    fun getThemeSeedColor(context: Context): Int {
        return prefs(context)
            .getInt(KEY_THEME_SEED_COLOR, ThemePaletteManager.DEFAULT_SEED_COLOR)
    }

    fun saveLastDestination(context: Context, userId: String, destination: String) {
        prefs(context)
            .edit()
            .putString("$KEY_LAST_DESTINATION_PREFIX$userId", destination)
            .apply()
    }

    fun getLastDestination(context: Context, userId: String): String? {
        return prefs(context)
            .getString("$KEY_LAST_DESTINATION_PREFIX$userId", null)
    }

    fun saveDisplayedProblems(context: Context, problems: List<ProblemArea>) {
        prefs(context)
            .edit()
            .putStringSet(KEY_DISPLAYED_PROBLEMS, problems.map { it.name }.toSet())
            .apply()
    }

    fun isProblemDisplayed(context: Context, area: ProblemArea): Boolean {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_DISPLAYED_PROBLEMS)) return true
        return prefs.getStringSet(KEY_DISPLAYED_PROBLEMS, emptySet()).orEmpty().contains(area.name)
    }

    fun saveSkippedChallengeTasksForDay(context: Context, userId: String, dayKey: String, taskIds: Set<String>) {
        prefs(context)
            .edit()
            .putStringSet("$KEY_SKIPPED_CHALLENGE_TASKS_PREFIX$userId:$dayKey", taskIds)
            .apply()
    }

    fun getSkippedChallengeTasksForDay(context: Context, userId: String, dayKey: String): Set<String> {
        return prefs(context)
            .getStringSet("$KEY_SKIPPED_CHALLENGE_TASKS_PREFIX$userId:$dayKey", emptySet())
            .orEmpty()
    }

    fun saveTileSwipeLeftAction(context: Context, action: String) {
        prefs(context)
            .edit()
            .putString(KEY_TILE_SWIPE_LEFT_ACTION, sanitizeTileSwipeAction(action, TILE_SWIPE_NEXT))
            .apply()
    }

    fun getTileSwipeLeftAction(context: Context): String {
        return prefs(context)
            .getString(KEY_TILE_SWIPE_LEFT_ACTION, TILE_SWIPE_NEXT)
            ?.let { sanitizeTileSwipeAction(it, TILE_SWIPE_NEXT) }
            ?: TILE_SWIPE_NEXT
    }

    fun saveTileSwipeRightAction(context: Context, action: String) {
        prefs(context)
            .edit()
            .putString(KEY_TILE_SWIPE_RIGHT_ACTION, sanitizeTileSwipeAction(action, TILE_SWIPE_PREVIOUS))
            .apply()
    }

    fun getTileSwipeRightAction(context: Context): String {
        return prefs(context)
            .getString(KEY_TILE_SWIPE_RIGHT_ACTION, TILE_SWIPE_PREVIOUS)
            ?.let { sanitizeTileSwipeAction(it, TILE_SWIPE_PREVIOUS) }
            ?: TILE_SWIPE_PREVIOUS
    }

    fun saveLauncherIconId(context: Context, iconId: String) {
        prefs(context)
            .edit()
            .putString(KEY_LAUNCHER_ICON_ID, sanitizeLauncherIconId(iconId))
            .apply()
    }

    fun getLauncherIconId(context: Context): String {
        return prefs(context)
            .getString(KEY_LAUNCHER_ICON_ID, LauncherIconManager.ICON_DEFAULT)
            ?.let { sanitizeLauncherIconId(it) }
            ?: LauncherIconManager.ICON_DEFAULT
    }

    fun applyTheme(context: Context) {
        applyThemeMode(getThemeMode(context))
    }

    fun isDarkMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun sanitizeTileSwipeAction(action: String, fallback: String): String {
        return when (action) {
            TILE_SWIPE_NONE,
            TILE_SWIPE_OPEN,
            TILE_SWIPE_PREVIOUS,
            TILE_SWIPE_NEXT -> action
            else -> fallback
        }
    }

    private fun sanitizeLauncherIconId(iconId: String): String {
        return LauncherIconManager.options.firstOrNull { it.id == iconId }?.id
            ?: LauncherIconManager.ICON_DEFAULT
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

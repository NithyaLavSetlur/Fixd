package com.example.fixd

data class UserAppearanceSettings(
    val themeMode: String = UserPreferences.THEME_SYSTEM,
    val themeSeedColor: Int = ThemePaletteManager.DEFAULT_SEED_COLOR
)

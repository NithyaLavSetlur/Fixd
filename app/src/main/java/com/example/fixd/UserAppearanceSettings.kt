package com.example.fixd

data class UserAppearanceSettings(
    val themeMode: String = UserPreferences.THEME_SYSTEM,
    val paletteId: String = ThemePaletteManager.PALETTE_OCEAN,
    val backgroundStoragePath: String = "",
    val backgroundBrightness: Int = AppBackgroundManager.DEFAULT_BRIGHTNESS,
    val backgroundBlur: Int = AppBackgroundManager.DEFAULT_BLUR
) {
    fun hasBackground(): Boolean = backgroundStoragePath.isNotBlank()
}

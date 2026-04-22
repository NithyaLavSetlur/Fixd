package com.example.fixd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

@Composable
fun FixdComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = ThemePaletteManager.currentPalette(context)
    val isDark = UserPreferences.isDarkMode(context)
    val surfaceVariant = Color(ColorUtils.blendARGB(palette.card, palette.surface, if (isDark) 0.35f else 0.55f))
    val onSurfaceVariant = ThemePaletteManager.mutedContentColorForRole(SurfaceRole.CARD, palette)
    val scheme = if (isDark) {
        darkColorScheme(
            primary = Color(palette.primary),
            onPrimary = Color(ThemePaletteManager.readableTextColorOn(palette.primary, palette)),
            secondary = Color(palette.secondary),
            onSecondary = Color(ThemePaletteManager.readableTextColorOn(palette.secondary, palette)),
            background = Color(palette.surface),
            onBackground = Color(ThemePaletteManager.readableTextColorOn(palette.surface, palette)),
            surface = Color(palette.card),
            onSurface = Color(ThemePaletteManager.readableTextColorOn(palette.card, palette)),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(onSurfaceVariant),
            error = Color(palette.danger),
            onError = Color(ThemePaletteManager.readableTextColorOn(palette.danger, palette))
        )
    } else {
        lightColorScheme(
            primary = Color(palette.primary),
            onPrimary = Color(ThemePaletteManager.readableTextColorOn(palette.primary, palette)),
            secondary = Color(palette.secondary),
            onSecondary = Color(ThemePaletteManager.readableTextColorOn(palette.secondary, palette)),
            background = Color(palette.surface),
            onBackground = Color(ThemePaletteManager.readableTextColorOn(palette.surface, palette)),
            surface = Color(palette.card),
            onSurface = Color(ThemePaletteManager.readableTextColorOn(palette.card, palette)),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(onSurfaceVariant),
            error = Color(palette.danger),
            onError = Color(ThemePaletteManager.readableTextColorOn(palette.danger, palette))
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

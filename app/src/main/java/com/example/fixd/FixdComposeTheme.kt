package com.example.fixd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

    val baseTypography = Typography()
    val typography = Typography(
        displayLarge = baseTypography.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = baseTypography.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = baseTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = baseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = baseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    )

    MaterialTheme(
        colorScheme = scheme,
        shapes = shapes,
        typography = typography,
        content = content
    )
}

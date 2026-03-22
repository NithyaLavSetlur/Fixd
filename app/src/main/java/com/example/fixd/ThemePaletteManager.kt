package com.example.fixd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StyleRes
import com.google.firebase.auth.FirebaseAuth

data class ThemePalette(
    val id: String,
    val label: String,
    @StyleRes val overlayRes: Int,
    val launcherAlias: String,
    val sampleColor: Int
) {
    val red: Int get() = (sampleColor shr 16) and 0xFF
    val green: Int get() = (sampleColor shr 8) and 0xFF
    val blue: Int get() = sampleColor and 0xFF
}

object ThemePaletteManager {
    const val PALETTE_OCEAN = "ocean"
    const val PALETTE_MINT = "mint"
    const val PALETTE_SUNSET = "sunset"
    const val PALETTE_ORCHID = "orchid"

    val palettes = listOf(
        ThemePalette(PALETTE_OCEAN, "Ocean", R.style.ThemeOverlay_Fixd_Palette_Ocean, ".SplashOceanAlias", 0x168AA7),
        ThemePalette(PALETTE_MINT, "Mint", R.style.ThemeOverlay_Fixd_Palette_Mint, ".SplashMintAlias", 0x24B7A8),
        ThemePalette(PALETTE_SUNSET, "Sunset", R.style.ThemeOverlay_Fixd_Palette_Sunset, ".SplashSunsetAlias", 0xD56B4A),
        ThemePalette(PALETTE_ORCHID, "Orchid", R.style.ThemeOverlay_Fixd_Palette_Orchid, ".SplashOrchidAlias", 0x7567D8)
    )

    fun paletteById(id: String?): ThemePalette = palettes.firstOrNull { it.id == id } ?: palettes.first()

    fun applyOverlay(context: Context, appearance: UserAppearanceSettings = AppBackgroundManager.currentSettings()) {
        context.theme.applyStyle(paletteById(appearance.paletteId).overlayRes, true)
    }

    fun nearestPalette(imageBytes: ByteArray): ThemePalette {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return palettes.first()
        val sampleSize = 12
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, true)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (x in 0 until sampleSize) {
            for (y in 0 until sampleSize) {
                val pixel = scaled.getPixel(x, y)
                red += ((pixel shr 16) and 0xFF).toLong()
                green += ((pixel shr 8) and 0xFF).toLong()
                blue += (pixel and 0xFF).toLong()
                count++
            }
        }
        val avgR = (red / count).toInt()
        val avgG = (green / count).toInt()
        val avgB = (blue / count).toInt()
        return palettes.minByOrNull { palette ->
            val dr = palette.red - avgR
            val dg = palette.green - avgG
            val db = palette.blue - avgB
            dr * dr + dg * dg + db * db
        } ?: palettes.first()
    }

    fun syncFromAppearance(context: Context) {
        applyOverlay(context)
    }

    fun loadSignedInUserAppearance(context: Context, onComplete: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            AppBackgroundManager.clearCurrentSettings()
            onComplete?.invoke()
            return
        }
        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                AppBackgroundManager.updateSettings(settings)
                UserPreferences.saveThemeMode(context, settings.themeMode)
                UserPreferences.applyThemeMode(settings.themeMode)
                syncFromAppearance(context)
                onComplete?.invoke()
            },
            onFailure = {
                onComplete?.invoke()
            }
        )
    }
}

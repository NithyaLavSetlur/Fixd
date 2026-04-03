package com.example.fixd

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

data class GeneratedPalette(
    val primary: Int,
    val primaryDark: Int,
    val secondary: Int,
    val surface: Int,
    val card: Int,
    val text: Int,
    val textMuted: Int,
    val accent: Int,
    val gradientMid: Int,
    val onPrimary: Int
)

object ThemePaletteManager {
    const val DEFAULT_SEED_COLOR = 0xFF168AA7.toInt()

    private var currentSettings = UserAppearanceSettings()

    fun currentSettings(): UserAppearanceSettings = currentSettings

    fun updateSettings(settings: UserAppearanceSettings) {
        currentSettings = settings
    }

    fun clearCurrentSettings() {
        currentSettings = UserAppearanceSettings()
    }

    fun applyOverlay(context: Context) = Unit

    fun syncFromAppearance(context: Context) {
        if (context is Activity) {
            applyToActivity(context)
        }
    }

    fun paletteFor(settings: UserAppearanceSettings, isDarkMode: Boolean): GeneratedPalette {
        val hsv = FloatArray(3)
        Color.colorToHSV(settings.themeSeedColor, hsv)
        val hue = hsv[0]
        val saturation = hsv[1].coerceIn(0.25f, 0.9f)
        val value = hsv[2].coerceIn(0.45f, 0.95f)

        val primary = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        val primaryDark = Color.HSVToColor(floatArrayOf(hue, (saturation + 0.08f).coerceAtMost(1f), (value * 0.68f).coerceAtLeast(0.22f)))
        val secondary = Color.HSVToColor(floatArrayOf((hue + 18f) % 360f, (saturation * 0.72f).coerceAtLeast(0.22f), (value * 1.05f).coerceAtMost(1f)))
        val accent = Color.HSVToColor(floatArrayOf((hue + 28f) % 360f, (saturation * 0.58f).coerceAtLeast(0.18f), 0.96f))
        val gradientMid = ColorUtils.blendARGB(primary, secondary, 0.45f)

        val surfaceBase = if (isDarkMode) 0xFF08161C.toInt() else 0xFFF4FAFB.toInt()
        val cardBase = if (isDarkMode) 0xFF12242C.toInt() else 0xFFFFFFFF.toInt()
        val surface = ColorUtils.blendARGB(surfaceBase, primaryDark, if (isDarkMode) 0.16f else 0.08f)
        val card = ColorUtils.blendARGB(cardBase, primary, if (isDarkMode) 0.12f else 0.07f)
        val text = if (isDarkMode) 0xFFF3FAFC.toInt() else 0xFF102A35.toInt()
        val textMuted = ColorUtils.blendARGB(text, surface, 0.42f)
        val onPrimary = if (ColorUtils.calculateLuminance(primary) > 0.45) Color.BLACK else Color.WHITE

        return GeneratedPalette(
            primary = primary,
            primaryDark = primaryDark,
            secondary = secondary,
            surface = surface,
            card = card,
            text = text,
            textMuted = textMuted,
            accent = accent,
            gradientMid = gradientMid,
            onPrimary = onPrimary
        )
    }

    fun applyToActivity(activity: Activity) {
        val palette = paletteFor(currentSettings, UserPreferences.isDarkMode(activity))
        activity.window.setBackgroundDrawable(ColorDrawable(palette.surface))
        activity.window.statusBarColor = palette.card
        activity.window.navigationBarColor = palette.card
        applyToView(activity.findViewById(android.R.id.content), palette)
    }

    fun applyToView(view: View?, palette: GeneratedPalette) {
        if (view == null) return

        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(palette.card)
                view.strokeColor = Color.TRANSPARENT
            }
            is MaterialButton -> {
                view.backgroundTintList = ColorStateList.valueOf(palette.primary)
                view.setTextColor(palette.onPrimary)
                view.strokeColor = ColorStateList.valueOf(palette.primaryDark)
            }
            is BottomNavigationView -> {
                view.setBackgroundColor(palette.card)
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                )
                val colors = intArrayOf(palette.primary, palette.textMuted)
                val stateList = ColorStateList(states, colors)
                view.itemIconTintList = stateList
                view.itemTextColor = stateList
            }
            is NavigationView -> {
                view.setBackgroundColor(palette.card)
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                )
                val colors = intArrayOf(palette.primary, palette.text)
                view.itemIconTintList = ColorStateList(states, colors)
                view.itemTextColor = ColorStateList(states, colors)
            }
            is TextInputLayout -> {
                view.boxBackgroundColor = palette.card
                view.setBoxStrokeColorStateList(ColorStateList.valueOf(palette.accent))
                view.defaultHintTextColor = ColorStateList.valueOf(palette.textMuted)
            }
            is TextInputEditText, is EditText -> {
                (view as TextView).setTextColor(palette.text)
                view.setHintTextColor(palette.textMuted)
            }
            is LinearProgressIndicator -> {
                view.setIndicatorColor(palette.secondary)
                view.trackColor = ColorUtils.blendARGB(palette.surface, palette.textMuted, 0.18f)
            }
            is ProgressBar -> {
                view.indeterminateTintList = ColorStateList.valueOf(palette.primary)
                view.progressTintList = ColorStateList.valueOf(palette.primary)
            }
            is CompoundButton -> {
                view.buttonTintList = ColorStateList.valueOf(palette.primary)
                view.setTextColor(palette.text)
            }
            is ImageButton -> {
                view.imageTintList = ColorStateList.valueOf(palette.text)
            }
            is ImageView -> {
                if (view.drawable == null) {
                    view.background = gradientBackground(palette)
                }
            }
            is TextView -> {
                val current = view.currentTextColor
                val useMuted = ColorUtils.calculateContrast(current, Color.WHITE) < 3.0 &&
                    ColorUtils.calculateContrast(current, Color.BLACK) < 3.0
                view.setTextColor(if (useMuted) palette.textMuted else palette.text)
            }
        }

        if (view is ViewGroup) {
            if (view is ScrollView || view.background == null || view.background is ColorDrawable || view.background is GradientDrawable) {
                if (view !is MaterialCardView && view !is NavigationView && view !is BottomNavigationView) {
                    view.setBackgroundColor(palette.surface)
                }
            }
            view.children.forEach { child ->
                applyToView(child, palette)
            }
        }
    }

    private fun gradientBackground(palette: GeneratedPalette): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ColorUtils.blendARGB(palette.primaryDark, palette.surface, 0.35f),
                ColorUtils.blendARGB(palette.gradientMid, palette.surface, 0.7f)
            )
        )
    }

    fun loadSignedInUserAppearance(context: Context, onComplete: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            clearCurrentSettings()
            onComplete?.invoke()
            return
        }
        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                updateSettings(settings)
                UserPreferences.saveThemeMode(context, settings.themeMode)
                UserPreferences.applyThemeMode(settings.themeMode)
                onComplete?.invoke()
            },
            onFailure = {
                onComplete?.invoke()
            }
        )
    }
}

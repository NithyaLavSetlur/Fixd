package com.example.fixd

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.google.android.material.chip.Chip
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
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
    val success: Int,
    val danger: Int,
    val accent: Int,
    val gradientMid: Int,
    val onPrimary: Int,
    val onCard: Int,
    val onSurface: Int
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

        val surfaceBase = if (isDarkMode) 0xFF08161C.toInt() else 0xFFFFFFFF.toInt()
        val cardBase = if (isDarkMode) 0xFF12242C.toInt() else 0xFFFFFFFF.toInt()
        val surface = if (isDarkMode) {
            ColorUtils.blendARGB(surfaceBase, primaryDark, 0.16f)
        } else {
            surfaceBase
        }
        val card = if (isDarkMode) {
            ColorUtils.blendARGB(cardBase, primary, 0.12f)
        } else {
            cardBase
        }
        val text = if (isDarkMode) 0xFFF3FAFC.toInt() else 0xFF102A35.toInt()
        val textMuted = ColorUtils.blendARGB(text, surface, 0.42f)
        val success = if (isDarkMode) 0xFF7AD9B2.toInt() else 0xFF1E8A66.toInt()
        val danger = if (isDarkMode) 0xFFFF8B8B.toInt() else 0xFFB03A3A.toInt()
        val onPrimary = if (ColorUtils.calculateLuminance(primary) > 0.45) Color.BLACK else Color.WHITE
        val onCard = if (ColorUtils.calculateLuminance(card) > 0.45) 0xFF102A35.toInt() else 0xFFF3FAFC.toInt()
        val onSurface = if (ColorUtils.calculateLuminance(surface) > 0.45) 0xFF102A35.toInt() else 0xFFF3FAFC.toInt()

        return GeneratedPalette(
            primary = primary,
            primaryDark = primaryDark,
            secondary = secondary,
            surface = surface,
            card = card,
            text = text,
            textMuted = textMuted,
            success = success,
            danger = danger,
            accent = accent,
            gradientMid = gradientMid,
            onPrimary = onPrimary,
            onCard = onCard,
            onSurface = onSurface
        )
    }

    fun applyToActivity(activity: Activity) {
        val palette = paletteFor(currentSettings, UserPreferences.isDarkMode(activity))
        activity.window.setBackgroundDrawable(ColorDrawable(palette.surface))
        activity.window.statusBarColor = palette.card
        activity.window.navigationBarColor = palette.card
        applyToView(activity.findViewById(android.R.id.content), palette)
    }

    fun applyToDialog(dialog: AlertDialog) {
        val context = dialog.context
        val palette = paletteFor(currentSettings, UserPreferences.isDarkMode(context))
        dialog.window?.let { window ->
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 30f * context.resources.displayMetrics.density
                colors = intArrayOf(
                    ColorUtils.blendARGB(palette.card, palette.surface, 0.08f),
                    palette.card
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            window.setBackgroundDrawable(background)
            window.setDimAmount(0.42f)
            window.attributes = window.attributes.apply {
                windowAnimations = R.style.Animation_Fixd_Dialog
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius((22 * context.resources.displayMetrics.density).toInt())
                window.setDimAmount(0.46f)
            }
        }
        applyToView(dialog.window?.decorView, palette)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { applyDialogButton(it, palette, filled = true) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { applyDialogButton(it, palette, filled = false) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { applyDialogButton(it, palette, filled = false) }
    }

    fun applyToView(view: View?, palette: GeneratedPalette) {
        if (view == null) return

        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(palette.card)
                view.strokeColor = Color.TRANSPARENT
            }
            is MaterialButton -> {
                applyButtonColors(view, palette)
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
                view.setHintTextColor(ColorStateList.valueOf(palette.textMuted))
                view.setBoxStrokeColorStateList(ColorStateList.valueOf(palette.primary))
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
                applyCompoundButtonColors(view, palette)
            }
            is SwitchMaterial -> {
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                )
                view.thumbTintList = ColorStateList(
                    states,
                    intArrayOf(palette.primary, ColorUtils.blendARGB(palette.card, palette.textMuted, 0.25f))
                )
                view.trackTintList = ColorStateList(
                    states,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(palette.primary, 130),
                        ColorUtils.blendARGB(palette.surface, palette.textMuted, 0.18f)
                    )
                )
            }
            is Chip -> {
                applyChipColors(view, palette)
            }
            is ImageButton -> {
                if (view.background is GradientDrawable || view.background is ColorDrawable) {
                    view.backgroundTintList = ColorStateList.valueOf(palette.card)
                }
                view.imageTintList = ColorStateList.valueOf(palette.text)
            }
            is ShapeableImageView -> {
                if (view.background is ColorDrawable) {
                    view.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 26))
                }
                if (view.drawable == null) {
                    view.background = gradientBackground(palette)
                } else if (view.imageTintList != null) {
                    view.imageTintList = ColorStateList.valueOf(palette.primary)
                }
                view.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 56))
            }
            is ImageView -> {
                if (view.drawable == null) {
                    view.background = gradientBackground(palette)
                } else {
                    tintImageViewIfNeeded(view, palette)
                }
            }
            is TextView -> {
                applyTextColor(view, palette)
            }
        }

        if (view is ViewGroup) {
            if (view.background == null || view.background is ColorDrawable) {
                if (view !is MaterialCardView &&
                    view !is NavigationView &&
                    view !is BottomNavigationView &&
                    !hasAncestorOfType<MaterialCardView>(view)
                ) {
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

    private fun applyButtonColors(button: MaterialButton, palette: GeneratedPalette) {
        val isOutlined = button.strokeWidth > 0
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf()
        )
        if (isOutlined) {
            val enabledBackground = ColorUtils.setAlphaComponent(palette.card, 235)
            val disabledBackground = ColorUtils.blendARGB(palette.card, palette.surface, 0.55f)
            val enabledText = readableTextOn(enabledBackground, palette, fallback = palette.primary)
            val disabledText = ColorUtils.blendARGB(enabledText, disabledBackground, 0.38f)
            button.backgroundTintList = ColorStateList(
                states,
                intArrayOf(disabledBackground, enabledBackground)
            )
            button.setTextColor(
                ColorStateList(
                    states,
                    intArrayOf(disabledText, enabledText)
                )
            )
            button.iconTint = ColorStateList(
                states,
                intArrayOf(disabledText, enabledText)
            )
            button.strokeColor = ColorStateList(
                states,
                intArrayOf(
                    ColorUtils.blendARGB(palette.textMuted, disabledBackground, 0.24f),
                    palette.primary
                )
            )
        } else {
            val enabledBackground = palette.primary
            val disabledBackground = ColorUtils.blendARGB(palette.primary, palette.card, 0.72f)
            val enabledText = readableTextOn(enabledBackground, palette, fallback = palette.onPrimary)
            val disabledText = readableTextOn(disabledBackground, palette, fallback = palette.textMuted)
            button.backgroundTintList = ColorStateList(
                states,
                intArrayOf(disabledBackground, enabledBackground)
            )
            button.setTextColor(
                ColorStateList(
                    states,
                    intArrayOf(disabledText, enabledText)
                )
            )
            button.iconTint = ColorStateList(
                states,
                intArrayOf(disabledText, enabledText)
            )
            button.strokeColor = ColorStateList(
                states,
                intArrayOf(disabledBackground, palette.primaryDark)
            )
        }
        button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(readableTextOn(palette.primary, palette, palette.onPrimary), 26))
    }

    private fun applyCompoundButtonColors(button: CompoundButton, palette: GeneratedPalette) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val tint = ColorStateList(
            states,
            intArrayOf(
                palette.primary,
                ColorUtils.blendARGB(palette.card, palette.textMuted, 0.32f)
            )
        )
        button.buttonTintList = tint
        button.setTextColor(palette.text)
    }

    private fun applyChipColors(chip: Chip, palette: GeneratedPalette) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        chip.chipBackgroundColor = ColorStateList(
            states,
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.primary, 230),
                ColorUtils.blendARGB(palette.card, palette.surface, 0.35f)
            )
        )
        chip.setTextColor(
            ColorStateList(
                states,
                intArrayOf(palette.onPrimary, palette.text)
            )
        )
        chip.chipStrokeColor = ColorStateList(
            states,
            intArrayOf(
                palette.primaryDark,
                ColorUtils.setAlphaComponent(palette.primary, 72)
            )
        )
        chip.chipIconTint = ColorStateList(
            states,
            intArrayOf(palette.onPrimary, palette.primary)
        )
        chip.checkedIconTint = ColorStateList.valueOf(palette.onPrimary)
        chip.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 36))
    }

    private fun tintImageViewIfNeeded(view: ImageView, palette: GeneratedPalette) {
        val background = view.background
        if (background is ColorDrawable) {
            view.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 22))
        }
        val tintList = view.imageTintList
        if (tintList != null || background is GradientDrawable || background is ColorDrawable) {
            view.imageTintList = ColorStateList.valueOf(palette.primary)
        }
    }

    private fun applyTextColor(textView: TextView, palette: GeneratedPalette) {
        val current = textView.currentTextColor
        textView.setTextColor(
            when {
                looksLikeColor(current, Color.WHITE) -> chooseTextForContext(textView, palette, preferLight = true)
                looksLikeAny(current, listOf(0xFF5F7D88.toInt(), 0xFF9BB4BC.toInt())) -> palette.textMuted
                looksLikeAny(current, listOf(0xFF1E8A66.toInt(), 0xFF7AD9B2.toInt())) -> palette.success
                looksLikeAny(current, listOf(0xFF2F8FCE.toInt(), 0xFF6CB7F0.toInt())) -> palette.secondary
                looksLikeAny(current, listOf(0xFFD14343.toInt(), 0xFFB03A3A.toInt(), 0xFFFF8B8B.toInt())) -> palette.danger
                else -> chooseTextForContext(textView, palette, preferLight = false)
            }
        )
    }

    private fun chooseTextForContext(textView: TextView, palette: GeneratedPalette, preferLight: Boolean): Int {
        val background = findNearestSolidBackground(textView)
        if (background != null) {
            return if (ColorUtils.calculateContrast(palette.text, background) >= 4.5) {
                palette.text
            } else if (ColorUtils.calculateContrast(palette.onSurface, background) >= 4.5) {
                palette.onSurface
            } else if (ColorUtils.calculateContrast(palette.onCard, background) >= 4.5) {
                palette.onCard
            } else if (preferLight) {
                Color.WHITE
            } else {
                palette.text
            }
        }
        return if (preferLight) Color.WHITE else palette.text
    }

    private fun readableTextOn(background: Int, palette: GeneratedPalette, fallback: Int): Int {
        val opaqueBackground = if (Color.alpha(background) < 255) {
            ColorUtils.compositeColors(background, palette.surface)
        } else {
            background
        }
        val candidates = listOf(
            palette.onPrimary,
            palette.onCard,
            palette.onSurface,
            palette.text,
            Color.WHITE,
            Color.BLACK,
            fallback
        ).distinct()
        return candidates.maxByOrNull { ColorUtils.calculateContrast(it, opaqueBackground) } ?: fallback
    }

    private fun findNearestSolidBackground(view: View): Int? {
        var current: View? = view
        while (current != null) {
            val drawable = current.background
            when (drawable) {
                is ColorDrawable -> return drawable.color
                is GradientDrawable -> return null
            }
            current = current.parent as? View
        }
        return null
    }

    private inline fun <reified T : View> hasAncestorOfType(view: View): Boolean {
        var current = view.parent as? View
        while (current != null) {
            if (current is T) return true
            current = current.parent as? View
        }
        return false
    }

    private fun looksLikeAny(color: Int, candidates: List<Int>): Boolean {
        return candidates.any { looksLikeColor(color, it) }
    }

    private fun looksLikeColor(color: Int, candidate: Int, tolerance: Int = 42): Boolean {
        val dr = Color.red(color) - Color.red(candidate)
        val dg = Color.green(color) - Color.green(candidate)
        val db = Color.blue(color) - Color.blue(candidate)
        return dr * dr + dg * dg + db * db <= tolerance * tolerance * 3
    }

    private fun applyDialogButton(button: TextView, palette: GeneratedPalette, filled: Boolean) {
        val dialogSurface = palette.card
        val targetColor = if (filled) palette.primary else palette.secondary
        val readableColor = readableTextOn(dialogSurface, palette, targetColor)
        button.setTextColor(
            if (ColorUtils.calculateContrast(targetColor, dialogSurface) >= 4.5) {
                targetColor
            } else {
                readableColor
            }
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

package com.example.fixd

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.doOnAttach
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
    private const val MIST_SCROLL_REENABLE_DELAY_MS = 180L

    private var currentSettings = UserAppearanceSettings()

    fun currentSettings(): UserAppearanceSettings = currentSettings

    fun updateSettings(settings: UserAppearanceSettings) {
        currentSettings = settings
    }

    fun clearCurrentSettings() {
        currentSettings = UserAppearanceSettings()
    }

    fun applyOverlay(context: Context) = Unit

    fun loadCachedSettings(context: Context) {
        updateSettings(
            UserAppearanceSettings(
                themeMode = UserPreferences.getThemeMode(context),
                themeSeedColor = UserPreferences.getThemeSeedColor(context)
            )
        )
    }

    fun syncFromAppearance(context: Context) {
        if (context is Activity) {
            applyToActivity(context)
        }
    }

    fun currentPalette(context: Context): GeneratedPalette {
        return paletteFor(currentSettings, UserPreferences.isDarkMode(context))
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
            window.setDimAmount(0.52f)
            window.attributes = window.attributes.apply {
                windowAnimations = R.style.Animation_Fixd_Dialog
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val horizontalInset = (24 * context.resources.displayMetrics.density).toInt()
            val maxWidth = (context.resources.displayMetrics.widthPixels - (horizontalInset * 2)).coerceAtLeast(0)
            window.setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = (18 * context.resources.displayMetrics.density).toInt()
                }
                window.setBackgroundBlurRadius((28 * context.resources.displayMetrics.density).toInt())
                window.setDimAmount(0.56f)
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
            is MistBorderView -> {
                view.applyPalette(palette)
            }
            is MaterialCardView -> {
                view.setCardBackgroundColor(palette.card)
                view.strokeColor = Color.TRANSPARENT
            }
            is MaterialButton -> {
                applyButtonColors(view, palette)
            }
            is Button -> {
                applyPlainButtonColors(view, palette)
            }
            is BottomNavigationView -> {
                val navBackground = palette.card
                val selectedColor = ensureContrast(readableAccentOn(navBackground, palette), navBackground, palette)
                val unselectedColor = adaptiveMutedTextColor(navBackground, palette)
                view.setBackgroundColor(navBackground)
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                )
                val colors = intArrayOf(selectedColor, unselectedColor)
                val stateList = ColorStateList(states, colors)
                view.itemIconTintList = stateList
                view.itemTextColor = stateList
            }
            is NavigationView -> {
                val navBackground = palette.card
                val selectedColor = ensureContrast(readableAccentOn(navBackground, palette), navBackground, palette)
                val defaultColor = adaptiveTextColor(navBackground, palette)
                view.setBackgroundColor(navBackground)
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                )
                val colors = intArrayOf(selectedColor, defaultColor)
                view.itemIconTintList = ColorStateList(states, colors)
                view.itemTextColor = ColorStateList(states, colors)
            }
            is TextInputLayout -> {
                val fieldBackground = resolveFieldBackground(view, palette)
                val fieldText = adaptiveTextColor(fieldBackground, palette)
                val fieldHint = adaptiveMutedTextColor(fieldBackground, palette)
                view.boxBackgroundColor = fieldBackground
                view.defaultHintTextColor = ColorStateList.valueOf(fieldHint)
                view.setHintTextColor(ColorStateList.valueOf(fieldHint))
                view.setBoxStrokeColorStateList(
                    ColorStateList.valueOf(ensureContrast(readableAccentOn(fieldBackground, palette), fieldBackground, palette, minContrast = 3.2))
                )
                view.placeholderTextColor = ColorStateList.valueOf(fieldHint)
                view.counterTextColor = ColorStateList.valueOf(fieldHint)
                view.counterOverflowTextColor = ColorStateList.valueOf(ensureContrast(palette.danger, fieldBackground, palette))
                view.setHelperTextColor(ColorStateList.valueOf(fieldHint))
                view.setErrorTextColor(ColorStateList.valueOf(ensureContrast(palette.danger, fieldBackground, palette)))
                view.editText?.setTextColor(fieldText)
                view.editText?.setHintTextColor(fieldHint)
            }
            is TextInputEditText, is EditText -> {
                val inputBackground = resolveTextSurface(view, palette)
                (view as TextView).setTextColor(adaptiveTextColor(inputBackground, palette))
                view.setHintTextColor(adaptiveMutedTextColor(inputBackground, palette))
            }
            is LinearProgressIndicator -> {
                view.setIndicatorColor(palette.secondary)
                view.trackColor = ColorUtils.blendARGB(palette.surface, palette.textMuted, 0.18f)
            }
            is ProgressBar -> {
                view.indeterminateTintList = ColorStateList.valueOf(palette.primary)
                view.progressTintList = ColorStateList.valueOf(palette.primary)
            }
            is ScrollView -> {
                bindMistAwareScroll(view)
            }
            is CompoundButton -> {
                applyCompoundButtonColors(view, palette)
            }
            is SwitchMaterial -> {
                val switchText = adaptiveTextColor(resolveTextSurface(view, palette), palette)
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                )
                view.setTextColor(switchText)
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
                val buttonBackground = resolveEffectiveBackground(view, palette)
                if (view.background is GradientDrawable || view.background is ColorDrawable) {
                    view.backgroundTintList = ColorStateList.valueOf(palette.card)
                }
                view.imageTintList = ColorStateList.valueOf(adaptiveTextColor(buttonBackground, palette))
            }
            is ShapeableImageView -> {
                if (view.background is ColorDrawable) {
                    view.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 26))
                }
                if (view.drawable == null) {
                    view.background = gradientBackground(palette)
                } else if (shouldTintDrawable(view.drawable)) {
                    view.imageTintList = ColorStateList.valueOf(readableAccentOn(resolveEffectiveBackground(view, palette), palette))
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
            if (view.background == null || view.background is ColorDrawable || view.background is GradientDrawable) {
                if (view !is MaterialCardView &&
                    view !is NavigationView &&
                    view !is BottomNavigationView &&
                    !hasAncestorOfType<MaterialCardView>(view)
                ) {
                    applyContainerBackground(view, palette)
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

    private fun applyContainerBackground(view: View, palette: GeneratedPalette) {
        when (val background = view.background) {
            is GradientDrawable -> {
                val updated = ((background.constantState?.newDrawable()?.mutate()) as? GradientDrawable)
                    ?: (background.mutate() as GradientDrawable)
                updated.setColor(
                    if (view is ScrollView) palette.card else palette.surface
                )
                view.background = updated
            }
            else -> {
                view.setBackgroundColor(if (view is ScrollView) palette.card else palette.surface)
            }
        }
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
        val background = resolveTextSurface(button, palette)
        val textColor = adaptiveTextColor(background, palette)
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
        button.setTextColor(textColor)
    }

    private fun applyChipColors(chip: Chip, palette: GeneratedPalette) {
        val checkedBackground = ColorUtils.setAlphaComponent(palette.primary, 230)
        val uncheckedBackground = ColorUtils.blendARGB(palette.card, palette.surface, 0.35f)
        val checkedText = readableTextOn(checkedBackground, palette, palette.onPrimary)
        val uncheckedText = adaptiveTextColor(uncheckedBackground, palette)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        chip.chipBackgroundColor = ColorStateList(
            states,
            intArrayOf(
                checkedBackground,
                uncheckedBackground
            )
        )
        chip.setTextColor(
            ColorStateList(
                states,
                intArrayOf(checkedText, uncheckedText)
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
        val resolvedBackground = resolveEffectiveBackground(view, palette)
        val targetTint = readableAccentOn(resolvedBackground, palette)
        val tintList = view.imageTintList
        if (shouldTintDrawable(view.drawable) || tintList != null || background is GradientDrawable || background is ColorDrawable) {
            view.imageTintList = ColorStateList.valueOf(targetTint)
        }
    }

    private fun shouldTintDrawable(drawable: Drawable?): Boolean {
        return drawable != null && drawable !is BitmapDrawable
    }

    private fun applyTextColor(textView: TextView, palette: GeneratedPalette) {
        val background = resolveTextSurface(textView, palette)
        textView.setTextColor(adaptiveTextColor(background, palette))
        textView.setLinkTextColor(ensureContrast(readableAccentOn(background, palette), background, palette))
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

    private fun readableAccentOn(background: Int?, palette: GeneratedPalette): Int {
        val resolvedBackground = background ?: palette.surface
        val accentCandidates = listOf(
            palette.primary,
            palette.secondary,
            palette.accent,
            palette.text,
            palette.onSurface,
            palette.onCard
        ).distinct()
        return accentCandidates
            .filter { ColorUtils.calculateContrast(it, resolvedBackground) >= 4.5 }
            .maxByOrNull { ColorUtils.calculateContrast(it, resolvedBackground) }
            ?: readableTextOn(resolvedBackground, palette, palette.text)
    }

    fun adaptiveTextColorForView(view: View, palette: GeneratedPalette = currentPalette(view.context)): Int {
        return adaptiveTextColor(resolveTextSurface(view, palette), palette)
    }

    fun adaptiveMutedTextColorForView(view: View, palette: GeneratedPalette = currentPalette(view.context)): Int {
        return adaptiveMutedTextColor(resolveTextSurface(view, palette), palette)
    }

    private fun findNearestSolidBackground(view: View): Int? {
        var current: View? = view
        while (current != null) {
            drawableColor(current.background)?.let { return it }
            current = current.parent as? View
        }
        return null
    }

    private fun resolveTextSurface(view: View, palette: GeneratedPalette): Int {
        val textInputParent = generateSequence(view.parent as? View) { it.parent as? View }
            .filterIsInstance<TextInputLayout>()
            .firstOrNull()
        if (textInputParent != null && textInputParent.boxBackgroundColor != Color.TRANSPARENT) {
            return compositeOnSurface(textInputParent.boxBackgroundColor, palette)
        }
        return resolveEffectiveBackground(view, palette)
    }

    private fun resolveEffectiveBackground(view: View, palette: GeneratedPalette): Int {
        var current: View? = view
        while (current != null) {
            drawableColor(current.background)?.let { return compositeOnSurface(it, palette) }
            current = current.parent as? View
        }
        return when (view) {
            is MaterialCardView, is TextInputLayout -> palette.card
            else -> palette.surface
        }
    }

    private fun resolveFieldBackground(view: View, palette: GeneratedPalette): Int {
        val surrounding = resolveEffectiveBackground(view.parent as? View ?: view, palette)
        val targetCard = if (isDarkPalette(palette)) {
            ColorUtils.blendARGB(palette.card, palette.surface, 0.18f)
        } else {
            ColorUtils.blendARGB(palette.card, palette.primary, 0.06f)
        }
        return ColorUtils.blendARGB(surrounding, targetCard, if (isDarkPalette(palette)) 0.88f else 0.8f)
    }

    private fun adaptiveTextColor(background: Int, palette: GeneratedPalette): Int {
        return readableTextOn(background, palette, palette.text)
    }

    private fun adaptiveMutedTextColor(background: Int, palette: GeneratedPalette): Int {
        val base = adaptiveTextColor(background, palette)
        listOf(0.2f, 0.28f, 0.36f, 0.44f).forEach { blend ->
            val muted = ColorUtils.blendARGB(base, background, blend)
            if (ColorUtils.calculateContrast(muted, background) >= 3.2) {
                return muted
            }
        }
        return base
    }

    private fun ensureContrast(candidate: Int, background: Int, palette: GeneratedPalette, minContrast: Double = 4.5): Int {
        return if (ColorUtils.calculateContrast(candidate, background) >= minContrast) {
            candidate
        } else {
            readableTextOn(background, palette, candidate)
        }
    }

    private fun drawableColor(drawable: Drawable?): Int? {
        return when (drawable) {
            is ColorDrawable -> drawable.color
            is GradientDrawable -> {
                drawable.color?.defaultColor
                    ?: drawable.colors?.takeIf { it.isNotEmpty() }?.let(::averageColor)
            }
            else -> null
        }
    }

    private fun averageColor(colors: IntArray): Int {
        val count = colors.size.coerceAtLeast(1)
        val alpha = colors.sumOf { Color.alpha(it) } / count
        val red = colors.sumOf { Color.red(it) } / count
        val green = colors.sumOf { Color.green(it) } / count
        val blue = colors.sumOf { Color.blue(it) } / count
        return Color.argb(alpha, red, green, blue)
    }

    private fun compositeOnSurface(color: Int, palette: GeneratedPalette): Int {
        return if (Color.alpha(color) < 255) {
            ColorUtils.compositeColors(color, palette.surface)
        } else {
            color
        }
    }

    private fun isDarkPalette(palette: GeneratedPalette): Boolean {
        return ColorUtils.calculateLuminance(palette.surface) < 0.3
    }

    private inline fun <reified T : View> hasAncestorOfType(view: View): Boolean {
        var current = view.parent as? View
        while (current != null) {
            if (current is T) return true
            current = current.parent as? View
        }
        return false
    }

    private fun applyDialogButton(button: TextView, palette: GeneratedPalette, filled: Boolean) {
        val density = button.resources.displayMetrics.density
        val background = if (filled) {
            palette.primary
        } else {
            ColorUtils.blendARGB(palette.card, palette.surface, 0.28f)
        }
        val textColor = readableTextOn(
            background = background,
            palette = palette,
            fallback = if (filled) palette.onPrimary else palette.text
        )
        val strokeColor = if (filled) {
            palette.primaryDark
        } else {
            ColorUtils.setAlphaComponent(readableAccentOn(background, palette), 92)
        }
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * density
            setColor(background)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), strokeColor)
        }
        button.setTextColor(textColor)
        button.compoundDrawableTintList = ColorStateList.valueOf(textColor)
        button.minHeight = (40f * density).toInt()
        val horizontalPadding = (18f * density).toInt()
        val verticalPadding = (10f * density).toInt()
        button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
    }

    private fun applyPlainButtonColors(button: Button, palette: GeneratedPalette) {
        val background = findNearestSolidBackground(button) ?: palette.surface
        val buttonBackground = if (ColorUtils.calculateContrast(palette.primary, background) >= 2.2) {
            palette.primary
        } else {
            readableAccentOn(background, palette)
        }
        val textColor = readableTextOn(buttonBackground, palette, palette.onPrimary)
        button.backgroundTintList = ColorStateList.valueOf(buttonBackground)
        button.setTextColor(textColor)
    }

    private fun bindMistAwareScroll(scrollView: ScrollView) {
        if (scrollView.getTag(R.id.tag_mist_scroll_bound) == true) return
        scrollView.setTag(R.id.tag_mist_scroll_bound, true)
        val handler = Handler(Looper.getMainLooper())
        val reenable = Runnable { setNearestMistEnabled(scrollView, true) }

        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    handler.removeCallbacks(reenable)
                    setNearestMistEnabled(scrollView, false)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(reenable)
                    handler.postDelayed(reenable, MIST_SCROLL_REENABLE_DELAY_MS)
                }
            }
            false
        }
        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            handler.removeCallbacks(reenable)
            setNearestMistEnabled(scrollView, false)
            handler.postDelayed(reenable, MIST_SCROLL_REENABLE_DELAY_MS)
        }
        scrollView.doOnAttach {
            scrollView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    handler.removeCallbacks(reenable)
                    v.removeOnAttachStateChangeListener(this)
                }
            })
        }
    }

    private fun setNearestMistEnabled(view: View, enabled: Boolean) {
        val root = view.rootView as? ViewGroup ?: return
        root.children.forEach { child ->
            if (child is MistBorderView) {
                child.setAnimationsEnabled(enabled)
            }
        }
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
                UserPreferences.saveThemeSeedColor(context, settings.themeSeedColor)
                UserPreferences.applyThemeMode(settings.themeMode)
                onComplete?.invoke()
            },
            onFailure = {
                onComplete?.invoke()
            }
        )
    }
}

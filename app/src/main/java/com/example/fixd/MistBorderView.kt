package com.example.fixd

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class MistBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    companion object {
        private const val FULL_CYCLE = (Math.PI * 2.0).toFloat()
    }


    private val leftPath = Path()
    private val rightPath = Path()
    private val topPath = Path()
    private val bottomPath = Path()
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var animationProgress = 0f
    private var palette: GeneratedPalette? = null
    private var animator: ValueAnimator? = null
    private var animationsEnabled = true
    private val density = resources.displayMetrics.density

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        alpha = 0.82f
    }

    fun applyPalette(palette: GeneratedPalette) {
        this.palette = palette
        updatePaints()
        invalidate()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        if (animationsEnabled == enabled) return
        animationsEnabled = enabled
        if (enabled) {
            alpha = 0.82f
            startAnimation()
        } else {
            animator?.cancel()
            animator = null
            alpha = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaints()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        buildVerticalMist(leftPath, leftSide = true)
        buildVerticalMist(rightPath, leftSide = false)
        buildHorizontalMist(topPath, topSide = true)
        buildHorizontalMist(bottomPath, topSide = false)
        canvas.drawPath(leftPath, leftPaint)
        canvas.drawPath(rightPath, rightPaint)
        canvas.drawPath(topPath, topPaint)
        canvas.drawPath(bottomPath, bottomPaint)

        val inset = edgeThickness() * 0.68f
        canvas.drawRoundRect(
            inset,
            inset,
            width - inset,
            height - inset,
            32f * resources.displayMetrics.density,
            32f * resources.displayMetrics.density,
            glowPaint
        )
    }

    private fun startAnimation() {
        if (!animationsEnabled) return
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun updatePaints() {
        val palette = palette ?: return
        if (width == 0 || height == 0) return

        val darkPalette = isDarkPalette(palette)
        val mistPrimary = if (darkPalette) palette.primary else darkenForLightMode(palette.primary)
        val mistSecondary = if (darkPalette) palette.secondary else darkenForLightMode(palette.secondary)
        val mistAccent = if (darkPalette) palette.accent else darkenForLightMode(palette.accent)
        val mistA = ColorUtils.setAlphaComponent(mistPrimary, if (darkPalette) 78 else 188)
        val mistB = ColorUtils.setAlphaComponent(mistSecondary, if (darkPalette) 60 else 152)
        val mistC = ColorUtils.setAlphaComponent(mistAccent, if (darkPalette) 46 else 124)
        val clear = Color.TRANSPARENT
        val thickness = edgeThickness()

        leftPaint.shader = LinearGradient(
            0f, 0f, thickness, 0f,
            intArrayOf(mistA, mistA, mistB, mistC, clear),
            floatArrayOf(0f, 0.14f, 0.38f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        rightPaint.shader = LinearGradient(
            width.toFloat(), 0f, width - thickness, 0f,
            intArrayOf(mistA, mistA, mistB, mistC, clear),
            floatArrayOf(0f, 0.14f, 0.38f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        topPaint.shader = LinearGradient(
            0f, 0f, 0f, thickness,
            intArrayOf(mistA, mistA, mistB, mistC, clear),
            floatArrayOf(0f, 0.14f, 0.38f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        bottomPaint.shader = LinearGradient(
            0f, height.toFloat(), 0f, height - thickness,
            intArrayOf(mistA, mistA, mistB, mistC, clear),
            floatArrayOf(0f, 0.14f, 0.38f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        leftPaint.maskFilter = BlurMaskFilter(16f * density, BlurMaskFilter.Blur.NORMAL)
        rightPaint.maskFilter = BlurMaskFilter(16f * density, BlurMaskFilter.Blur.NORMAL)
        topPaint.maskFilter = BlurMaskFilter(16f * density, BlurMaskFilter.Blur.NORMAL)
        bottomPaint.maskFilter = BlurMaskFilter(16f * density, BlurMaskFilter.Blur.NORMAL)
        glowPaint.color = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(mistPrimary, mistAccent, 0.38f),
            if (darkPalette) 72 else 148
        )
        glowPaint.strokeWidth = 1.2f * density
        glowPaint.maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private fun buildVerticalMist(path: Path, leftSide: Boolean) {
        path.reset()
        val thickness = edgeThickness()
        val amplitude = thickness * 0.32f
        val step = (height / 18f).coerceAtLeast(16f * density)
        val baseX = if (leftSide) 0f else width.toFloat()
        val upwardDrift = animationProgress * 2f
        val detailDrift = animationProgress * 4f

        path.moveTo(baseX, 0f)
        path.lineTo(baseX, 0f)
        var y = -step
        while (y <= height + step) {
            val progress = (y / height).coerceIn(0f, 1f)
            val taper = edgeEnvelope(progress)
            val currentThickness = thickness * taper
            val wavePosition = progress * 2.35f
            val primaryWave = sin((wavePosition - upwardDrift) * FULL_CYCLE)
            val secondaryWave = sin(((wavePosition * 1.9f) - detailDrift) * FULL_CYCLE + 0.45f) * 0.24f
            val offset = (primaryWave + secondaryWave) * amplitude * taper
            val innerX = if (leftSide) {
                (currentThickness + offset).coerceIn(0f, thickness * 1.65f)
            } else {
                (width - currentThickness - offset).coerceIn(width - thickness * 1.65f, width.toFloat())
            }
            path.lineTo(innerX, y)
            y += step
        }
        path.lineTo(baseX, height.toFloat())
        path.close()
    }

    private fun buildHorizontalMist(path: Path, topSide: Boolean) {
        path.reset()
        val thickness = edgeThickness()
        val amplitude = thickness * 0.2f
        val step = (width / 22f).coerceAtLeast(16f * density)
        val baseY = if (topSide) 0f else height.toFloat()
        val sidewaysDrift = animationProgress * 1.65f
        val detailDrift = animationProgress * 3.1f

        path.moveTo(0f, baseY)
        path.lineTo(0f, baseY)
        var x = -step
        while (x <= width + step) {
            val progress = (x / width).coerceIn(0f, 1f)
            val taper = horizontalCornerEnvelope(progress)
            val currentThickness = thickness * taper
            val wavePosition = progress * 2.1f
            val primaryWave = sin((wavePosition - sidewaysDrift) * FULL_CYCLE + 0.9f)
            val secondaryWave = sin(((wavePosition * 2.05f) - detailDrift) * FULL_CYCLE + 0.2f) * 0.2f
            val offset = (primaryWave + secondaryWave) * amplitude * taper
            val innerY = if (topSide) {
                (currentThickness + offset).coerceIn(0f, thickness * 1.45f)
            } else {
                (height - currentThickness - offset).coerceIn(height - thickness * 1.45f, height.toFloat())
            }
            path.lineTo(x, innerY)
            x += step
        }
        path.lineTo(width.toFloat(), baseY)
        path.close()
    }

    private fun edgeEnvelope(progress: Float): Float {
        val centered = sin(progress * PI).toFloat().coerceIn(0f, 1f)
        return 0.34f + centered.pow(0.72f) * 0.66f
    }

    private fun horizontalCornerEnvelope(progress: Float): Float {
        val distanceToNearestCorner = minOf(progress, 1f - progress)
        val cornerReach = 0.18f
        val normalized = (1f - (distanceToNearestCorner / cornerReach)).coerceIn(0f, 1f)
        return normalized.pow(1.85f)
    }

    private fun edgeThickness(): Float {
        return density * if ((palette?.let(::isDarkPalette) == true)) 42f else 36f
    }

    private fun darkenForLightMode(color: Int): Int {
        return ColorUtils.blendARGB(color, Color.BLACK, 0.64f)
    }

    private fun isDarkPalette(palette: GeneratedPalette): Boolean {
        return ColorUtils.calculateLuminance(palette.surface) < 0.3
    }
}

package com.example.fixd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var selectedColor: Int = ThemePaletteManager.DEFAULT_SEED_COLOR
        set(value) {
            field = value
            updateSelectionFromColor(value)
            invalidate()
        }

    var onColorChanged: ((Int) -> Unit)? = null

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * resources.displayMetrics.density
        color = Color.argb(120, 0, 0, 0)
    }

    private var wheelBitmap: Bitmap? = null
    private var currentHue = 0f
    private var currentSaturation = 1f

    init {
        updateSelectionFromColor(selectedColor)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildWheelBitmap(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        wheelBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, wheelPaint)
        }

        val radius = min(width, height) / 2f
        val selectorRadius = radius * currentSaturation
        val angle = Math.toRadians(currentHue.toDouble())
        val cx = width / 2f + (selectorRadius * kotlin.math.cos(angle)).toFloat()
        val cy = height / 2f + (selectorRadius * kotlin.math.sin(angle)).toFloat()
        canvas.drawCircle(cx, cy, 16f * resources.displayMetrics.density, selectorShadowPaint)
        canvas.drawCircle(cx, cy, 16f * resources.displayMetrics.density, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updateFromPoint(event.x, event.y)
                true
            }
            else -> super.onTouchEvent(event)
        }
        if (handled) parent?.requestDisallowInterceptTouchEvent(true)
        return handled
    }

    private fun updateFromPoint(x: Float, y: Float) {
        val radius = min(width, height) / 2f
        val dx = x - width / 2f
        val dy = y - height / 2f
        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
        currentHue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
        currentSaturation = if (radius == 0f) 0f else (distance / radius).coerceIn(0f, 1f)
        selectedColor = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, 1f))
        onColorChanged?.invoke(selectedColor)
    }

    private fun updateSelectionFromColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0]
        currentSaturation = hsv[1]
    }

    private fun buildWheelBitmap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val radius = min(w, h) / 2f
        for (x in 0 until w) {
            for (y in 0 until h) {
                val dx = x - w / 2f
                val dy = y - h / 2f
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > radius) {
                    bitmap.setPixel(x, y, Color.TRANSPARENT)
                } else {
                    val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                    val saturation = (distance / radius).coerceIn(0f, 1f)
                    bitmap.setPixel(x, y, Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                }
            }
        }
        wheelBitmap = bitmap
    }
}

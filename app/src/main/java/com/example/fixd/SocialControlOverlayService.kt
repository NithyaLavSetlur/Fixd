package com.example.fixd

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class SocialControlOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var quickSettingsView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var settingsParams: WindowManager.LayoutParams? = null
    private var settings = SocialControlSettings()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = SocialControlPreferences.load(this)
        if (settings.floatingBubbleEnabled) {
            showBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = SocialControlPreferences.load(this)
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> {
                hideQuickSettings()
                hideBubble()
            }
            ACTION_SHOW_QUICK_SETTINGS -> {
                showBubble()
                showQuickSettings()
            }
            ACTION_HIDE_QUICK_SETTINGS -> hideQuickSettings()
            ACTION_TOGGLE_QUICK_SETTINGS -> {
                showBubble()
                if (quickSettingsView == null) showQuickSettings() else hideQuickSettings()
            }
            ACTION_REFRESH_SETTINGS -> refreshOverlay()
            else -> if (settings.floatingBubbleEnabled) showBubble()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideQuickSettings()
        hideBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubbleView != null || !SocialControlManager.canDrawOverlays(this)) return
        val palette = ThemePaletteManager.currentPalette(this)
        val iconSize = 132
        val bubbleCard = CardView(this).apply {
            radius = 36f
            cardElevation = 18f
            useCompatPadding = true
            alpha = 0.98f
            setCardBackgroundColor(ColorUtils.setAlphaComponent(palette.surface, 244))
            addView(
                ImageView(context).apply {
                    setImageResource(R.mipmap.ic_launcher_round)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                    setPadding(14, 14, 14, 14)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                }
            )
            setOnTouchListener(BubbleTouchListener())
        }
        val params = baseOverlayLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultBubbleX()
            y = 360
        }
        bubbleView = bubbleCard
        bubbleParams = params
        windowManager.addView(bubbleCard, params)
    }

    private fun hideBubble() {
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
            bubbleView = null
            bubbleParams = null
        }
    }

    private fun showQuickSettings() {
        if (quickSettingsView != null || !SocialControlManager.canDrawOverlays(this)) return
        val palette = ThemePaletteManager.currentPalette(this)
        val card = CardView(this).apply {
            radius = 28f
            cardElevation = 18f
            useCompatPadding = true
            alpha = 0.94f
            setCardBackgroundColor(ColorUtils.setAlphaComponent(palette.card, 236))
            addView(buildControlPanel())
        }
        val params = baseOverlayLayoutParams(
            width = minOf(screenWidth() - 96, 760),
            height = WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        quickSettingsView = card
        settingsParams = params
        updateQuickSettingsPosition()
        windowManager.addView(card, params)
    }

    private fun hideQuickSettings() {
        quickSettingsView?.let {
            runCatching { windowManager.removeView(it) }
            quickSettingsView = null
            settingsParams = null
        }
    }

    private fun refreshOverlay() {
        if (!settings.floatingBubbleEnabled) {
            hideQuickSettings()
            hideBubble()
            return
        }
        if (bubbleView == null) {
            showBubble()
        }
        if (quickSettingsView != null) {
            hideQuickSettings()
            showQuickSettings()
        }
    }

    private fun buildControlPanel(): View {
        val palette = ThemePaletteManager.currentPalette(this)
        val textColor = ThemePaletteManager.readableTextColorOn(palette.card, palette)
        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 24, 28, 24)
                    addView(panelTitle("Fixd social controls"))
                    addView(panelSubtitle("Edit all supported social restrictions from the floating Fixd icon."))
                    addView(panelSection("General"))
                    addView(panelSwitch("Enable device control", settings.appControlEnabled) {
                        updateSettings(settings.copy(appControlEnabled = it))
                    })
                    addView(panelSwitch("Show floating icon", settings.floatingBubbleEnabled) {
                        updateSettings(settings.copy(floatingBubbleEnabled = it))
                    })
                    addView(panelSection("Instagram"))
                    addView(panelSwitch("Disable Reels", settings.instagramBlockReels) {
                        updateSettings(settings.copy(instagramBlockReels = it))
                    })
                    addView(TextView(context).apply {
                        text = "Feed browsing stays allowed. Reels opened from the feed are sent to Messages, and reels opened from chats are closed back to the chat thread."
                        textSize = 12f
                        alpha = 0.72f
                        setPadding(0, 4, 0, 0)
                        setTextColor(textColor)
                    })
                    addView(panelSection("YouTube"))
                    addView(panelSwitch("Disable Shorts", settings.youtubeBlockShorts) {
                        updateSettings(settings.copy(youtubeBlockShorts = it))
                    })
                    addView(TextView(context).apply {
                        text = "Blocked Shorts tabs and opened Shorts are sent back to YouTube Home. Normal Home scrolling is allowed."
                        textSize = 12f
                        alpha = 0.72f
                        setPadding(0, 4, 0, 0)
                        setTextColor(textColor)
                    })
                    addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            16
                        )
                    })
                    addView(
                        Button(context).apply {
                            text = "Close"
                            setOnClickListener { hideQuickSettings() }
                        }
                    )
                }
            )
        }
    }

    private fun panelTitle(text: String): View {
        val palette = ThemePaletteManager.currentPalette(this)
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 0, 0, 16)
            setTextColor(ThemePaletteManager.readableTextColorOn(palette.card, palette))
        }
    }

    private fun panelSubtitle(text: String): View {
        val palette = ThemePaletteManager.currentPalette(this)
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            alpha = 0.72f
            setPadding(0, 0, 0, 14)
            setTextColor(ThemePaletteManager.readableTextColorOn(palette.card, palette))
        }
    }

    private fun panelSection(text: String): View {
        val palette = ThemePaletteManager.currentPalette(this)
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(0, 12, 0, 4)
            setTextColor(ThemePaletteManager.readableColorOn(palette.card, palette.primary, palette))
        }
    }

    private fun panelSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val palette = ThemePaletteManager.currentPalette(this)
        return Switch(this).apply {
            this.text = text
            isChecked = checked
            setTextColor(ThemePaletteManager.readableTextColorOn(palette.card, palette))
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
    }

    private fun updateSettings(updated: SocialControlSettings) {
        settings = updated
        SocialControlPreferences.save(this, updated)
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            UserSocialControlRepository.saveSettings(
                userId = userId,
                settings = updated,
                onSuccess = {},
                onFailure = {}
            )
        }
        if (!updated.floatingBubbleEnabled) {
            hideQuickSettings()
            hideBubble()
            stopSelf()
            return
        }
        refreshOverlay()
    }

    private fun updateBubblePosition(rawX: Float, rawY: Float) {
        val params = bubbleParams ?: return
        val bubble = bubbleView ?: return
        val width = bubble.width.takeIf { it > 0 } ?: bubble.measuredWidth.coerceAtLeast(132)
        val height = bubble.height.takeIf { it > 0 } ?: bubble.measuredHeight.coerceAtLeast(132)
        params.x = rawX.roundToInt().coerceIn(0, max(0, screenWidth() - width))
        params.y = rawY.roundToInt().coerceIn(100, max(100, screenHeight() - height - 100))
        windowManager.updateViewLayout(bubble, params)
        updateQuickSettingsPosition()
    }

    private fun snapBubbleToEdge() {
        val params = bubbleParams ?: return
        val bubble = bubbleView ?: return
        val width = bubble.width.takeIf { it > 0 } ?: bubble.measuredWidth.coerceAtLeast(132)
        val leftX = 16
        val rightX = max(16, screenWidth() - width - 16)
        params.x = if (params.x + (width / 2) < screenWidth() / 2) leftX else rightX
        windowManager.updateViewLayout(bubble, params)
        updateQuickSettingsPosition()
    }

    private fun updateQuickSettingsPosition() {
        val panel = quickSettingsView ?: return
        val panelParams = settingsParams ?: return
        val bubble = bubbleView ?: return
        val bubbleLayout = bubbleParams ?: return
        val bubbleWidth = bubble.width.takeIf { it > 0 } ?: bubble.measuredWidth.coerceAtLeast(132)
        val bubbleHeight = bubble.height.takeIf { it > 0 } ?: bubble.measuredHeight.coerceAtLeast(132)
        val panelWidth = panel.width.takeIf { it > 0 } ?: panel.measuredWidth.coerceAtLeast(640)
        val margin = 20
        val openOnRight = bubbleLayout.x < (screenWidth() / 2)
        val x = if (openOnRight) {
            (bubbleLayout.x + bubbleWidth + margin).coerceAtMost(max(24, screenWidth() - panelWidth - 24))
        } else {
            (bubbleLayout.x - panelWidth - margin).coerceAtLeast(24)
        }
        val y = bubbleLayout.y.coerceIn(80, max(80, screenHeight() - bubbleHeight - 120))
        panelParams.x = x
        panelParams.y = y
        if (panel.parent != null) {
            windowManager.updateViewLayout(panel, panelParams)
        }
    }

    private fun defaultBubbleX(): Int {
        val bubbleWidth = 148
        return max(16, screenWidth() - bubbleWidth - 16)
    }

    private fun screenWidth(): Int {
        return resources.displayMetrics.widthPixels
    }

    private fun screenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }

    private fun baseOverlayLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = bubbleParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    moved = moved || abs(deltaX) > 10 || abs(deltaY) > 10
                    updateBubblePosition(initialX + deltaX, initialY + deltaY)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (quickSettingsView == null) showQuickSettings() else hideQuickSettings()
                    } else {
                        snapBubbleToEdge()
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        const val ACTION_SHOW_BUBBLE = "fixd.social.overlay.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "fixd.social.overlay.HIDE_BUBBLE"
        const val ACTION_SHOW_QUICK_SETTINGS = "fixd.social.overlay.SHOW_QUICK_SETTINGS"
        const val ACTION_HIDE_QUICK_SETTINGS = "fixd.social.overlay.HIDE_QUICK_SETTINGS"
        const val ACTION_TOGGLE_QUICK_SETTINGS = "fixd.social.overlay.TOGGLE_QUICK_SETTINGS"
        const val ACTION_REFRESH_SETTINGS = "fixd.social.overlay.REFRESH_SETTINGS"
    }
}

package com.example.fixd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object SocialControlManager {
    const val EXTRA_TARGET_APP = "social_target_app"

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun overlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun accessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun showQuickSettings(context: Context, targetApp: String? = null) {
        if (!canDrawOverlays(context)) return
        context.startService(Intent(context, SocialControlOverlayService::class.java).apply {
            action = SocialControlOverlayService.ACTION_SHOW_QUICK_SETTINGS
            putExtra(EXTRA_TARGET_APP, targetApp)
        })
    }

    fun hideQuickSettings(context: Context) {
        context.startService(Intent(context, SocialControlOverlayService::class.java).apply {
            action = SocialControlOverlayService.ACTION_HIDE_QUICK_SETTINGS
        })
    }

    fun toggleQuickSettings(context: Context, targetApp: String? = null) {
        if (!canDrawOverlays(context)) return
        context.startService(Intent(context, SocialControlOverlayService::class.java).apply {
            action = SocialControlOverlayService.ACTION_TOGGLE_QUICK_SETTINGS
            putExtra(EXTRA_TARGET_APP, targetApp)
        })
    }

    fun showFloatingBubble(context: Context) {
        if (!canDrawOverlays(context)) return
        context.startService(Intent(context, SocialControlOverlayService::class.java).apply {
            action = SocialControlOverlayService.ACTION_SHOW_BUBBLE
        })
    }

    fun hideFloatingBubble(context: Context) {
        context.startService(Intent(context, SocialControlOverlayService::class.java).apply {
            action = SocialControlOverlayService.ACTION_HIDE_BUBBLE
        })
    }

    fun refreshOverlay(context: Context) {
        if (!canDrawOverlays(context)) return
        context.startService(Intent(context, SocialControlOverlayService::class.java).apply {
            action = SocialControlOverlayService.ACTION_REFRESH_SETTINGS
        })
    }
}

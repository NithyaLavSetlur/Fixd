package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

data class WidgetColors(
    val surface: Int,
    val title: Int,
    val body: Int,
    val muted: Int
)

object WidgetSupport {
    fun updateAll(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        buildRemoteViews: (Context) -> RemoteViews
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
        if (ids.isEmpty()) return
        val views = buildRemoteViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    fun colors(context: Context): WidgetColors {
        val palette = ThemePaletteManager.currentPalette(context)
        val surface = ColorUtils.blendARGB(palette.surface, palette.card, 0.55f)
        val title = ThemePaletteManager.readableColorOn(surface, palette.primary, palette)
        val body = ThemePaletteManager.readableTextColorOn(surface, palette)
        val muted = ColorUtils.blendARGB(body, surface, 0.45f)
        return WidgetColors(surface = surface, title = title, body = body, muted = muted)
    }

    fun dashboardPendingIntent(
        context: Context,
        requestCode: Int,
        area: ProblemArea,
        challengePage: ChallengePage? = null,
        openAction: String? = null
    ): PendingIntent {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, area.name)
            challengePage?.let { putExtra(DashboardActivity.EXTRA_OPEN_CHALLENGE_PAGE, it.name) }
            openAction?.let { putExtra(DashboardActivity.EXTRA_OPEN_ACTION, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

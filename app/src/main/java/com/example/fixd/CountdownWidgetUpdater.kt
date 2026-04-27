package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

object CountdownWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildRemoteViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val palette = ThemePaletteManager.currentPalette(context)
        val widgetSurface = ColorUtils.blendARGB(palette.surface, palette.card, 0.55f)
        val titleColor = ThemePaletteManager.readableColorOn(widgetSurface, palette.primary, palette)
        val bodyColor = ThemePaletteManager.readableTextColorOn(widgetSurface, palette)
        val mutedColor = ColorUtils.blendARGB(bodyColor, widgetSurface, 0.45f)
        val items = CountdownWidgetCache.get(context)
            .sortedBy { it.targetAt }
            .filter { it.targetAt > System.currentTimeMillis() }
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)

        views.setTextColor(R.id.countdownWidgetTitle, titleColor)
        views.setTextColor(R.id.countdownWidgetSubtitle, mutedColor)
        views.setTextColor(R.id.countdownWidgetOpenButton, android.graphics.Color.WHITE)

        bindCountdownRow(context, views, items.getOrNull(0), R.id.countdownRowOne, R.id.countdownNameOne, R.id.countdownTimerOne, bodyColor, mutedColor)
        bindCountdownRow(context, views, items.getOrNull(1), R.id.countdownRowTwo, R.id.countdownNameTwo, R.id.countdownTimerTwo, bodyColor, mutedColor)

        val extraCount = (items.size - 2).coerceAtLeast(0)
        views.setViewVisibility(R.id.countdownWidgetMore, if (extraCount > 0) View.VISIBLE else View.GONE)
        if (extraCount > 0) {
            views.setTextColor(R.id.countdownWidgetMore, mutedColor)
            views.setTextViewText(R.id.countdownWidgetMore, context.getString(R.string.countdown_widget_more, extraCount))
        }

        val openIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.COUNTDOWN.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            7110,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.countdownWidgetRoot, pendingIntent)
        views.setOnClickPendingIntent(R.id.countdownWidgetOpenButton, pendingIntent)
        return views
    }

    private fun bindCountdownRow(
        context: Context,
        views: RemoteViews,
        item: CountdownWidgetItem?,
        rowViewId: Int,
        titleViewId: Int,
        timerViewId: Int,
        bodyColor: Int,
        mutedColor: Int
    ) {
        if (item == null) {
            views.setViewVisibility(rowViewId, View.GONE)
            if (rowViewId == R.id.countdownRowOne) {
                views.setViewVisibility(rowViewId, View.VISIBLE)
                views.setTextViewText(titleViewId, context.getString(R.string.countdown_widget_empty))
                views.setTextColor(titleViewId, mutedColor)
                views.setViewVisibility(timerViewId, View.GONE)
            }
            return
        }

        views.setViewVisibility(rowViewId, View.VISIBLE)
        views.setViewVisibility(timerViewId, View.VISIBLE)
        views.setTextViewText(titleViewId, item.title)
        views.setTextColor(titleViewId, bodyColor)
        views.setTextColor(timerViewId, mutedColor)
        val base = SystemClock.elapsedRealtime() + (item.targetAt - System.currentTimeMillis())
        views.setChronometer(timerViewId, base, null, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            views.setChronometerCountDown(timerViewId, true)
        }
    }
}

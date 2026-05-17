package com.example.fixd

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

object CountdownWidgetUpdater {
    fun updateAll(context: Context) {
        WidgetSupport.updateAll(context, CountdownWidgetProvider::class.java, ::buildRemoteViews)
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val colors = WidgetSupport.colors(context)
        val items = CountdownWidgetCache.get(context)
            .sortedBy { it.targetAt }
            .filter { it.targetAt > System.currentTimeMillis() }
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)

        views.setTextColor(R.id.countdownWidgetTitle, colors.title)
        views.setTextColor(R.id.countdownWidgetSubtitle, colors.muted)
        views.setTextColor(R.id.countdownWidgetOpenButton, android.graphics.Color.WHITE)

        bindCountdownRow(context, views, items.getOrNull(0), R.id.countdownRowOne, R.id.countdownNameOne, R.id.countdownTimerOne, colors.body, colors.muted)
        bindCountdownRow(context, views, items.getOrNull(1), R.id.countdownRowTwo, R.id.countdownNameTwo, R.id.countdownTimerTwo, colors.body, colors.muted)

        val extraCount = (items.size - 2).coerceAtLeast(0)
        views.setViewVisibility(R.id.countdownWidgetMore, if (extraCount > 0) View.VISIBLE else View.GONE)
        if (extraCount > 0) {
            views.setTextColor(R.id.countdownWidgetMore, colors.muted)
            views.setTextViewText(R.id.countdownWidgetMore, context.getString(R.string.countdown_widget_more, extraCount))
        }

        val pendingIntent = WidgetSupport.dashboardPendingIntent(context, 7110, ProblemArea.COUNTDOWN)
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

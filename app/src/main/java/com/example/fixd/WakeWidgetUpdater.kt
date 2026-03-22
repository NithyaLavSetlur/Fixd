package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

object WakeWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WakeStreakWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ids.forEach { manager.updateAppWidget(it, buildRemoteViews(context)) }
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val submissions = WakeSubmissionCache.getSubmissions(context)
        val stats = WakeStatsCalculator.calculate(submissions)
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_wake_streak)
        remoteViews.setTextViewText(
            R.id.widgetStreakValue,
            when (stats.currentStreak) {
                1 -> context.getString(R.string.wake_streak_day)
                else -> context.getString(R.string.wake_streak_days, stats.currentStreak)
            }
        )
        remoteViews.setTextViewText(R.id.widgetStreakLabel, context.getString(R.string.widget_wake_streak_label))
        when (stats.todayStatus) {
            "awake" -> {
                remoteViews.setViewVisibility(R.id.widgetStatus, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widgetStatus, context.getString(R.string.wake_status_awake_symbol))
                remoteViews.setTextColor(R.id.widgetStatus, context.getColor(R.color.brand_success))
            }
            "asleep" -> {
                remoteViews.setViewVisibility(R.id.widgetStatus, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widgetStatus, context.getString(R.string.wake_status_asleep_symbol))
                remoteViews.setTextColor(R.id.widgetStatus, context.getColor(R.color.wake_status_asleep))
            }
            else -> {
                remoteViews.setViewVisibility(R.id.widgetStatus, View.INVISIBLE)
            }
        }

        val openWakeIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.WAKE_UP.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val createAlarmIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.WAKE_UP.name)
            putExtra(DashboardActivity.EXTRA_OPEN_ACTION, DashboardActivity.OPEN_ACTION_CREATE_ALARM)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        remoteViews.setOnClickPendingIntent(
            R.id.widgetRoot,
            PendingIntent.getActivity(context, 5010, openWakeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetCreateAlarmButton,
            PendingIntent.getActivity(context, 5011, createAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        return remoteViews
    }
}

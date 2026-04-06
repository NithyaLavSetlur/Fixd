package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WakeWidgetUpdater {
    private val widgetTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault())

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WakeStreakWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ids.forEach { manager.updateAppWidget(it, buildRemoteViews(context)) }
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val submissions = WakeSubmissionCache.getSubmissions(context)
        val stats = WakeStatsCalculator.calculate(submissions)
        val recentEntries = submissions
            .sortedByDescending { WakeSubmissionUi.primaryTimestamp(it) }
            .take(2)
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_wake_streak)
        remoteViews.setTextViewText(
            R.id.widgetStreakValue,
            when (stats.currentStreak) {
                1 -> context.getString(R.string.wake_streak_day)
                else -> context.getString(R.string.wake_streak_days, stats.currentStreak)
            }
        )
        remoteViews.setTextViewText(R.id.widgetStreakLabel, context.getString(R.string.widget_wake_streak_label))
        remoteViews.setTextViewText(R.id.widgetStreakCaption, context.getString(R.string.widget_streak_caption))
        remoteViews.setTextViewText(R.id.widgetRecentLabel, context.getString(R.string.widget_recent_label))
        when (stats.todayStatus) {
            "awake" -> {
                remoteViews.setViewVisibility(R.id.widgetStatus, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widgetStatus, context.getString(R.string.wake_history_status_awake))
                remoteViews.setTextColor(R.id.widgetStatus, context.getColor(R.color.brand_success))
                remoteViews.setTextViewText(R.id.widgetStatusDetail, context.getString(R.string.widget_status_ready))
            }
            "asleep" -> {
                remoteViews.setViewVisibility(R.id.widgetStatus, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widgetStatus, context.getString(R.string.wake_history_status_asleep))
                remoteViews.setTextColor(R.id.widgetStatus, context.getColor(R.color.wake_status_asleep))
                remoteViews.setTextViewText(R.id.widgetStatusDetail, context.getString(R.string.widget_status_retry))
            }
            else -> {
                remoteViews.setViewVisibility(R.id.widgetStatus, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widgetStatus, context.getString(R.string.wake_history_status_pending))
                remoteViews.setTextColor(R.id.widgetStatus, context.getColor(R.color.white))
                remoteViews.setTextViewText(R.id.widgetStatusDetail, context.getString(R.string.widget_status_pending))
            }
        }

        if (recentEntries.isNotEmpty()) {
            remoteViews.setTextViewText(R.id.widgetRecentPrimary, buildRecentLine(context, recentEntries[0]))
            remoteViews.setTextViewText(
                R.id.widgetRecentSecondary,
                if (recentEntries.size > 1) buildRecentLine(context, recentEntries[1]) else context.getString(R.string.widget_recent_empty_secondary)
            )
        } else {
            remoteViews.setTextViewText(R.id.widgetRecentPrimary, context.getString(R.string.widget_recent_empty_primary))
            remoteViews.setTextViewText(R.id.widgetRecentSecondary, context.getString(R.string.widget_recent_empty_secondary))
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

    private fun buildRecentLine(context: Context, submission: WakeSubmission): String {
        val timestamp = Instant.ofEpochMilli(WakeSubmissionUi.primaryTimestamp(submission))
            .atZone(ZoneId.systemDefault())
            .format(widgetTimeFormatter)
        val status = WakeSubmissionUi.wakeStatusLabel(context, submission)
        val type = WakeSubmissionUi.typeLabel(context, submission)
        return context.getString(
            R.string.widget_recent_entry,
            timestamp,
            WakeSubmissionUi.formatAlarmTime(submission.alarmHour, submission.alarmMinute),
            status,
            type
        )
    }
}

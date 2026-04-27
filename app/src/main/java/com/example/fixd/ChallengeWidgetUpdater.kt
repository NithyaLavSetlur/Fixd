package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

object ChallengeWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ChallengeWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildRemoteViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val summary = ChallengeWidgetCache.get(context)
        val palette = ThemePaletteManager.currentPalette(context)
        val widgetSurface = ColorUtils.blendARGB(palette.surface, palette.card, 0.55f)
        val titleColor = ThemePaletteManager.readableColorOn(widgetSurface, palette.primary, palette)
        val bodyColor = ThemePaletteManager.readableTextColorOn(widgetSurface, palette)
        val mutedColor = ColorUtils.blendARGB(bodyColor, widgetSurface, 0.45f)
        val views = RemoteViews(context.packageName, R.layout.widget_challenge_board)

        views.setTextColor(R.id.challengeWidgetTitle, titleColor)
        views.setTextColor(R.id.challengeWidgetLevel, bodyColor)
        views.setTextColor(R.id.challengeWidgetXp, mutedColor)
        views.setTextColor(R.id.challengeWidgetStreak, mutedColor)
        views.setTextColor(R.id.challengeWidgetMissionsLabel, mutedColor)
        views.setTextColor(R.id.challengeWidgetOpenButton, android.graphics.Color.WHITE)
        views.setTextColor(R.id.challengeWidgetMissionOne, bodyColor)
        views.setTextColor(R.id.challengeWidgetMissionTwo, bodyColor)
        views.setTextColor(R.id.challengeWidgetMissionThree, bodyColor)
        views.setTextViewText(R.id.challengeWidgetLevel, context.getString(R.string.challenge_widget_level, summary.level))
        views.setTextViewText(
            R.id.challengeWidgetXp,
            context.getString(R.string.challenge_widget_progress, summary.completedToday, summary.totalToday, summary.totalXp)
        )
        views.setTextViewText(
            R.id.challengeWidgetStreak,
            context.getString(R.string.challenge_widget_streak, summary.streak)
        )

        val missionViews = listOf(
            R.id.challengeWidgetMissionOne,
            R.id.challengeWidgetMissionTwo,
            R.id.challengeWidgetMissionThree
        )
        missionViews.forEachIndexed { index, viewId ->
            val mission = summary.missions.getOrNull(index)
            if (mission == null) {
                views.setViewVisibility(viewId, if (index == 0) View.VISIBLE else View.GONE)
                if (index == 0) {
                    views.setTextViewText(viewId, context.getString(R.string.challenge_widget_empty))
                    views.setTextColor(viewId, mutedColor)
                }
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(
                    viewId,
                    context.getString(R.string.challenge_widget_mission_line, mission.icon, mission.title, mission.xp)
                )
                views.setTextColor(viewId, bodyColor)
            }
        }

        val openBoardIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.CHALLENGES.name)
            putExtra(DashboardActivity.EXTRA_OPEN_CHALLENGE_PAGE, ChallengePage.BOARD.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            6010,
            openBoardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.challengeWidgetRoot, pendingIntent)
        views.setOnClickPendingIntent(R.id.challengeWidgetOpenButton, pendingIntent)
        return views
    }
}

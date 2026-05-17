package com.example.fixd

import android.content.Context
import android.view.View
import android.widget.RemoteViews

object ChallengeWidgetUpdater {
    fun updateAll(context: Context) {
        WidgetSupport.updateAll(context, ChallengeWidgetProvider::class.java, ::buildRemoteViews)
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val summary = ChallengeWidgetCache.get(context)
        val colors = WidgetSupport.colors(context)
        val views = RemoteViews(context.packageName, R.layout.widget_challenge_board)

        views.setTextColor(R.id.challengeWidgetTitle, colors.title)
        views.setTextColor(R.id.challengeWidgetLevel, colors.body)
        views.setTextColor(R.id.challengeWidgetXp, colors.muted)
        views.setTextColor(R.id.challengeWidgetStreak, colors.muted)
        views.setTextColor(R.id.challengeWidgetMissionsLabel, colors.muted)
        views.setTextColor(R.id.challengeWidgetOpenButton, android.graphics.Color.WHITE)
        views.setTextColor(R.id.challengeWidgetMissionOne, colors.body)
        views.setTextColor(R.id.challengeWidgetMissionTwo, colors.body)
        views.setTextColor(R.id.challengeWidgetMissionThree, colors.body)
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
                    views.setTextColor(viewId, colors.muted)
                }
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(
                    viewId,
                    context.getString(R.string.challenge_widget_mission_line, mission.icon, mission.title, mission.xp)
                )
                views.setTextColor(viewId, colors.body)
            }
        }

        val pendingIntent = WidgetSupport.dashboardPendingIntent(context, 6010, ProblemArea.CHALLENGES, ChallengePage.BOARD)
        views.setOnClickPendingIntent(R.id.challengeWidgetRoot, pendingIntent)
        views.setOnClickPendingIntent(R.id.challengeWidgetOpenButton, pendingIntent)
        return views
    }
}

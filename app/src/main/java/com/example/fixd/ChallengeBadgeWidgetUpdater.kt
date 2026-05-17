package com.example.fixd

import android.content.Context
import android.widget.RemoteViews

object ChallengeBadgeWidgetUpdater {
    fun updateAll(context: Context) {
        WidgetSupport.updateAll(context, ChallengeBadgeWidgetProvider::class.java, ::buildRemoteViews)
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val summary = ChallengeWidgetCache.get(context)
        val colors = WidgetSupport.colors(context)
        val views = RemoteViews(context.packageName, R.layout.widget_challenge_badges)

        views.setTextColor(R.id.challengeBadgesWidgetTitle, colors.title)
        views.setTextColor(R.id.challengeBadgesWidgetCount, colors.muted)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiOne, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiTwo, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiThree, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiFour, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetNameOne, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetNameTwo, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetNameThree, colors.body)
        views.setTextColor(R.id.challengeBadgesWidgetNameFour, colors.body)

        views.setTextViewText(
            R.id.challengeBadgesWidgetCount,
            context.getString(R.string.challenge_badges_widget_count, summary.badges.size)
        )

        bindBadge(views, summary.badges.getOrNull(0), R.id.challengeBadgesWidgetEmojiOne, R.id.challengeBadgesWidgetNameOne, context)
        bindBadge(views, summary.badges.getOrNull(1), R.id.challengeBadgesWidgetEmojiTwo, R.id.challengeBadgesWidgetNameTwo, context)
        bindBadge(views, summary.badges.getOrNull(2), R.id.challengeBadgesWidgetEmojiThree, R.id.challengeBadgesWidgetNameThree, context)
        bindBadge(views, summary.badges.getOrNull(3), R.id.challengeBadgesWidgetEmojiFour, R.id.challengeBadgesWidgetNameFour, context)

        val pendingIntent = WidgetSupport.dashboardPendingIntent(context, 6030, ProblemArea.CHALLENGES, ChallengePage.BADGES)
        views.setOnClickPendingIntent(R.id.challengeBadgesWidgetRoot, pendingIntent)
        return views
    }

    private fun bindBadge(
        views: RemoteViews,
        badge: ChallengeWidgetBadge?,
        emojiViewId: Int,
        nameViewId: Int,
        context: Context
    ) {
        if (badge == null) {
            views.setTextViewText(emojiViewId, "\u2606")
            views.setTextViewText(nameViewId, context.getString(R.string.challenge_badges_widget_empty))
        } else {
            views.setTextViewText(emojiViewId, badge.emoji)
            views.setTextViewText(nameViewId, badge.name)
        }
    }
}

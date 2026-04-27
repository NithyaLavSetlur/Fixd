package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

object ChallengeBadgeWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ChallengeBadgeWidgetProvider::class.java))
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
        val views = RemoteViews(context.packageName, R.layout.widget_challenge_badges)

        views.setTextColor(R.id.challengeBadgesWidgetTitle, titleColor)
        views.setTextColor(R.id.challengeBadgesWidgetCount, mutedColor)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiOne, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiTwo, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiThree, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetEmojiFour, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetNameOne, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetNameTwo, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetNameThree, bodyColor)
        views.setTextColor(R.id.challengeBadgesWidgetNameFour, bodyColor)

        views.setTextViewText(
            R.id.challengeBadgesWidgetCount,
            context.getString(R.string.challenge_badges_widget_count, summary.badges.size)
        )

        bindBadge(views, summary.badges.getOrNull(0), R.id.challengeBadgesWidgetEmojiOne, R.id.challengeBadgesWidgetNameOne, context)
        bindBadge(views, summary.badges.getOrNull(1), R.id.challengeBadgesWidgetEmojiTwo, R.id.challengeBadgesWidgetNameTwo, context)
        bindBadge(views, summary.badges.getOrNull(2), R.id.challengeBadgesWidgetEmojiThree, R.id.challengeBadgesWidgetNameThree, context)
        bindBadge(views, summary.badges.getOrNull(3), R.id.challengeBadgesWidgetEmojiFour, R.id.challengeBadgesWidgetNameFour, context)

        val openIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.CHALLENGES.name)
            putExtra(DashboardActivity.EXTRA_OPEN_CHALLENGE_PAGE, ChallengePage.BADGES.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            6030,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

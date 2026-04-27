package com.example.fixd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

object ChallengeFigureWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ChallengeFigureWidgetProvider::class.java))
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
        val views = RemoteViews(context.packageName, R.layout.widget_challenge_figures)

        views.setTextColor(R.id.challengeFiguresWidgetTitle, titleColor)
        views.setTextColor(R.id.challengeFiguresWidgetCount, mutedColor)
        views.setTextColor(R.id.challengeFiguresWidgetEmojiOne, bodyColor)
        views.setTextColor(R.id.challengeFiguresWidgetEmojiTwo, bodyColor)
        views.setTextColor(R.id.challengeFiguresWidgetEmojiThree, bodyColor)
        views.setTextColor(R.id.challengeFiguresWidgetCaptionOne, bodyColor)
        views.setTextColor(R.id.challengeFiguresWidgetCaptionTwo, bodyColor)
        views.setTextColor(R.id.challengeFiguresWidgetCaptionThree, bodyColor)

        views.setTextViewText(
            R.id.challengeFiguresWidgetCount,
            context.getString(R.string.challenge_figures_widget_count, summary.figures.size)
        )

        bindFigure(views, summary.figures.getOrNull(0), R.id.challengeFiguresWidgetEmojiOne, R.id.challengeFiguresWidgetCaptionOne, context)
        bindFigure(views, summary.figures.getOrNull(1), R.id.challengeFiguresWidgetEmojiTwo, R.id.challengeFiguresWidgetCaptionTwo, context)
        bindFigure(views, summary.figures.getOrNull(2), R.id.challengeFiguresWidgetEmojiThree, R.id.challengeFiguresWidgetCaptionThree, context)

        val openIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.CHALLENGES.name)
            putExtra(DashboardActivity.EXTRA_OPEN_CHALLENGE_PAGE, ChallengePage.GALLERY.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            6020,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.challengeFiguresWidgetRoot, pendingIntent)
        return views
    }

    private fun bindFigure(
        views: RemoteViews,
        figure: ChallengeWidgetFigure?,
        emojiViewId: Int,
        captionViewId: Int,
        context: Context
    ) {
        if (figure == null) {
            views.setTextViewText(emojiViewId, "\u2728")
            views.setTextViewText(captionViewId, context.getString(R.string.challenge_figures_widget_empty))
        } else {
            views.setTextViewText(emojiViewId, figure.emoji)
            views.setTextViewText(
                captionViewId,
                context.getString(R.string.challenge_figures_widget_item, figure.name, figure.level)
            )
        }
    }
}

package com.example.fixd

import android.content.Context
import android.widget.RemoteViews

object ChallengeFigureWidgetUpdater {
    fun updateAll(context: Context) {
        WidgetSupport.updateAll(context, ChallengeFigureWidgetProvider::class.java, ::buildRemoteViews)
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val summary = ChallengeWidgetCache.get(context)
        val colors = WidgetSupport.colors(context)
        val views = RemoteViews(context.packageName, R.layout.widget_challenge_figures)

        views.setTextColor(R.id.challengeFiguresWidgetTitle, colors.title)
        views.setTextColor(R.id.challengeFiguresWidgetCount, colors.muted)
        views.setTextColor(R.id.challengeFiguresWidgetEmojiOne, colors.body)
        views.setTextColor(R.id.challengeFiguresWidgetEmojiTwo, colors.body)
        views.setTextColor(R.id.challengeFiguresWidgetEmojiThree, colors.body)
        views.setTextColor(R.id.challengeFiguresWidgetCaptionOne, colors.body)
        views.setTextColor(R.id.challengeFiguresWidgetCaptionTwo, colors.body)
        views.setTextColor(R.id.challengeFiguresWidgetCaptionThree, colors.body)

        views.setTextViewText(
            R.id.challengeFiguresWidgetCount,
            context.getString(R.string.challenge_figures_widget_count, summary.figures.size)
        )

        bindFigure(views, summary.figures.getOrNull(0), R.id.challengeFiguresWidgetEmojiOne, R.id.challengeFiguresWidgetCaptionOne, context)
        bindFigure(views, summary.figures.getOrNull(1), R.id.challengeFiguresWidgetEmojiTwo, R.id.challengeFiguresWidgetCaptionTwo, context)
        bindFigure(views, summary.figures.getOrNull(2), R.id.challengeFiguresWidgetEmojiThree, R.id.challengeFiguresWidgetCaptionThree, context)

        val pendingIntent = WidgetSupport.dashboardPendingIntent(context, 6020, ProblemArea.CHALLENGES, ChallengePage.GALLERY)
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

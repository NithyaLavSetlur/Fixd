package com.example.fixd

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ChallengeEndOfDayReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!UserPreferences.isProblemDisplayed(context, ProblemArea.CHALLENGES)) {
            ChallengeEndOfDayReminderScheduler.cancel(context)
            return
        }
        ChallengeEndOfDayReminderScheduler.scheduleNext(context)

        val summary = ChallengeWidgetCache.get(context)
        if (summary.totalToday <= 0 || summary.completedToday >= summary.totalToday) return

        NotificationHelper.ensureChannels(context)
        val canNotify = NotificationHelper.canPostNotifications(context) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!canNotify) return

        val openIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.CHALLENGES.name)
            putExtra(DashboardActivity.EXTRA_OPEN_CHALLENGE_PAGE, ChallengePage.BOARD.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NotificationHelper.CHALLENGE_END_OF_DAY_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = context.getString(
            R.string.challenge_end_of_day_notification_body,
            summary.completedToday,
            summary.totalToday
        )
        val notification = NotificationCompat.Builder(context, NotificationHelper.GENERAL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.challenge_end_of_day_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(
            NotificationHelper.CHALLENGE_END_OF_DAY_NOTIFICATION_ID,
            notification
        )
    }
}

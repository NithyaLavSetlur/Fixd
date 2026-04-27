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

class CountdownReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannels(context)
        val countdownId = intent.getStringExtra(EXTRA_COUNTDOWN_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_COUNTDOWN_TITLE).orEmpty()
        if (countdownId.isBlank() || title.isBlank()) return

        val openIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.COUNTDOWN.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            countdownId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.GENERAL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.countdown_notification_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.countdown_notification_body)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val canNotify = NotificationHelper.canPostNotifications(context) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (canNotify) {
            NotificationManagerCompat.from(context).notify(NotificationHelper.COUNTDOWN_NOTIFICATION_ID_BASE + countdownId.hashCode(), notification)
        }
    }

    companion object {
        const val EXTRA_COUNTDOWN_ID = "countdown_id"
        const val EXTRA_COUNTDOWN_TITLE = "countdown_title"
        const val EXTRA_TARGET_AT = "countdown_target_at"
    }
}

package com.example.fixd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object CountdownReminderScheduler {
    fun schedule(context: Context, countdown: CountdownEntry) {
        if (countdown.id.isBlank() || countdown.notifyAt <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, countdown.id)
        val pendingIntent = pendingIntent(context, countdown)
        when {
            AlarmScheduler.canScheduleExactAlarms(context) -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, countdown.notifyAt, pendingIntent)
            }
            else -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, countdown.notifyAt, pendingIntent)
            }
        }
    }

    fun cancel(context: Context, countdownId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                countdownId.hashCode(),
                reminderIntent(context, countdownId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun pendingIntent(context: Context, countdown: CountdownEntry): PendingIntent {
        val intent = reminderIntent(context, countdown.id).apply {
            putExtra(CountdownReminderReceiver.EXTRA_COUNTDOWN_ID, countdown.id)
            putExtra(CountdownReminderReceiver.EXTRA_COUNTDOWN_TITLE, countdown.title)
            putExtra(CountdownReminderReceiver.EXTRA_TARGET_AT, countdown.targetAt)
        }
        return PendingIntent.getBroadcast(
            context,
            countdown.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderIntent(context: Context, countdownId: String): Intent {
        return Intent(context, CountdownReminderReceiver::class.java).apply {
            action = "com.example.fixd.COUNTDOWN_REMINDER.$countdownId"
            data = Uri.parse("fixd://countdown/$countdownId")
        }
    }
}

package com.example.fixd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Calendar

object ChallengeEndOfDayReminderScheduler {
    private const val REQUEST_CODE = 13001
    private const val REMINDER_HOUR = 20
    private const val REMINDER_MINUTE = 30

    fun scheduleNext(context: Context) {
        cancel(context)
        if (!UserPreferences.isProblemDisplayed(context, ProblemArea.CHALLENGES)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextReminderAtMillis()
        val pendingIntent = reminderPendingIntent(context)
        AlarmManagerSupport.scheduleAllowWhileIdle(context, alarmManager, triggerAt, pendingIntent)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(context))
    }

    private fun nextReminderAtMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, REMINDER_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ChallengeEndOfDayReminderReceiver::class.java).apply {
            action = "com.example.fixd.CHALLENGE_END_OF_DAY_REMINDER"
            data = Uri.parse("fixd://challenge-end-of-day-reminder")
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

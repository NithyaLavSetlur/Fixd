package com.example.fixd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object WakeFollowUpScheduler {
    const val FOLLOW_UP_DELAY_MS = 30L * 60L * 1000L

    fun schedule(
        context: Context,
        userId: String,
        submissionId: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context, userId, submissionId)
        )
    }

    fun cancel(context: Context, userId: String, submissionId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, userId, submissionId))
    }

    private fun pendingIntent(context: Context, userId: String, submissionId: String): PendingIntent {
        val intent = Intent(context, WakeFollowUpReceiver::class.java).apply {
            putExtra(WakeFollowUpReceiver.EXTRA_USER_ID, userId)
            putExtra(WakeFollowUpReceiver.EXTRA_SUBMISSION_ID, submissionId)
        }
        return PendingIntent.getBroadcast(
            context,
            submissionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

package com.example.fixd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object WakeFollowUpScheduler {
    const val FOLLOW_UP_DELAY_MS = 30L * 60L * 1000L
    const val FIRST_FOLLOW_UP_ATTEMPT = 1
    const val SECOND_FOLLOW_UP_ATTEMPT = 2

    fun schedule(
        context: Context,
        userId: String,
        submissionId: String,
        triggerAtMillis: Long,
        attempt: Int = FIRST_FOLLOW_UP_ATTEMPT
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context, userId, submissionId, attempt)
        )
    }

    fun cancel(context: Context, userId: String, submissionId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, userId, submissionId, FIRST_FOLLOW_UP_ATTEMPT))
        alarmManager.cancel(pendingIntent(context, userId, submissionId, SECOND_FOLLOW_UP_ATTEMPT))
    }

    private fun pendingIntent(context: Context, userId: String, submissionId: String, attempt: Int): PendingIntent {
        val intent = Intent(context, WakeFollowUpReceiver::class.java).apply {
            putExtra(WakeFollowUpReceiver.EXTRA_USER_ID, userId)
            putExtra(WakeFollowUpReceiver.EXTRA_SUBMISSION_ID, submissionId)
            putExtra(WakeFollowUpReceiver.EXTRA_ATTEMPT, attempt)
        }
        return PendingIntent.getBroadcast(
            context,
            (submissionId + ":" + attempt).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

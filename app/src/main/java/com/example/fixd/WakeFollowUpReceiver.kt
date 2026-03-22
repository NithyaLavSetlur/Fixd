package com.example.fixd

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class WakeFollowUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannels(context)
        val userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        val submissionId = intent.getStringExtra(EXTRA_SUBMISSION_ID).orEmpty()
        if (userId.isBlank() || submissionId.isBlank()) return

        val openAppIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.WAKE_UP.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            submissionId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val yesIntent = actionPendingIntent(context, submissionId, userId, WakeFollowUpActionReceiver.ACTION_YES)
        val noIntent = actionPendingIntent(context, submissionId, userId, WakeFollowUpActionReceiver.ACTION_NO)
        val notificationId = NotificationHelper.FOLLOW_UP_NOTIFICATION_ID_BASE + submissionId.hashCode()

        val notification = NotificationCompat.Builder(context, NotificationHelper.GENERAL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.wake_follow_up_title))
            .setContentText(context.getString(R.string.wake_follow_up_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.wake_follow_up_body)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.wake_follow_up_yes), yesIntent)
            .addAction(0, context.getString(R.string.wake_follow_up_no), noIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun actionPendingIntent(
        context: Context,
        submissionId: String,
        userId: String,
        action: String
    ): PendingIntent {
        val intent = Intent(context, WakeFollowUpActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_SUBMISSION_ID, submissionId)
        }
        return PendingIntent.getBroadcast(
            context,
            (submissionId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_USER_ID = "wake_follow_up_user_id"
        const val EXTRA_SUBMISSION_ID = "wake_follow_up_submission_id"
    }
}

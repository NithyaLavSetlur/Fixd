package com.example.fixd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class WakeFollowUpActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra(WakeFollowUpReceiver.EXTRA_USER_ID).orEmpty()
        val submissionId = intent.getStringExtra(WakeFollowUpReceiver.EXTRA_SUBMISSION_ID).orEmpty()
        if (userId.isBlank() || submissionId.isBlank()) return

        val wakeStatus = when (intent.action) {
            ACTION_YES -> "awake"
            ACTION_NO -> "asleep"
            else -> return
        }

        val pendingResult = goAsync()
        WakeFollowUpScheduler.cancel(context, userId, submissionId)
        AlarmRepository.updateSubmissionWakeStatus(
            userId = userId,
            submissionId = submissionId,
            wakeStatus = wakeStatus,
            onSuccess = {
                val cachedSubmission = WakeSubmissionCache.getSubmissions(context).firstOrNull { it.id == submissionId }
                if (cachedSubmission != null) {
                    WakeSubmissionCache.upsertSubmission(context, cachedSubmission.copy(wakeStatus = wakeStatus))
                    WakeWidgetUpdater.updateAll(context)
                }
                NotificationManagerCompat.from(context).cancel(notificationId(submissionId))
                val launchIntent = Intent(context, DashboardActivity::class.java).apply {
                    putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.WAKE_UP.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(launchIntent)
                pendingResult.finish()
            },
            onFailure = {
                pendingResult.finish()
            }
        )
    }

    private fun notificationId(submissionId: String): Int {
        return NotificationHelper.FOLLOW_UP_NOTIFICATION_ID_BASE + submissionId.hashCode()
    }

    companion object {
        const val ACTION_YES = "fixd.action.WAKE_FOLLOW_UP_YES"
        const val ACTION_NO = "fixd.action.WAKE_FOLLOW_UP_NO"
    }
}

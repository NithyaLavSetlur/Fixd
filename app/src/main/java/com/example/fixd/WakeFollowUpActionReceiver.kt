package com.example.fixd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class WakeFollowUpActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra(WakeFollowUpReceiver.EXTRA_USER_ID).orEmpty()
        val submissionId = intent.getStringExtra(WakeFollowUpReceiver.EXTRA_SUBMISSION_ID).orEmpty()
        val attempt = intent.getIntExtra(
            WakeFollowUpReceiver.EXTRA_ATTEMPT,
            WakeFollowUpScheduler.FIRST_FOLLOW_UP_ATTEMPT
        )
        if (userId.isBlank() || submissionId.isBlank()) return

        val pendingResult = goAsync()
        when (intent.action) {
            ACTION_YES -> {
                WakeFollowUpScheduler.cancel(context, userId, submissionId)
                persistWakeStatus(
                    context = context,
                    userId = userId,
                    submissionId = submissionId,
                    wakeStatus = "awake",
                    pendingResult = pendingResult
                )
            }
            ACTION_NO -> {
                if (attempt < WakeFollowUpScheduler.SECOND_FOLLOW_UP_ATTEMPT) {
                    WakeFollowUpScheduler.schedule(
                        context = context,
                        userId = userId,
                        submissionId = submissionId,
                        triggerAtMillis = System.currentTimeMillis() + WakeFollowUpScheduler.FOLLOW_UP_DELAY_MS,
                        attempt = WakeFollowUpScheduler.SECOND_FOLLOW_UP_ATTEMPT
                    )
                    NotificationManagerCompat.from(context).cancel(notificationId(submissionId))
                    openWakeArea(context)
                    pendingResult.finish()
                } else {
                    WakeFollowUpScheduler.cancel(context, userId, submissionId)
                    persistWakeStatus(
                        context = context,
                        userId = userId,
                        submissionId = submissionId,
                        wakeStatus = "asleep",
                        pendingResult = pendingResult
                    )
                }
            }
            else -> pendingResult.finish()
        }
    }

    private fun notificationId(submissionId: String): Int {
        return NotificationHelper.FOLLOW_UP_NOTIFICATION_ID_BASE + submissionId.hashCode()
    }

    private fun persistWakeStatus(
        context: Context,
        userId: String,
        submissionId: String,
        wakeStatus: String,
        pendingResult: PendingResult
    ) {
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
                openWakeArea(context)
                pendingResult.finish()
            },
            onFailure = {
                pendingResult.finish()
            }
        )
    }

    private fun openWakeArea(context: Context) {
        val launchIntent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OPEN_AREA, ProblemArea.WAKE_UP.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(launchIntent)
    }

    companion object {
        const val ACTION_YES = "fixd.action.WAKE_FOLLOW_UP_YES"
        const val ACTION_NO = "fixd.action.WAKE_FOLLOW_UP_NO"
    }
}

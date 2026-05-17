package com.example.fixd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (UserPreferences.isProblemDisplayed(context, ProblemArea.WAKE_UP)) {
            LocalAlarmCache.getAlarms(context)
                .filter { it.enabled }
                .forEach { AlarmScheduler.schedule(context, it) }
        }
        if (UserPreferences.isProblemDisplayed(context, ProblemArea.COUNTDOWN)) {
            CountdownLocalCache.getCountdowns(context)
                .filter { it.notifyAt > System.currentTimeMillis() }
                .forEach { CountdownReminderScheduler.schedule(context, it) }
        }
        if (UserPreferences.isProblemDisplayed(context, ProblemArea.CHALLENGES)) {
            ChallengeEndOfDayReminderScheduler.scheduleNext(context)
        }
        WakeWidgetUpdater.updateAll(context)
    }
}

package com.example.fixd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (!AlarmScheduler.canScheduleExactAlarms(context)) {
            WakeWidgetUpdater.updateAll(context)
            return
        }
        LocalAlarmCache.getAlarms(context)
            .filter { it.enabled }
            .forEach { AlarmScheduler.schedule(context, it) }
        CountdownLocalCache.getCountdowns(context)
            .filter { it.notifyAt > System.currentTimeMillis() }
            .forEach { CountdownReminderScheduler.schedule(context, it) }
        WakeWidgetUpdater.updateAll(context)
    }
}

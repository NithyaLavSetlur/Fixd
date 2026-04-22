package com.example.fixd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannels(context)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "fixd:alarm_wake_lock"
        )
        wakeLock.acquire(30_000)
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val alarmName = intent.getStringExtra(EXTRA_ALARM_NAME).orEmpty()
        val alarmHour = intent.getIntExtra(EXTRA_ALARM_HOUR, 0)
        val alarmMinute = intent.getIntExtra(EXTRA_ALARM_MINUTE, 0)
        val triggeredAt = System.currentTimeMillis()
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_RESUME
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_NAME, alarmName)
            putExtra(EXTRA_ALARM_HOUR, alarmHour)
            putExtra(EXTRA_ALARM_MINUTE, alarmMinute)
            putExtra(EXTRA_TRIGGERED_AT, triggeredAt)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        val activityIntent = Intent(context, AlarmChallengeActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_NAME, alarmName)
            putExtra(EXTRA_ALARM_HOUR, alarmHour)
            putExtra(EXTRA_ALARM_MINUTE, alarmMinute)
            putExtra(EXTRA_TRIGGERED_AT, triggeredAt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(activityIntent) }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_NAME = "alarm_name"
        const val EXTRA_ALARM_HOUR = "alarm_hour"
        const val EXTRA_ALARM_MINUTE = "alarm_minute"
        const val EXTRA_TRIGGERED_AT = "triggered_at"
    }
}

package com.example.fixd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {
    fun schedule(context: Context, alarm: WakeAlarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays)
        val pendingIntent = pendingIntent(context, alarm)
        val showIntent = Intent(context, DashboardActivity::class.java)
        val info = AlarmManager.AlarmClockInfo(triggerAt, PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        alarmManager.setAlarmClock(info, pendingIntent)
    }

    fun cancel(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                alarmId.hashCode(),
                Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun pendingIntent(context: Context, alarm: WakeAlarm): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_NAME, alarm.name)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, alarm.minute)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int, repeatDays: List<Int>): Long {
        val now = Calendar.getInstance()
        val targetDays = if (repeatDays.isEmpty()) listOf(1, 2, 3, 4, 5, 6, 7) else repeatDays
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.get(Calendar.DAY_OF_WEEK) in targetDays && candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis
            }
        }
        return now.timeInMillis + 24 * 60 * 60 * 1000
    }
}

package com.example.fixd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, alarm: WakeAlarm) {
        if (!canScheduleExactAlarms(context)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays)
        cancel(context, alarm.id)
        val pendingIntent = pendingIntent(context, alarm)
        val showIntent = Intent(context, DashboardActivity::class.java)
        val info = AlarmManager.AlarmClockInfo(triggerAt, PendingIntent.getActivity(
            context,
            0,
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
                0,
                alarmIntent(context, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun pendingIntent(context: Context, alarm: WakeAlarm): PendingIntent {
        val intent = alarmIntent(context, alarm.id).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_NAME, alarm.name)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, alarm.minute)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmIntent(context: Context, alarmId: String): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.fixd.ALARM_TRIGGER.$alarmId"
            data = Uri.parse("fixd://alarm/$alarmId")
        }
    }

    private fun nextTriggerMillis(hour: Int, minute: Int, repeatDays: List<Int>): Long {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now(zoneId)
        val targetDays = if (repeatDays.isEmpty()) {
            DayOfWeek.entries.toSet()
        } else {
            repeatDays.mapNotNull(::toDayOfWeek).toSet()
        }

        for (offset in 0..7) {
            val candidateDate = LocalDate.now(zoneId).plusDays(offset.toLong())
            val candidate = candidateDate.atTime(hour, minute)
            if (candidate.dayOfWeek in targetDays && candidate.isAfter(now)) {
                return candidate.atZone(zoneId).toInstant().toEpochMilli()
            }
        }

        return LocalDate.now(zoneId)
            .plusDays(1)
            .atTime(hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun toDayOfWeek(value: Int): DayOfWeek? {
        return when (value) {
            1 -> DayOfWeek.SUNDAY
            2 -> DayOfWeek.MONDAY
            3 -> DayOfWeek.TUESDAY
            4 -> DayOfWeek.WEDNESDAY
            5 -> DayOfWeek.THURSDAY
            6 -> DayOfWeek.FRIDAY
            7 -> DayOfWeek.SATURDAY
            else -> null
        }
    }
}

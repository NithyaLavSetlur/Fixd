package com.example.fixd

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    const val ALARM_CHANNEL_ID = "fixd_alarm_channel"
    const val GENERAL_CHANNEL_ID = "fixd_general_channel"
    const val FOLLOW_UP_NOTIFICATION_ID_BASE = 9000
    const val COUNTDOWN_NOTIFICATION_ID_BASE = 12000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Wake alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Time-sensitive wake up alarms"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val generalChannel = NotificationChannel(
            GENERAL_CHANNEL_ID,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannels(listOf(alarmChannel, generalChannel))
    }

    fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

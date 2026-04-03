package com.example.fixd

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmRingingService : Service() {
    private var player: MediaPlayer? = null
    private var currentVolume = HIGH_VOLUME

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action.orEmpty()
        val alarmId = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID).orEmpty()
        val alarmName = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_NAME).orEmpty()
        val alarmHour = intent?.getIntExtra(AlarmReceiver.EXTRA_ALARM_HOUR, 0) ?: 0
        val alarmMinute = intent?.getIntExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, 0) ?: 0
        val triggeredAt = intent?.getLongExtra(AlarmReceiver.EXTRA_TRIGGERED_AT, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        when (action) {
            ACTION_STOP -> stopSelf()
            ACTION_PAUSE -> pauseAlarmSound()
            ACTION_LOW_VOLUME -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(alarmId, alarmName, alarmHour, alarmMinute, triggeredAt)
                )
                startAlarmSound(LOW_VOLUME)
            }
            ACTION_RESUME, "" -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(alarmId, alarmName, alarmHour, alarmMinute, triggeredAt)
                )
                startAlarmSound(HIGH_VOLUME)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(
        alarmId: String,
        alarmName: String,
        alarmHour: Int,
        alarmMinute: Int,
        triggeredAt: Long
    ): Notification {
        val fullScreenIntent = Intent(this, AlarmChallengeActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_NAME, alarmName)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, alarmHour)
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, alarmMinute)
            putExtra(AlarmReceiver.EXTRA_TRIGGERED_AT, triggeredAt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationHelper.ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(getString(R.string.alarm_notification_body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startAlarmSound(volume: Float) {
        if (player == null) {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(applicationContext, defaultUri)
                isLooping = true
                prepare()
            }
        }
        currentVolume = volume.coerceIn(0f, 1f)
        player?.setVolume(currentVolume, currentVolume)
        if (player?.isPlaying != true) player?.start()
    }

    private fun pauseAlarmSound() {
        if (player?.isPlaying == true) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        player?.stop()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 7001
        const val ACTION_STOP = "fixd.action.STOP_ALARM"
        const val ACTION_PAUSE = "fixd.action.PAUSE_ALARM"
        const val ACTION_RESUME = "fixd.action.RESUME_ALARM"
        const val ACTION_LOW_VOLUME = "fixd.action.LOW_VOLUME_ALARM"
        private const val HIGH_VOLUME = 1f
        private const val LOW_VOLUME = 0.08f
    }
}

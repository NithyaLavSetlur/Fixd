package com.example.fixd

import android.content.Context
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object WakeSubmissionUi {
    private val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val dateTimeFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    fun typeLabel(context: Context, submission: WakeSubmission): String {
        return context.getString(
            if (submission.type == "image") R.string.wake_history_image else R.string.wake_history_text
        )
    }

    fun formatAlarmTime(hour: Int, minute: Int): String {
        val safeHour = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour >= 12) "PM" else "AM"
        return String.format(Locale.getDefault(), "%d:%02d %s", safeHour, minute, suffix)
    }

    fun formatDate(timestamp: Long): String = dateFormatter.format(Date(effectiveTimestamp(timestamp)))

    fun formatDateTime(timestamp: Long): String = dateTimeFormatter.format(Date(effectiveTimestamp(timestamp)))

    fun formatDuration(context: Context, durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            context.getString(R.string.wake_duration_minutes_seconds, minutes, seconds)
        } else {
            context.getString(R.string.wake_duration_seconds, seconds)
        }
    }

    fun bindWakeStatus(
        textView: TextView,
        submission: WakeSubmission,
        palette: GeneratedPalette = ThemePaletteManager.currentPalette(textView.context)
    ) {
        when (submission.wakeStatus) {
            "awake" -> {
                textView.text = textView.context.getString(R.string.wake_status_awake_symbol)
                textView.setTextColor(ThemePaletteManager.readableColorForView(textView, palette.success, palette))
            }
            "asleep" -> {
                textView.text = textView.context.getString(R.string.wake_status_asleep_symbol)
                textView.setTextColor(ThemePaletteManager.readableColorForView(textView, palette.danger, palette))
            }
            else -> {
                textView.text = textView.context.getString(R.string.wake_history_status_pending)
                textView.setTextColor(ThemePaletteManager.adaptiveMutedTextColorForView(textView, palette))
            }
        }
    }

    fun wakeStatusLabel(context: Context, submission: WakeSubmission): String {
        return when (submission.wakeStatus) {
            "awake" -> context.getString(R.string.wake_history_status_awake)
            "asleep" -> context.getString(R.string.wake_history_status_asleep)
            else -> context.getString(R.string.wake_history_status_pending)
        }
    }

    fun primaryTimestamp(submission: WakeSubmission): Long {
        return when {
            submission.completedAt > 0L -> submission.completedAt
            submission.createdAt > 0L -> submission.createdAt
            else -> submission.triggeredAt
        }
    }

    private fun effectiveTimestamp(timestamp: Long): Long {
        return if (timestamp > 0L) timestamp else System.currentTimeMillis()
    }
}

package com.example.fixd

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.fixd.databinding.ViewSubmissionDetailBinding
import java.io.File
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

    fun bindWakeStatus(textView: TextView, submission: WakeSubmission) {
        when (submission.wakeStatus) {
            "awake" -> {
                textView.text = textView.context.getString(R.string.wake_status_awake_symbol)
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.brand_success))
            }
            "asleep" -> {
                textView.text = textView.context.getString(R.string.wake_status_asleep_symbol)
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.wake_status_asleep))
            }
            else -> {
                textView.text = textView.context.getString(R.string.wake_history_status_pending)
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.brand_text_muted))
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

    fun showDetails(context: Context, inflater: LayoutInflater, submission: WakeSubmission) {
        val binding = ViewSubmissionDetailBinding.inflate(inflater)
        binding.detailDate.text = formatDateTime(primaryTimestamp(submission))
        binding.detailMeta.text = context.getString(
            R.string.wake_history_detail_meta,
            typeLabel(context, submission),
            formatAlarmTime(submission.alarmHour, submission.alarmMinute),
            formatDuration(context, submission.responseDurationMs),
            submission.verdict.ifBlank { context.getString(R.string.wake_history_status_pending) }
        )
        binding.detailWakeStatus.text = wakeStatusLabel(context, submission)
        bindWakeStatus(binding.detailWakeStatus, submission)
        binding.detailAiResponse.text = submission.feedback.ifBlank {
            context.getString(R.string.wake_history_detail_ai_empty)
        }

        if (submission.type == "image") {
            binding.detailInputText.text = if (submission.text.isNotBlank()) {
                submission.text
            } else {
                context.getString(R.string.wake_history_detail_image_only)
            }
            val file = File(submission.imagePath)
            if (submission.imagePath.isNotBlank() && file.exists()) {
                binding.detailImage.visibility = android.view.View.VISIBLE
                binding.detailImage.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            } else {
                binding.detailImage.visibility = android.view.View.GONE
                binding.detailInputText.append("\n\n" + context.getString(R.string.wake_history_image_unavailable))
            }
        } else {
            binding.detailImage.visibility = android.view.View.GONE
            binding.detailInputText.text = submission.text.ifBlank {
                context.getString(R.string.wake_history_detail_no_text)
            }
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.wake_history_detail_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

package com.example.fixd

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

object WakeSubmissionCache {
    private const val PREFS_NAME = "wake_submission_cache"
    private const val KEY_SUBMISSIONS = "submissions"
    private const val KEY_SUBMISSIONS_PREFIX = "submissions:"

    fun saveSubmissions(context: Context, submissions: List<WakeSubmission>) {
        saveSubmissions(context, currentUserId(), submissions)
    }

    fun saveSubmissions(context: Context, userId: String, submissions: List<WakeSubmission>) {
        if (userId.isBlank()) return
        val json = JSONArray().apply {
            submissions
                .map(::sanitizeSubmission)
                .distinctBy { it.id.ifBlank { "${it.alarmId}:${it.createdAt}:${it.completedAt}" } }
                .sortedByDescending { it.createdAt }
                .forEach { put(it.toJson()) }
        }
        prefs(context).edit().putString(scopedKey(userId), json.toString()).apply()
    }

    fun getSubmissions(context: Context): List<WakeSubmission> {
        return getSubmissions(context, currentUserId())
    }

    fun getSubmissions(context: Context, userId: String): List<WakeSubmission> {
        if (userId.isBlank()) return emptyList()
        val raw = prefs(context).getString(scopedKey(userId), null)
            ?: legacyValueForFirstScopedRead(context, userId)
            ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toWakeSubmission())
                }
            }.map(::sanitizeSubmission)
                .distinctBy { it.id.ifBlank { "${it.alarmId}:${it.createdAt}:${it.completedAt}" } }
                .sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun upsertSubmission(context: Context, submission: WakeSubmission) {
        upsertSubmission(context, currentUserId(), submission)
    }

    fun upsertSubmission(context: Context, userId: String, submission: WakeSubmission) {
        if (userId.isBlank()) return
        val sanitized = sanitizeSubmission(submission)
        val updated = getSubmissions(context, userId)
            .filterNot { it.id == submission.id }
            .plus(sanitized)
            .sortedByDescending { it.createdAt }
        saveSubmissions(context, userId, updated)
    }

    private fun sanitizeSubmission(submission: WakeSubmission): WakeSubmission {
        val wakeStatus = when (submission.wakeStatus) {
            "pending", "awake", "asleep" -> submission.wakeStatus
            else -> "pending"
        }
        val verdict = when (submission.verdict) {
            "passed", "retry" -> submission.verdict
            else -> submission.verdict.ifBlank { "retry" }
        }
        return submission.copy(
            verdict = verdict,
            alarmHour = submission.alarmHour.coerceIn(0, 23),
            alarmMinute = submission.alarmMinute.coerceIn(0, 59),
            triggeredAt = submission.triggeredAt.coerceAtLeast(0L),
            completedAt = submission.completedAt.coerceAtLeast(0L),
            responseDurationMs = submission.responseDurationMs.coerceAtLeast(0L),
            wakeStatus = wakeStatus,
            createdAt = submission.createdAt.coerceAtLeast(0L)
        )
    }

    private fun legacyValueForFirstScopedRead(context: Context, userId: String): String? {
        val prefs = prefs(context)
        val hasScopedCaches = prefs.all.keys.any { it.startsWith(KEY_SUBMISSIONS_PREFIX) }
        if (hasScopedCaches || !prefs.contains(KEY_SUBMISSIONS)) return null
        val legacy = prefs.getString(KEY_SUBMISSIONS, null) ?: return null
        prefs.edit().putString(scopedKey(userId), legacy).apply()
        return legacy
    }

    private fun WakeSubmission.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("alarmId", alarmId)
            put("type", type)
            put("text", text)
            put("imagePath", imagePath)
            put("verdict", verdict)
            put("feedback", feedback)
            put("alarmHour", alarmHour)
            put("alarmMinute", alarmMinute)
            put("triggeredAt", triggeredAt)
            put("completedAt", completedAt)
            put("responseDurationMs", responseDurationMs)
            put("wakeStatus", wakeStatus)
            put("createdAt", createdAt)
        }
    }

    private fun JSONObject.toWakeSubmission(): WakeSubmission {
        return WakeSubmission(
            id = optString("id"),
            alarmId = optString("alarmId"),
            type = optString("type"),
            text = optString("text"),
            imagePath = optString("imagePath"),
            verdict = optString("verdict"),
            feedback = optString("feedback"),
            alarmHour = optInt("alarmHour"),
            alarmMinute = optInt("alarmMinute"),
            triggeredAt = optLong("triggeredAt"),
            completedAt = optLong("completedAt"),
            responseDurationMs = optLong("responseDurationMs"),
            wakeStatus = optString("wakeStatus", "pending"),
            createdAt = optLong("createdAt")
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun scopedKey(userId: String) = "$KEY_SUBMISSIONS_PREFIX$userId"

    private fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
}

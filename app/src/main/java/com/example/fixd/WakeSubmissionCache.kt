package com.example.fixd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WakeSubmissionCache {
    private const val PREFS_NAME = "wake_submission_cache"
    private const val KEY_SUBMISSIONS = "submissions"

    fun saveSubmissions(context: Context, submissions: List<WakeSubmission>) {
        val json = JSONArray().apply {
            submissions.forEach { put(it.toJson()) }
        }
        prefs(context).edit().putString(KEY_SUBMISSIONS, json.toString()).apply()
    }

    fun getSubmissions(context: Context): List<WakeSubmission> {
        val raw = prefs(context).getString(KEY_SUBMISSIONS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toWakeSubmission())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsertSubmission(context: Context, submission: WakeSubmission) {
        val updated = getSubmissions(context)
            .filterNot { it.id == submission.id }
            .plus(submission)
            .sortedByDescending { it.createdAt }
        saveSubmissions(context, updated)
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
}

package com.example.fixd

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

object LocalAlarmCache {
    private const val PREFS_NAME = "fixd_alarm_cache"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_ALARMS_PREFIX = "$KEY_ALARMS:"

    fun saveAlarms(context: Context, alarms: List<WakeAlarm>) {
        saveAlarms(context, currentUserId(), alarms)
    }

    fun saveAlarms(context: Context, userId: String, alarms: List<WakeAlarm>) {
        if (userId.isBlank()) return
        val array = JSONArray()
        alarms.forEach { alarm ->
            val sanitized = sanitizeAlarm(alarm)
            array.put(
                JSONObject().apply {
                    put("id", sanitized.id)
                    put("name", sanitized.name)
                    put("hour", sanitized.hour)
                    put("minute", sanitized.minute)
                    put("enabled", sanitized.enabled)
                    put("createdAt", sanitized.createdAt)
                    put("repeatDays", JSONArray(sanitized.repeatDays))
                    put("kind", sanitized.kind)
                    put("triggerAtMillis", sanitized.triggerAtMillis)
                    put("sleepDurationHours", sanitized.sleepDurationHours.toDouble())
                }
            )
        }
        prefs(context).edit().putString(scopedKey(userId), array.toString()).apply()
    }

    fun getAlarms(context: Context): List<WakeAlarm> {
        return getAlarms(context, currentUserId())
    }

    fun getAlarms(context: Context, userId: String): List<WakeAlarm> {
        if (userId.isBlank()) return emptyList()
        val prefs = prefs(context)
        val key = scopedKey(userId)
        val raw = prefs.getString(key, null)
            ?: legacyValueForFirstScopedRead(context, userId)
            ?: return emptyList()
        return parseAlarms(raw)
    }

    private fun parseAlarms(raw: String): List<WakeAlarm> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val repeatDaysArray = item.optJSONArray("repeatDays") ?: JSONArray()
                val repeatDays = buildList {
                    for (dayIndex in 0 until repeatDaysArray.length()) {
                        add(repeatDaysArray.optInt(dayIndex))
                    }
                }
                add(
                    WakeAlarm(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        hour = item.optInt("hour"),
                        minute = item.optInt("minute"),
                        repeatDays = repeatDays,
                        enabled = item.optBoolean("enabled", true),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        kind = item.optString("kind", WakeAlarm.KIND_STANDARD),
                        triggerAtMillis = item.optLong("triggerAtMillis", 0L),
                        sleepDurationHours = item.optDouble("sleepDurationHours", 0.0).toFloat()
                    )
                )
            }
        }.map(::sanitizeAlarm)
    }.getOrDefault(emptyList())

    private fun sanitizeAlarm(alarm: WakeAlarm): WakeAlarm {
        val repeatDays = alarm.repeatDays
            .filter { it in CalendarDayRange }
            .distinct()
            .ifEmpty { CalendarDayRange.toList() }
        val kind = alarm.kind.takeIf { it == WakeAlarm.KIND_SLEEP_DURATION || it == WakeAlarm.KIND_STANDARD }
            ?: WakeAlarm.KIND_STANDARD
        return alarm.copy(
            hour = alarm.hour.coerceIn(0, 23),
            minute = alarm.minute.coerceIn(0, 59),
            repeatDays = repeatDays,
            kind = kind,
            triggerAtMillis = alarm.triggerAtMillis.coerceAtLeast(0L),
            sleepDurationHours = alarm.sleepDurationHours.coerceAtLeast(0f)
        )
    }

    private fun legacyValueForFirstScopedRead(context: Context, userId: String): String? {
        val prefs = prefs(context)
        val hasScopedCaches = prefs.all.keys.any { it.startsWith(KEY_ALARMS_PREFIX) }
        if (hasScopedCaches || !prefs.contains(KEY_ALARMS)) return null
        val legacy = prefs.getString(KEY_ALARMS, null) ?: return null
        prefs.edit().putString(scopedKey(userId), legacy).apply()
        return legacy
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun scopedKey(userId: String) = "$KEY_ALARMS_PREFIX$userId"

    private fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private val CalendarDayRange = 1..7
}

package com.example.fixd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LocalAlarmCache {
    private const val PREFS_NAME = "fixd_alarm_cache"
    private const val KEY_ALARMS = "alarms"

    fun saveAlarms(context: Context, alarms: List<WakeAlarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject().apply {
                    put("id", alarm.id)
                    put("name", alarm.name)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("enabled", alarm.enabled)
                    put("createdAt", alarm.createdAt)
                    put("repeatDays", JSONArray(alarm.repeatDays))
                }
            )
        }
        prefs(context).edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    fun getAlarms(context: Context): List<WakeAlarm> {
        val raw = prefs(context).getString(KEY_ALARMS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
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
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

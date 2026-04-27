package com.example.fixd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CountdownLocalCache {
    private const val PREFS_NAME = "fixd_countdown_cache"
    private const val KEY_COUNTDOWNS = "countdowns"

    fun saveCountdowns(context: Context, countdowns: List<CountdownEntry>) {
        val array = JSONArray()
        countdowns.forEach { countdown ->
            array.put(
                JSONObject().apply {
                    put("id", countdown.id)
                    put("title", countdown.title)
                    put("targetAt", countdown.targetAt)
                    put("notifyAt", countdown.notifyAt)
                    put("createdAt", countdown.createdAt)
                }
            )
        }
        prefs(context).edit().putString(KEY_COUNTDOWNS, array.toString()).apply()
    }

    fun getCountdowns(context: Context): List<CountdownEntry> {
        val raw = prefs(context).getString(KEY_COUNTDOWNS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        CountdownEntry(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            targetAt = item.optLong("targetAt"),
                            notifyAt = item.optLong("notifyAt", item.optLong("targetAt")),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

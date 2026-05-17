package com.example.fixd

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

object CountdownLocalCache {
    private const val PREFS_NAME = "fixd_countdown_cache"
    private const val KEY_COUNTDOWNS = "countdowns"
    private const val KEY_COUNTDOWNS_PREFIX = "countdowns:"

    fun saveCountdowns(context: Context, countdowns: List<CountdownEntry>) {
        saveCountdowns(context, currentUserId(), countdowns)
    }

    fun saveCountdowns(context: Context, userId: String, countdowns: List<CountdownEntry>) {
        if (userId.isBlank()) return
        val array = JSONArray()
        countdowns.forEach { countdown ->
            val sanitized = sanitizeCountdown(countdown)
            array.put(
                JSONObject().apply {
                    put("id", sanitized.id)
                    put("title", sanitized.title)
                    put("targetAt", sanitized.targetAt)
                    put("notifyAt", sanitized.notifyAt)
                    put("createdAt", sanitized.createdAt)
                }
            )
        }
        prefs(context).edit().putString(scopedKey(userId), array.toString()).apply()
    }

    fun getCountdowns(context: Context): List<CountdownEntry> {
        return getCountdowns(context, currentUserId())
    }

    fun getCountdowns(context: Context, userId: String): List<CountdownEntry> {
        if (userId.isBlank()) return emptyList()
        val raw = prefs(context).getString(scopedKey(userId), null)
            ?: legacyValueForFirstScopedRead(context, userId)
            ?: return emptyList()
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
            }.map(::sanitizeCountdown)
                .distinctBy { it.id.ifBlank { "${it.title}:${it.targetAt}:${it.createdAt}" } }
                .sortedBy { it.targetAt }
        }.getOrDefault(emptyList())
    }

    private fun sanitizeCountdown(countdown: CountdownEntry): CountdownEntry {
        val targetAt = countdown.targetAt.coerceAtLeast(0L)
        return countdown.copy(
            targetAt = targetAt,
            notifyAt = countdown.notifyAt.coerceIn(0L, targetAt),
            createdAt = countdown.createdAt.coerceAtLeast(0L)
        )
    }

    private fun legacyValueForFirstScopedRead(context: Context, userId: String): String? {
        val prefs = prefs(context)
        val hasScopedCaches = prefs.all.keys.any { it.startsWith(KEY_COUNTDOWNS_PREFIX) }
        if (hasScopedCaches || !prefs.contains(KEY_COUNTDOWNS)) return null
        val legacy = prefs.getString(KEY_COUNTDOWNS, null) ?: return null
        prefs.edit().putString(scopedKey(userId), legacy).apply()
        return legacy
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun scopedKey(userId: String) = "$KEY_COUNTDOWNS_PREFIX$userId"

    private fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
}

package com.example.fixd

import android.content.Context

data class CountdownWidgetItem(
    val title: String,
    val targetAt: Long
)

object CountdownWidgetCache {
    private const val PREFS_NAME = "fixd_countdown_widget_cache"
    private const val KEY_ITEMS = "items"
    private const val ITEM_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\u001F"

    fun save(context: Context, items: List<CountdownEntry>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_ITEMS,
                items.joinToString(ITEM_SEPARATOR) { item ->
                    listOf(item.title, item.targetAt.toString()).joinToString(FIELD_SEPARATOR)
                }
            )
            .apply()
    }

    fun get(context: Context): List<CountdownWidgetItem> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null)
            ?.split(ITEM_SEPARATOR)
            ?.mapNotNull { row ->
                val parts = row.split(FIELD_SEPARATOR)
                if (parts.size < 2) return@mapNotNull null
                CountdownWidgetItem(
                    title = parts[0],
                    targetAt = parts[1].toLongOrNull() ?: return@mapNotNull null
                )
            }
            ?: emptyList()
    }
}

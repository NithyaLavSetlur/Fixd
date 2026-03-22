package com.example.fixd

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object WakeStatsCalculator {
    fun calculate(submissions: List<WakeSubmission>, zoneId: ZoneId = ZoneId.systemDefault()): WakeStats {
        if (submissions.isEmpty()) {
            return WakeStats()
        }

        val dailyStatuses = mutableMapOf<LocalDate, String>()
        submissions
            .sortedByDescending { it.triggeredAt.takeIf { value -> value > 0L } ?: it.createdAt }
            .forEach { submission ->
                val timestamp = submission.triggeredAt.takeIf { it > 0L } ?: submission.createdAt
                val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
                val existing = dailyStatuses[date]
                dailyStatuses[date] = mergeDailyStatus(existing, submission.wakeStatus)
            }

        val today = LocalDate.now(zoneId)
        val todayStatus = dailyStatuses[today] ?: "pending"
        val anchorDate = when {
            todayStatus == "awake" -> today
            todayStatus == "pending" -> today.minusDays(1)
            else -> return WakeStats(currentStreak = 0, todayStatus = todayStatus)
        }

        var streak = 0
        var cursor = anchorDate
        while (dailyStatuses[cursor] == "awake") {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return WakeStats(currentStreak = streak, todayStatus = todayStatus)
    }

    private fun mergeDailyStatus(existing: String?, incoming: String): String {
        return when {
            existing == "asleep" || incoming == "asleep" -> "asleep"
            existing == "awake" || incoming == "awake" -> "awake"
            existing == "pending" -> existing
            incoming.isNotBlank() -> incoming
            else -> existing ?: "pending"
        }
    }
}

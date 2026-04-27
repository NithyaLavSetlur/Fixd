package com.example.fixd

import android.content.Context

data class ChallengeWidgetMission(
    val title: String,
    val xp: Int,
    val icon: String
)

data class ChallengeWidgetFigure(
    val emoji: String,
    val name: String,
    val level: Int
)

data class ChallengeWidgetBadge(
    val emoji: String,
    val name: String
)

data class ChallengeWidgetSummary(
    val level: Int = 1,
    val totalXp: Int = 0,
    val streak: Int = 0,
    val completedToday: Int = 0,
    val totalToday: Int = 0,
    val missions: List<ChallengeWidgetMission> = emptyList(),
    val figures: List<ChallengeWidgetFigure> = emptyList(),
    val badges: List<ChallengeWidgetBadge> = emptyList()
)

object ChallengeWidgetCache {
    private const val PREFS_NAME = "fixd_challenge_widget_cache"
    private const val KEY_LEVEL = "level"
    private const val KEY_TOTAL_XP = "total_xp"
    private const val KEY_STREAK = "streak"
    private const val KEY_COMPLETED_TODAY = "completed_today"
    private const val KEY_TOTAL_TODAY = "total_today"
    private const val KEY_MISSIONS = "missions"
    private const val KEY_FIGURES = "figures"
    private const val KEY_BADGES = "badges"
    private const val MISSION_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\u001F"

    fun fromSnapshot(snapshot: ChallengeSnapshot): ChallengeWidgetSummary {
        val todayDayOfWeek = ChallengeGameEngine.currentDayOfWeek()
        val todayDayKey = ChallengeGameEngine.currentDayKey()
        val groupsById = snapshot.groups.associateBy { it.id }
        val activeTasks = snapshot.tasks
            .filter { todayDayOfWeek in it.activeDays }
            .sortedWith(
                compareByDescending<ChallengeTask> { it.xpReward }
                    .thenBy { it.title.lowercase() }
            )
        val todayCompletedTaskIds = snapshot.completions
            .filter { it.dayKey == todayDayKey }
            .map { it.taskId }
            .toSet()
        val totalXp = snapshot.completions.sumOf { it.xpReward }
        val ownedFigures = snapshot.unlocks.mapNotNull { unlock ->
            ChallengeGameEngine.shopCatalog.firstOrNull { it.id == unlock.figureId }?.let { figure ->
                ChallengeWidgetFigure(
                    emoji = figure.emoji,
                    name = figure.name,
                    level = unlock.figureLevel
                )
            }
        }.sortedByDescending { it.level }
        val unlockedBadgeIds = ChallengeGameEngine.unlockedBadgeIds(
            totalXp = totalXp,
            level = ChallengeGameEngine.levelStatus(totalXp).level,
            streak = ChallengeGameEngine.currentStreak(snapshot.completions),
            completionCount = snapshot.completions.size,
            figureCount = snapshot.unlocks.size,
            sceneryCount = snapshot.sceneryUnlocks.size,
            accessoryCount = snapshot.accessoryUnlocks.size,
            maxFriendGroupSize = snapshot.friendGroups.maxOfOrNull { it.memberUids.size } ?: 1
        )
        val badges = ChallengeGameEngine.badgeCatalog
            .filter { unlockedBadgeIds.contains(it.id) }
            .map { badge -> ChallengeWidgetBadge(emoji = badge.emoji, name = badge.name) }
        return ChallengeWidgetSummary(
            level = ChallengeGameEngine.levelStatus(totalXp).level,
            totalXp = totalXp,
            streak = ChallengeGameEngine.currentStreak(snapshot.completions),
            completedToday = activeTasks.count { todayCompletedTaskIds.contains(it.id) },
            totalToday = activeTasks.size,
            missions = activeTasks.take(3).map { task ->
                ChallengeWidgetMission(
                    title = task.title,
                    xp = task.xpReward,
                    icon = groupsById[task.groupId]?.icon.orEmpty().ifBlank { "\uD83C\uDFAF" }
                )
            },
            figures = ownedFigures,
            badges = badges
        )
    }

    fun save(context: Context, summary: ChallengeWidgetSummary) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LEVEL, summary.level)
            .putInt(KEY_TOTAL_XP, summary.totalXp)
            .putInt(KEY_STREAK, summary.streak)
            .putInt(KEY_COMPLETED_TODAY, summary.completedToday)
            .putInt(KEY_TOTAL_TODAY, summary.totalToday)
            .putString(
                KEY_MISSIONS,
                summary.missions.joinToString(MISSION_SEPARATOR) { mission ->
                    listOf(mission.title, mission.xp.toString(), mission.icon).joinToString(FIELD_SEPARATOR)
                }
            )
            .putString(
                KEY_FIGURES,
                summary.figures.joinToString(MISSION_SEPARATOR) { figure ->
                    listOf(figure.emoji, figure.name, figure.level.toString()).joinToString(FIELD_SEPARATOR)
                }
            )
            .putString(
                KEY_BADGES,
                summary.badges.joinToString(MISSION_SEPARATOR) { badge ->
                    listOf(badge.emoji, badge.name).joinToString(FIELD_SEPARATOR)
                }
            )
            .apply()
    }

    fun get(context: Context): ChallengeWidgetSummary {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val missions = prefs.getString(KEY_MISSIONS, null)
            ?.split(MISSION_SEPARATOR)
            ?.mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                ChallengeWidgetMission(
                    title = parts[0],
                    xp = parts[1].toIntOrNull() ?: 0,
                    icon = parts[2]
                )
            }
            ?: emptyList()
        val figures = prefs.getString(KEY_FIGURES, null)
            ?.split(MISSION_SEPARATOR)
            ?.mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                ChallengeWidgetFigure(
                    emoji = parts[0],
                    name = parts[1],
                    level = parts[2].toIntOrNull() ?: 1
                )
            }
            ?: emptyList()
        val badges = prefs.getString(KEY_BADGES, null)
            ?.split(MISSION_SEPARATOR)
            ?.mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 2) return@mapNotNull null
                ChallengeWidgetBadge(
                    emoji = parts[0],
                    name = parts[1]
                )
            }
            ?: emptyList()
        return ChallengeWidgetSummary(
            level = prefs.getInt(KEY_LEVEL, 1),
            totalXp = prefs.getInt(KEY_TOTAL_XP, 0),
            streak = prefs.getInt(KEY_STREAK, 0),
            completedToday = prefs.getInt(KEY_COMPLETED_TODAY, 0),
            totalToday = prefs.getInt(KEY_TOTAL_TODAY, 0),
            missions = missions,
            figures = figures,
            badges = badges
        )
    }
}

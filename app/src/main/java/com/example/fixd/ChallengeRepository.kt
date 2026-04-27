package com.example.fixd

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.UUID

data class ChallengeGroup(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "\uD83D\uDCD8",
    val createdAt: Long = System.currentTimeMillis()
)

data class ChallengeTask(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val note: String = "",
    val effort: Int = 2,
    val xpReward: Int = ChallengeGameEngine.xpForEffort(2),
    val activeDays: List<Int> = listOf(2, 3, 4, 5, 6),
    val createdAt: Long = System.currentTimeMillis()
)

data class ChallengeCompletion(
    val id: String = "",
    val taskId: String = "",
    val dayKey: String = "",
    val completedAt: Long = System.currentTimeMillis(),
    val xpReward: Int = 0
)

data class ChallengeShopUnlock(
    val figureId: String = "",
    val unlockedAt: Long = System.currentTimeMillis(),
    val figureLevel: Int = 1,
    val lastUpgradeAt: Long = 0L
)

data class ChallengeSceneryUnlock(
    val sceneryId: String = "",
    val unlockedAt: Long = System.currentTimeMillis()
)

data class ChallengeAccessoryUnlock(
    val accessoryId: String = "",
    val unlockedAt: Long = System.currentTimeMillis()
)

data class ChallengeFriendGroup(
    val id: String = "",
    val title: String = "",
    val createdByUid: String = "",
    val createdByUsername: String = "",
    val memberUids: List<String> = emptyList(),
    val memberUsernames: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ChallengeFriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val fromPreferredName: String = "",
    val toUid: String = "",
    val toUsername: String = "",
    val status: String = "pending",
    val memberUids: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ChallengeUserPublic(
    val uid: String,
    val username: String,
    val preferredName: String
)

data class ChallengeLeaderboardEntry(
    val uid: String,
    val username: String,
    val preferredName: String,
    val level: Int,
    val totalXp: Int,
    val streak: Int,
    val completionCount: Int,
    val figureCount: Int,
    val sceneryCount: Int,
    val accessoryCount: Int,
    val badgeCount: Int
)

data class ChallengeBadge(
    val id: String,
    val emoji: String,
    val name: String,
    val description: String
)

data class ChallengeDisplaySettings(
    val equippedSceneryId: String = "sunny_meadow",
    val equippedAccessoryIds: List<String> = emptyList()
)

data class ChallengeSnapshot(
    val groups: List<ChallengeGroup> = emptyList(),
    val tasks: List<ChallengeTask> = emptyList(),
    val completions: List<ChallengeCompletion> = emptyList(),
    val unlocks: List<ChallengeShopUnlock> = emptyList(),
    val sceneryUnlocks: List<ChallengeSceneryUnlock> = emptyList(),
    val accessoryUnlocks: List<ChallengeAccessoryUnlock> = emptyList(),
    val friendGroups: List<ChallengeFriendGroup> = emptyList(),
    val displaySettings: ChallengeDisplaySettings = ChallengeDisplaySettings()
)

data class ChallengeFigure(
    val id: String,
    val emoji: String,
    val name: String,
    val requiredLevel: Int,
    val description: String
)

data class ChallengeScenery(
    val id: String,
    val name: String,
    val requiredLevel: Int,
    val description: String,
    val topColor: Long,
    val bottomColor: Long,
    val groundColor: Long,
    val accentEmoji: String
)

data class ChallengeAccessory(
    val id: String,
    val emoji: String,
    val name: String,
    val requiredLevel: Int,
    val description: String
)

object ChallengeGameEngine {
    val groupIcons = listOf(
        "\uD83D\uDCD8", "\uD83E\uDDE0", "\uD83C\uDFCB\uFE0F", "\uD83C\uDFAF", "\uD83C\uDFA8", "\uD83D\uDCBB",
        "\uD83E\uDDEA", "\uD83E\uDEB4", "\uD83D\uDCC8", "\uD83E\uDDF9", "\uD83C\uDFB5", "\uD83C\uDF1F"
    )

    val shopCatalog = listOf(
        ChallengeFigure("panda_scout", "\uD83D\uDC3C", "Panda Scout", 2, "A tiny panda with a backpack."),
        ChallengeFigure("cat_mage", "\uD83D\uDC31", "Cat Mage", 4, "Small wizard cat with star eyes."),
        ChallengeFigure("frog_knight", "\uD83D\uDC38", "Frog Knight", 6, "A brave frog in polished armor."),
        ChallengeFigure("fox_merchant", "\uD83E\uDD8A", "Fox Merchant", 8, "A clever fox carrying rare loot."),
        ChallengeFigure("bunny_mech", "\uD83D\uDC30", "Bunny Mech", 10, "A compact rabbit mech with boosters."),
        ChallengeFigure("bear_king", "\uD83D\uDC3B", "Bear King", 12, "A chunky ruler with a soft cape."),
        ChallengeFigure("koala_dj", "\uD83D\uDC28", "Koala DJ", 14, "A chill koala spinning tiny records."),
        ChallengeFigure("penguin_sailor", "\uD83D\uDC27", "Penguin Sailor", 16, "A neat little captain with a striped scarf."),
        ChallengeFigure("deer_sage", "\uD83E\uDD8C", "Deer Sage", 18, "A calm guide with moonlit antlers."),
        ChallengeFigure("otter_chef", "\uD83E\uDDA6", "Otter Chef", 20, "A playful cook balancing mini desserts."),
        ChallengeFigure("hamster_hero", "\uD83D\uDC39", "Hamster Hero", 22, "A pocket-sized champion with endless energy.")
    )

    val sceneryCatalog = listOf(
        ChallengeScenery("sunny_meadow", "Sunny Meadow", 1, "The default bright field for your collection.", 0xFFBEE7FF, 0xFFE8F8C8, 0xFF8BCF7B, "\u2600\uFE0F"),
        ChallengeScenery("sunset_camp", "Sunset Camp", 5, "Warm evening light with a cozy camp glow.", 0xFFFFC18C, 0xFFFFE7B2, 0xFFB9785A, "\uD83D\uDD25"),
        ChallengeScenery("moon_garden", "Moon Garden", 9, "A quiet night garden with silver tones.", 0xFF364A7C, 0xFF8AA6D8, 0xFF5D7B68, "\uD83C\uDF19"),
        ChallengeScenery("cloud_castle", "Cloud Castle", 13, "A high-altitude dreamscape with soft clouds.", 0xFFD6E8FF, 0xFFF7FBFF, 0xFFBFD7F2, "\u2601\uFE0F"),
        ChallengeScenery("coral_cove", "Coral Cove", 17, "A bright seaside nook with breezy pastel water.", 0xFF9BE7FF, 0xFFD5FFF5, 0xFF75C7B5, "\uD83D\uDC1A"),
        ChallengeScenery("berry_fair", "Berry Fair", 19, "A playful fairground with candy colors and streamers.", 0xFFFFC0D9, 0xFFFFE8F1, 0xFFE98AA8, "\uD83C\uDFA0"),
        ChallengeScenery("starlight_station", "Starlight Station", 23, "A polished night platform under deep blue lights.", 0xFF24375F, 0xFF667FC5, 0xFF586B84, "\u2728")
    )

    val accessoryCatalog = listOf(
        ChallengeAccessory("lantern_post", "\uD83C\uDFEE", "Lantern Post", 4, "A warm lantern for the edge of the scene."),
        ChallengeAccessory("flower_patch", "\uD83C\uDF3C", "Flower Patch", 6, "A soft patch of flowers for the foreground."),
        ChallengeAccessory("treasure_crate", "\uD83E\uDDF0", "Treasure Crate", 8, "A small loot box near your figures."),
        ChallengeAccessory("tiny_fountain", "\u26F2", "Tiny Fountain", 10, "A decorative centerpiece with calm water."),
        ChallengeAccessory("star_banner", "\uD83C\uDF8F", "Star Banner", 12, "A hanging banner to make the scenery feel alive."),
        ChallengeAccessory("tea_table", "\uD83E\uDED6", "Tea Table", 14, "A tiny setup for a peaceful break."),
        ChallengeAccessory("mushroom_stool", "\uD83C\uDF44", "Mushroom Stool", 16, "A soft woodland seat near the figures."),
        ChallengeAccessory("paper_kites", "\uD83E\uDE81", "Paper Kites", 18, "A cluster of cheerful kites floating above."),
        ChallengeAccessory("arcade_cabinet", "\uD83D\uDD79\uFE0F", "Arcade Cabinet", 20, "A cute cabinet for a more playful scene."),
        ChallengeAccessory("mini_pond", "\uD83E\uDEB7", "Mini Pond", 22, "A reflective pond to soften the foreground.")
    )

    val badgeCatalog = listOf(
        ChallengeBadge("first_step", "\uD83D\uDC63", "First Step", "Complete your first mission."),
        ChallengeBadge("streak_three", "\uD83D\uDD25", "Spark Streak", "Reach a 3-day streak."),
        ChallengeBadge("streak_seven", "\uD83C\uDF1F", "Weekly Flame", "Reach a 7-day streak."),
        ChallengeBadge("streak_fourteen", "\uD83D\uDD25", "Hot Streak", "Reach a 14-day streak."),
        ChallengeBadge("level_five", "\uD83C\uDFC6", "Level Climber", "Reach level 5."),
        ChallengeBadge("level_ten", "\uD83D\uDC51", "Level Royalty", "Reach level 10."),
        ChallengeBadge("level_fifteen", "\uD83C\uDF96\uFE0F", "Summit Runner", "Reach level 15."),
        ChallengeBadge("xp_thousand", "\uD83D\uDC8E", "XP Gem", "Earn 1000 total XP."),
        ChallengeBadge("xp_three_thousand", "\uD83D\uDC8D", "XP Vault", "Earn 3000 total XP."),
        ChallengeBadge("crew_three", "\uD83E\uDD1D", "Crew Captain", "Join a friend group with 3 or more members."),
        ChallengeBadge("figure_collector", "\uD83E\uDDF8", "Figure Collector", "Unlock 3 figures."),
        ChallengeBadge("figure_hoarder", "\uD83E\uDE86", "Figure Hoarder", "Unlock 6 figures."),
        ChallengeBadge("scene_builder", "\uD83C\uDFA8", "Scene Builder", "Own 2 sceneries and 2 accessories."),
        ChallengeBadge("decor_master", "\uD83C\uDF08", "Decor Master", "Own 4 accessories."),
        ChallengeBadge("mission_master", "\uD83C\uDFAF", "Mission Master", "Complete 25 missions."),
        ChallengeBadge("mission_elite", "\uD83E\uDD47", "Mission Elite", "Complete 60 missions."),
        ChallengeBadge("group_founder", "\uD83C\uDFDB\uFE0F", "Group Founder", "Create your first competition group."),
        ChallengeBadge("shop_sprinter", "\uD83D\uDED2", "Shop Sprinter", "Unlock your first scenery, accessory, and figure.")
    )

    fun xpForEffort(effort: Int): Int = when (effort) {
        1 -> 15
        2 -> 30
        3 -> 55
        4 -> 85
        else -> 120
    }

    fun effortLabelRes(effort: Int): Int = when (effort) {
        1 -> R.string.challenge_effort_quick
        2 -> R.string.challenge_effort_standard
        3 -> R.string.challenge_effort_focus
        4 -> R.string.challenge_effort_boss
        else -> R.string.challenge_effort_epic
    }

    fun rankLabelRes(level: Int): Int = when {
        level >= 15 -> R.string.challenge_rank_legend
        level >= 11 -> R.string.challenge_rank_strategist
        level >= 7 -> R.string.challenge_rank_grinder
        level >= 4 -> R.string.challenge_rank_climber
        else -> R.string.challenge_rank_rookie
    }

    fun currentDayOfWeek(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    fun currentDayKey(now: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    fun levelStatus(totalXp: Int): ChallengeLevelStatus {
        var level = 1
        var remaining = totalXp
        var nextRequired = xpRequiredForNextLevel(level)
        while (remaining >= nextRequired) {
            remaining -= nextRequired
            level += 1
            nextRequired = xpRequiredForNextLevel(level)
        }
        return ChallengeLevelStatus(level, totalXp, remaining, nextRequired)
    }

    fun currentStreak(completions: List<ChallengeCompletion>, now: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val distinctDays = completions.map { it.dayKey }.distinct()
        if (distinctDays.isEmpty()) return 0
        var streak = 0
        while (true) {
            val key = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            if (distinctDays.contains(key)) {
                streak += 1
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    fun requiredAccountLevelForFigureLevel(figure: ChallengeFigure, targetFigureLevel: Int): Int {
        return figure.requiredLevel + ((targetFigureLevel - 1) * 3)
    }

    fun maxFigureLevel(): Int = 5

    fun maxEquippedAccessories(): Int = 3

    fun unlockedBadgeIds(
        totalXp: Int,
        level: Int,
        streak: Int,
        completionCount: Int,
        figureCount: Int,
        sceneryCount: Int,
        accessoryCount: Int,
        maxFriendGroupSize: Int
    ): Set<String> {
        val unlocked = mutableSetOf<String>()
        if (completionCount >= 1) unlocked += "first_step"
        if (streak >= 3) unlocked += "streak_three"
        if (streak >= 7) unlocked += "streak_seven"
        if (streak >= 14) unlocked += "streak_fourteen"
        if (level >= 5) unlocked += "level_five"
        if (level >= 10) unlocked += "level_ten"
        if (level >= 15) unlocked += "level_fifteen"
        if (totalXp >= 1000) unlocked += "xp_thousand"
        if (totalXp >= 3000) unlocked += "xp_three_thousand"
        if (maxFriendGroupSize >= 3) unlocked += "crew_three"
        if (figureCount >= 3) unlocked += "figure_collector"
        if (figureCount >= 6) unlocked += "figure_hoarder"
        if (sceneryCount >= 2 && accessoryCount >= 2) unlocked += "scene_builder"
        if (accessoryCount >= 4) unlocked += "decor_master"
        if (completionCount >= 25) unlocked += "mission_master"
        if (completionCount >= 60) unlocked += "mission_elite"
        if (maxFriendGroupSize >= 1) unlocked += "group_founder"
        if (figureCount >= 1 && sceneryCount >= 1 && accessoryCount >= 1) unlocked += "shop_sprinter"
        return unlocked
    }

    fun weekDaySequenceStartingSaturday(now: Long = System.currentTimeMillis()): List<Pair<Int, String>> {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return (0 until 7).map {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val dayKey = currentDayKey(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            dayOfWeek to dayKey
        }
    }

    private fun xpRequiredForNextLevel(level: Int): Int = 180 + ((level - 1) * 70)
}

data class ChallengeLevelStatus(
    val level: Int,
    val totalXp: Int,
    val currentLevelXp: Int,
    val xpToNextLevel: Int
) {
    val progress: Float
        get() = if (xpToNextLevel == 0) 1f else currentLevelXp.toFloat() / xpToNextLevel.toFloat()
}

object ChallengeRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private fun userDocument(userId: String) =
        firestore.collection("users").document(userId)

    private fun groups(userId: String) =
        userDocument(userId).collection("challengeGroups")

    private fun tasks(userId: String) =
        userDocument(userId).collection("challengeTasks")

    private fun completions(userId: String) =
        userDocument(userId).collection("challengeCompletions")

    private fun unlocks(userId: String) =
        userDocument(userId).collection("challengeShopUnlocks")

    private fun sceneryUnlocks(userId: String) =
        userDocument(userId).collection("challengeSceneryUnlocks")

    private fun accessoryUnlocks(userId: String) =
        userDocument(userId).collection("challengeAccessoryUnlocks")

    private fun friendGroupsCollection() =
        firestore.collection("challengeFriendGroups")

    private fun friendRequestsCollection() =
        firestore.collection("challengeFriendRequests")

    private fun publicProfiles() =
        firestore.collection("userPublicProfiles")

    fun loadSnapshot(
        userId: String,
        onSuccess: (ChallengeSnapshot) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val groupsTask = groups(userId).get()
        val tasksTask = tasks(userId).get()
        val completionsTask = completions(userId).get()
        val unlocksTask = unlocks(userId).get()
        val sceneryUnlocksTask = sceneryUnlocks(userId).get()
        val accessoryUnlocksTask = accessoryUnlocks(userId).get()
        val userTask = userDocument(userId).get()
        val friendGroupsTask = friendGroupsCollection()
            .whereArrayContains("memberUids", userId)
            .get()
            .continueWith { task -> if (task.isSuccessful) task.result else null }

        Tasks.whenAllSuccess<Any>(
            groupsTask,
            tasksTask,
            completionsTask,
            unlocksTask,
            sceneryUnlocksTask,
            accessoryUnlocksTask,
            userTask,
            friendGroupsTask
        )
            .addOnSuccessListener { results ->
                val groupSnapshot = results[0] as com.google.firebase.firestore.QuerySnapshot
                val taskSnapshot = results[1] as com.google.firebase.firestore.QuerySnapshot
                val completionSnapshot = results[2] as com.google.firebase.firestore.QuerySnapshot
                val unlockSnapshot = results[3] as com.google.firebase.firestore.QuerySnapshot
                val scenerySnapshot = results[4] as com.google.firebase.firestore.QuerySnapshot
                val accessorySnapshot = results[5] as com.google.firebase.firestore.QuerySnapshot
                val userSnapshot = results[6] as com.google.firebase.firestore.DocumentSnapshot
                val friendGroupSnapshot = results[7] as? com.google.firebase.firestore.QuerySnapshot

                val loadedGroups = groupSnapshot.documents.map { doc ->
                    ChallengeGroup(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        description = doc.getString("description").orEmpty(),
                        icon = doc.getString("icon").orEmpty().ifBlank { "\uD83D\uDCD8" },
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }.sortedBy { it.createdAt }

                val loadedTasks = taskSnapshot.documents.map { doc ->
                    val effort = (doc.getLong("effort") ?: 2L).toInt()
                    ChallengeTask(
                        id = doc.id,
                        groupId = doc.getString("groupId").orEmpty(),
                        title = doc.getString("title").orEmpty(),
                        note = doc.getString("note").orEmpty(),
                        effort = effort,
                        xpReward = (doc.getLong("xpReward") ?: ChallengeGameEngine.xpForEffort(effort).toLong()).toInt(),
                        activeDays = (doc.get("activeDays") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: listOf(2, 3, 4, 5, 6),
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }.sortedBy { it.createdAt }

                val loadedCompletions = completionSnapshot.documents.map { doc ->
                    ChallengeCompletion(
                        id = doc.id,
                        taskId = doc.getString("taskId").orEmpty(),
                        dayKey = doc.getString("dayKey").orEmpty(),
                        completedAt = doc.getLong("completedAt") ?: 0L,
                        xpReward = (doc.getLong("xpReward") ?: 0L).toInt()
                    )
                }.sortedByDescending { it.completedAt }

                val loadedUnlocks = unlockSnapshot.documents.map { doc ->
                    ChallengeShopUnlock(
                        figureId = doc.id,
                        unlockedAt = doc.getLong("unlockedAt") ?: 0L,
                        figureLevel = (doc.getLong("figureLevel") ?: 1L).toInt(),
                        lastUpgradeAt = doc.getLong("lastUpgradeAt") ?: 0L
                    )
                }.sortedBy { it.unlockedAt }

                val loadedSceneryUnlocks = scenerySnapshot.documents.map { doc ->
                    ChallengeSceneryUnlock(
                        sceneryId = doc.id,
                        unlockedAt = doc.getLong("unlockedAt") ?: 0L
                    )
                }.sortedBy { it.unlockedAt }

                val loadedAccessoryUnlocks = accessorySnapshot.documents.map { doc ->
                    ChallengeAccessoryUnlock(
                        accessoryId = doc.id,
                        unlockedAt = doc.getLong("unlockedAt") ?: 0L
                    )
                }.sortedBy { it.unlockedAt }

                val loadedFriendGroups = friendGroupSnapshot?.documents?.map { doc ->
                    ChallengeFriendGroup(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        createdByUid = doc.getString("createdByUid").orEmpty(),
                        createdByUsername = doc.getString("createdByUsername").orEmpty(),
                        memberUids = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        memberUsernames = (doc.get("memberUsernames") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }?.sortedBy { it.createdAt } ?: emptyList()

                onSuccess(
                    ChallengeSnapshot(
                        groups = loadedGroups,
                        tasks = loadedTasks,
                        completions = loadedCompletions,
                        unlocks = loadedUnlocks,
                        sceneryUnlocks = loadedSceneryUnlocks,
                        accessoryUnlocks = loadedAccessoryUnlocks,
                        friendGroups = loadedFriendGroups,
                        displaySettings = ChallengeDisplaySettings(
                            equippedSceneryId = userSnapshot.getString("challengeEquippedSceneryId") ?: "sunny_meadow",
                            equippedAccessoryIds = (userSnapshot.get("challengeEquippedAccessoryIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        )
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun saveGroup(
        userId: String,
        group: ChallengeGroup,
        onSuccess: (ChallengeGroup) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val document = if (group.id.isBlank()) groups(userId).document() else groups(userId).document(group.id)
        val updated = group.copy(id = document.id)
        document.set(
            mapOf(
                "title" to updated.title,
                "description" to updated.description,
                "icon" to updated.icon,
                "createdAt" to updated.createdAt
            )
        ).addOnSuccessListener { onSuccess(updated) }
            .addOnFailureListener(onFailure)
    }

    fun saveTask(
        userId: String,
        task: ChallengeTask,
        onSuccess: (ChallengeTask) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val document = if (task.id.isBlank()) tasks(userId).document() else tasks(userId).document(task.id)
        val updated = task.copy(id = document.id)
        document.set(
            mapOf(
                "groupId" to updated.groupId,
                "title" to updated.title,
                "note" to updated.note,
                "effort" to updated.effort,
                "xpReward" to updated.xpReward,
                "activeDays" to updated.activeDays,
                "createdAt" to updated.createdAt
            )
        ).addOnSuccessListener { onSuccess(updated) }
            .addOnFailureListener(onFailure)
    }

    fun deleteTask(
        userId: String,
        taskId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        completions(userId)
            .whereEqualTo("taskId", taskId)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                batch.delete(tasks(userId).document(taskId))
                snapshot.documents.forEach { document -> batch.delete(document.reference) }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun deleteGroup(
        userId: String,
        groupId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        tasks(userId)
            .whereEqualTo("groupId", groupId)
            .get()
            .addOnSuccessListener { taskSnapshot ->
                val taskIds = taskSnapshot.documents.map { it.id }
                completions(userId)
                    .get()
                    .addOnSuccessListener { completionSnapshot ->
                        val batch = firestore.batch()
                        batch.delete(groups(userId).document(groupId))
                        taskSnapshot.documents.forEach { batch.delete(it.reference) }
                        completionSnapshot.documents
                            .filter { document -> taskIds.contains(document.getString("taskId").orEmpty()) }
                            .forEach { batch.delete(it.reference) }
                        batch.commit()
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener(onFailure)
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun saveCompletionForToday(
        userId: String,
        task: ChallengeTask,
        onSuccess: (ChallengeCompletion) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val dayKey = ChallengeGameEngine.currentDayKey()
        val document = completions(userId).document("${task.id}_$dayKey")
        val completion = ChallengeCompletion(
            id = document.id,
            taskId = task.id,
            dayKey = dayKey,
            completedAt = System.currentTimeMillis(),
            xpReward = task.xpReward
        )
        document.set(
            mapOf(
                "taskId" to completion.taskId,
                "dayKey" to completion.dayKey,
                "completedAt" to completion.completedAt,
                "xpReward" to completion.xpReward
            )
        ).addOnSuccessListener { onSuccess(completion) }
            .addOnFailureListener(onFailure)
    }

    fun deleteCompletion(
        userId: String,
        completionId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        completions(userId).document(completionId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun unlockFigure(
        userId: String,
        figureId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        unlocks(userId).document(figureId)
            .set(
                mapOf(
                    "figureId" to figureId,
                    "unlockedAt" to System.currentTimeMillis(),
                    "figureLevel" to 1,
                    "lastUpgradeAt" to 0L,
                    "nonce" to UUID.randomUUID().toString()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun unlockScenery(
        userId: String,
        sceneryId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        sceneryUnlocks(userId).document(sceneryId)
            .set(
                mapOf(
                    "sceneryId" to sceneryId,
                    "unlockedAt" to System.currentTimeMillis(),
                    "nonce" to UUID.randomUUID().toString()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun unlockAccessory(
        userId: String,
        accessoryId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        accessoryUnlocks(userId).document(accessoryId)
            .set(
                mapOf(
                    "accessoryId" to accessoryId,
                    "unlockedAt" to System.currentTimeMillis(),
                    "nonce" to UUID.randomUUID().toString()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun upgradeFigure(
        userId: String,
        figureId: String,
        newLevel: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        unlocks(userId).document(figureId)
            .update(
                mapOf(
                    "figureLevel" to newLevel,
                    "lastUpgradeAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun equipScenery(
        userId: String,
        sceneryId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        userDocument(userId)
            .set(mapOf("challengeEquippedSceneryId" to sceneryId), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun saveEquippedAccessories(
        userId: String,
        accessoryIds: List<String>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        userDocument(userId)
            .set(mapOf("challengeEquippedAccessoryIds" to accessoryIds), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun searchUserByUsername(
        username: String,
        onSuccess: (ChallengeUserPublic?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val normalized = UserProfileRepository.normalizeUsername(username)
        firestore.collection("usernameIndex").document(normalized)
            .get()
            .addOnSuccessListener { snapshot ->
                val uid = snapshot.getString("uid")
                if (uid.isNullOrBlank()) {
                    onSuccess(null)
                    return@addOnSuccessListener
                }
                publicProfiles().document(uid).get()
                    .addOnSuccessListener { userSnapshot ->
                        onSuccess(
                            ChallengeUserPublic(
                                uid = uid,
                                username = userSnapshot.getString("username").orEmpty(),
                                preferredName = userSnapshot.getString("preferredName").orEmpty()
                            )
                        )
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun saveFriendGroup(
        currentUser: ChallengeUserPublic,
        title: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val document = friendGroupsCollection().document()
        document.set(
            mapOf(
                "title" to title.trim(),
                "createdByUid" to currentUser.uid,
                "createdByUsername" to currentUser.username,
                "memberUids" to listOf(currentUser.uid),
                "memberUsernames" to listOf(currentUser.username),
                "createdAt" to System.currentTimeMillis()
            )
        )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun addFriendToGroup(
        ownerId: String,
        group: ChallengeFriendGroup,
        friend: ChallengeUserPublic,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (group.createdByUid != ownerId) {
            onFailure(IllegalStateException("Only the group owner can add members."))
            return
        }
        val nextUids = (group.memberUids + friend.uid).distinct()
        val nextUsernames = (group.memberUsernames + friend.username).distinct()
        friendGroupsCollection().document(group.id)
            .update(
                mapOf(
                    "memberUids" to nextUids,
                    "memberUsernames" to nextUsernames
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun sendFriendRequest(
        fromUser: ChallengeUserPublic,
        toUser: ChallengeUserPublic,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (fromUser.uid == toUser.uid) {
            onFailure(IllegalArgumentException("You cannot friend yourself."))
            return
        }
        friendRequestsCollection()
            .whereArrayContains("memberUids", fromUser.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val duplicate = snapshot.documents.any { doc ->
                    val members = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                    members.contains(toUser.uid) && doc.getString("status").orEmpty() != "declined"
                }
                if (duplicate) {
                    onFailure(IllegalStateException("A friend connection already exists for that username."))
                    return@addOnSuccessListener
                }
                val document = friendRequestsCollection().document()
                document.set(
                    mapOf(
                        "fromUid" to fromUser.uid,
                        "fromUsername" to fromUser.username,
                        "fromPreferredName" to fromUser.preferredName,
                        "toUid" to toUser.uid,
                        "toUsername" to toUser.username,
                        "status" to "pending",
                        "memberUids" to listOf(fromUser.uid, toUser.uid),
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun respondToFriendRequest(
        requestId: String,
        accept: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        friendRequestsCollection().document(requestId)
            .update(
                mapOf(
                    "status" to if (accept) "accepted" else "declined",
                    "respondedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun loadSocialGraph(
        userId: String,
        onSuccess: (List<ChallengeFriendRequest>, List<ChallengeUserPublic>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        friendRequestsCollection()
            .whereArrayContains("memberUids", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val requests = snapshot.documents.map { doc ->
                    ChallengeFriendRequest(
                        id = doc.id,
                        fromUid = doc.getString("fromUid").orEmpty(),
                        fromUsername = doc.getString("fromUsername").orEmpty(),
                        fromPreferredName = doc.getString("fromPreferredName").orEmpty(),
                        toUid = doc.getString("toUid").orEmpty(),
                        toUsername = doc.getString("toUsername").orEmpty(),
                        status = doc.getString("status").orEmpty().ifBlank { "pending" },
                        memberUids = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }.sortedByDescending { it.createdAt }
                val incoming = requests.filter { it.status == "pending" && it.toUid == userId }
                val acceptedFriendIds = requests
                    .filter { it.status == "accepted" }
                    .map { if (it.fromUid == userId) it.toUid else it.fromUid }
                    .distinct()
                if (acceptedFriendIds.isEmpty()) {
                    onSuccess(incoming, emptyList())
                    return@addOnSuccessListener
                }
                val friends = mutableListOf<ChallengeUserPublic>()
                var remaining = acceptedFriendIds.size
                acceptedFriendIds.forEach { friendId ->
                    publicProfiles().document(friendId).get()
                        .addOnSuccessListener { profile ->
                            friends += ChallengeUserPublic(
                                uid = friendId,
                                username = profile.getString("username").orEmpty(),
                                preferredName = profile.getString("preferredName").orEmpty()
                            )
                            remaining -= 1
                            if (remaining == 0) {
                                onSuccess(incoming, friends.sortedBy { it.username.lowercase() })
                            }
                        }
                        .addOnFailureListener(onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun loadFriendLeaderboard(
        group: ChallengeFriendGroup,
        onSuccess: (List<ChallengeLeaderboardEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (group.memberUids.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val results = mutableListOf<ChallengeLeaderboardEntry>()
        var remaining = group.memberUids.size
        var failed = false

        group.memberUids.forEach { memberUid ->
            publicProfiles().document(memberUid).get()
                .addOnSuccessListener { userSnapshot ->
                    val username = userSnapshot.getString("username").orEmpty()
                    val preferredName = userSnapshot.getString("preferredName").orEmpty()
                    results += ChallengeLeaderboardEntry(
                        uid = memberUid,
                        username = username,
                        preferredName = preferredName,
                        level = (userSnapshot.getLong("challengeLevel") ?: 1L).toInt(),
                        totalXp = (userSnapshot.getLong("challengeTotalXp") ?: 0L).toInt(),
                        streak = (userSnapshot.getLong("challengeStreak") ?: 0L).toInt(),
                        completionCount = (userSnapshot.getLong("challengeCompletionCount") ?: 0L).toInt(),
                        figureCount = (userSnapshot.getLong("challengeFigureCount") ?: 0L).toInt(),
                        sceneryCount = (userSnapshot.getLong("challengeSceneryCount") ?: 0L).toInt(),
                        accessoryCount = (userSnapshot.getLong("challengeAccessoryCount") ?: 0L).toInt(),
                        badgeCount = (userSnapshot.getLong("challengeBadgeCount") ?: 0L).toInt()
                    )
                    remaining -= 1
                    if (!failed && remaining == 0) {
                        onSuccess(
                            results.sortedWith(
                                compareByDescending<ChallengeLeaderboardEntry> { it.totalXp }
                                    .thenByDescending { it.level }
                                    .thenBy { it.username.lowercase() }
                            )
                        )
                    }
                }
                .addOnFailureListener { if (!failed) { failed = true; onFailure(it) } }
        }
    }

    fun syncPublicChallengeStats(
        userId: String,
        snapshot: ChallengeSnapshot
    ) {
        val totalXp = snapshot.completions.sumOf { it.xpReward }
        val level = ChallengeGameEngine.levelStatus(totalXp).level
        val streak = ChallengeGameEngine.currentStreak(snapshot.completions)
        val badgeCount = ChallengeGameEngine.unlockedBadgeIds(
            totalXp = totalXp,
            level = level,
            streak = streak,
            completionCount = snapshot.completions.size,
            figureCount = snapshot.unlocks.size,
            sceneryCount = snapshot.sceneryUnlocks.size,
            accessoryCount = snapshot.accessoryUnlocks.size,
            maxFriendGroupSize = snapshot.friendGroups.maxOfOrNull { it.memberUids.size } ?: 1
        ).size
        publicProfiles().document(userId)
            .set(
                mapOf(
                    "challengeLevel" to level,
                    "challengeTotalXp" to totalXp,
                    "challengeStreak" to streak,
                    "challengeCompletionCount" to snapshot.completions.size,
                    "challengeFigureCount" to snapshot.unlocks.size,
                    "challengeSceneryCount" to snapshot.sceneryUnlocks.size,
                    "challengeAccessoryCount" to snapshot.accessoryUnlocks.size,
                    "challengeBadgeCount" to badgeCount
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
    }
}

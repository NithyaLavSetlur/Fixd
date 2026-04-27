@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.fixd

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ChallengePage(val titleRes: Int) {
    BOARD(R.string.challenge_page_board),
    MANAGE(R.string.challenge_page_manage),
    HISTORY(R.string.challenge_page_history),
    SHOP(R.string.challenge_page_shop),
    GALLERY(R.string.challenge_page_gallery),
    FRIENDS(R.string.challenge_page_friends),
    BADGES(R.string.challenge_page_badges),
    GROUP_FORM(R.string.challenge_group_form_title),
    TASK_FORM(R.string.challenge_task_form_title)
}

private data class ChallengeTaskUi(
    val task: ChallengeTask,
    val group: ChallengeGroup?,
    val completedToday: Boolean,
    val todayCompletionId: String? = null
)

private data class ChallengeTaskStats(
    val task: ChallengeTask,
    val group: ChallengeGroup?,
    val completions: List<ChallengeCompletion>
) {
    val totalCount: Int get() = completions.size
    val lastCompletedAt: Long? get() = completions.maxOfOrNull { it.completedAt }

    fun countSince(days: Int): Int {
        val cutoff = System.currentTimeMillis() - (days * 86_400_000L)
        return completions.count { it.completedAt >= cutoff }
    }

    fun weeklyTrend(): List<Int> {
        val calendar = Calendar.getInstance()
        return (0 until 4).map { weekOffset ->
            val end = calendar.timeInMillis - (weekOffset * 7L * 86_400_000L)
            val start = end - (7L * 86_400_000L)
            completions.count { it.completedAt in start until end }
        }.reversed()
    }
}

private data class OwnedFigureUi(
    val figure: ChallengeFigure,
    val unlock: ChallengeShopUnlock
)

private data class OwnedSceneryUi(
    val scenery: ChallengeScenery,
    val unlock: ChallengeSceneryUnlock
)

private data class OwnedAccessoryUi(
    val accessory: ChallengeAccessory,
    val unlock: ChallengeAccessoryUnlock
)

private data class FriendLeaderboardUi(
    val group: ChallengeFriendGroup,
    val entries: List<ChallengeLeaderboardEntry>
)

private data class ChallengeSessionCache(
    val userId: String,
    val snapshot: ChallengeSnapshot = ChallengeSnapshot(),
    val currentUserPublic: ChallengeUserPublic? = null,
    val incomingFriendRequests: List<ChallengeFriendRequest> = emptyList(),
    val acceptedFriends: List<ChallengeUserPublic> = emptyList(),
    val friendLeaderboards: Map<String, List<ChallengeLeaderboardEntry>> = emptyMap()
)

private object ChallengeRouteMemoryCache {
    var session: ChallengeSessionCache? = null
}

@Composable
fun ChallengesRoute(
    page: ChallengePage,
    onNavigateToPage: (ChallengePage) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val userId = auth.currentUser?.uid
    val cachedSession = remember(userId) {
        ChallengeRouteMemoryCache.session?.takeIf { it.userId == userId }
    }
    var snapshot by remember(userId) { mutableStateOf(cachedSession?.snapshot ?: ChallengeSnapshot()) }
    var loading by remember(userId) { mutableStateOf(cachedSession == null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var editingGroup by remember { mutableStateOf<ChallengeGroup?>(null) }
    var editingTask by remember { mutableStateOf<ChallengeTask?>(null) }
    var expandedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedShopTab by remember { mutableStateOf(0) }
    var currentUserPublic by remember(userId) { mutableStateOf(cachedSession?.currentUserPublic) }
    var selectedFriendsTab by remember { mutableStateOf(0) }
    var friendGroupName by remember { mutableStateOf("") }
    var friendSearchInput by remember { mutableStateOf("") }
    var foundFriend by remember { mutableStateOf<ChallengeUserPublic?>(null) }
    var incomingFriendRequests by remember(userId) { mutableStateOf(cachedSession?.incomingFriendRequests ?: emptyList()) }
    var acceptedFriends by remember(userId) { mutableStateOf(cachedSession?.acceptedFriends ?: emptyList()) }
    var friendLeaderboards by remember(userId) { mutableStateOf(cachedSession?.friendLeaderboards ?: emptyMap()) }
    var friendsLoading by remember { mutableStateOf(false) }
    var selectedFriendGroupId by remember { mutableStateOf<String?>(null) }
    var addFriendsGroup by remember { mutableStateOf<ChallengeFriendGroup?>(null) }

    fun persistCache() {
        val safeUserId = userId ?: return
        ChallengeRouteMemoryCache.session = ChallengeSessionCache(
            userId = safeUserId,
            snapshot = snapshot,
            currentUserPublic = currentUserPublic,
            incomingFriendRequests = incomingFriendRequests,
            acceptedFriends = acceptedFriends,
            friendLeaderboards = friendLeaderboards
        )
    }

    fun reload(showSpinner: Boolean = snapshot.groups.isEmpty() && snapshot.tasks.isEmpty() && snapshot.completions.isEmpty()) {
        val safeUserId = userId ?: return
        if (showSpinner) loading = true
        ChallengeRepository.loadSnapshot(
            userId = safeUserId,
            onSuccess = {
                snapshot = it
                ChallengeRepository.syncPublicChallengeStats(safeUserId, it)
                ChallengeWidgetCache.save(context, ChallengeWidgetCache.fromSnapshot(it))
                ChallengeWidgetUpdater.updateAll(context)
                ChallengeFigureWidgetUpdater.updateAll(context)
                ChallengeBadgeWidgetUpdater.updateAll(context)
                errorText = null
                loading = false
                persistCache()
                if (page == ChallengePage.FRIENDS) {
                    ChallengeRepository.loadSocialGraph(
                        userId = safeUserId,
                        onSuccess = { requests, friends ->
                            incomingFriendRequests = requests
                            acceptedFriends = friends
                            persistCache()
                        },
                        onFailure = { }
                    )
                }
            },
            onFailure = {
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
                loading = false
            }
        )
    }

    LaunchedEffect(userId) {
        if (userId == null) {
            loading = false
            errorText = context.getString(R.string.firebase_not_ready)
        } else {
            if (currentUserPublic == null) {
                UserProfileRepository.getProfile(
                    userId = userId,
                    onSuccess = { profile ->
                        currentUserPublic = ChallengeUserPublic(
                            uid = userId,
                            username = profile?.username.orEmpty(),
                            preferredName = profile?.preferredName.orEmpty()
                        )
                        persistCache()
                    },
                    onFailure = { }
                )
            }
            if (cachedSession == null) {
                reload(showSpinner = true)
            }
        }
    }

    val groupsById = snapshot.groups.associateBy { it.id }
    val tasksByGroupId = remember(snapshot.tasks) { snapshot.tasks.groupBy { it.groupId } }
    val todayDayOfWeek = remember(snapshot.tasks, snapshot.completions) { ChallengeGameEngine.currentDayOfWeek() }
    val todayDayKey = remember(snapshot.completions) { ChallengeGameEngine.currentDayKey() }
    val completionsByTask = remember(snapshot.completions) { snapshot.completions.groupBy { it.taskId } }
    val todayCompletionsByTask = remember(snapshot.completions) {
        snapshot.completions.filter { it.dayKey == todayDayKey }.associateBy { it.taskId }
    }
    val activeMissions = remember(snapshot.tasks, todayCompletionsByTask) {
        snapshot.tasks.filter { todayDayOfWeek in it.activeDays }
            .map { task ->
                ChallengeTaskUi(
                    task = task,
                    group = groupsById[task.groupId],
                    completedToday = todayCompletionsByTask.containsKey(task.id),
                    todayCompletionId = todayCompletionsByTask[task.id]?.id
                )
            }
            .sortedWith(compareBy<ChallengeTaskUi>({ it.completedToday }, { -it.task.xpReward }, { it.task.title.lowercase() }))
    }
    val totalXp = snapshot.completions.sumOf { it.xpReward }
    val levelStatus = remember(totalXp) { ChallengeGameEngine.levelStatus(totalXp) }
    val streak = remember(snapshot.completions) { ChallengeGameEngine.currentStreak(snapshot.completions) }
    val historyStats = remember(snapshot.tasks, snapshot.completions) {
        snapshot.tasks.map { task ->
            ChallengeTaskStats(
                task = task,
                group = groupsById[task.groupId],
                completions = completionsByTask[task.id].orEmpty()
            )
        }.sortedByDescending { it.totalCount }
    }
    val ownedFigures = remember(snapshot.unlocks) {
        snapshot.unlocks.mapNotNull { unlock ->
            ChallengeGameEngine.shopCatalog.firstOrNull { it.id == unlock.figureId }?.let { figure ->
                OwnedFigureUi(figure = figure, unlock = unlock)
            }
        }.sortedWith(compareByDescending<OwnedFigureUi> { it.unlock.figureLevel }.thenBy { it.figure.requiredLevel })
    }
    val ownedSceneries = remember(snapshot.sceneryUnlocks) {
        val actualUnlocks = snapshot.sceneryUnlocks.ifEmpty {
            listOf(ChallengeSceneryUnlock(sceneryId = "sunny_meadow", unlockedAt = 0L))
        }
        actualUnlocks.mapNotNull { unlock ->
            ChallengeGameEngine.sceneryCatalog.firstOrNull { it.id == unlock.sceneryId }?.let { scenery ->
                OwnedSceneryUi(scenery = scenery, unlock = unlock)
            }
        }.sortedBy { it.scenery.requiredLevel }
    }
    val ownedAccessories = remember(snapshot.accessoryUnlocks) {
        snapshot.accessoryUnlocks.mapNotNull { unlock ->
            ChallengeGameEngine.accessoryCatalog.firstOrNull { it.id == unlock.accessoryId }?.let { accessory ->
                OwnedAccessoryUi(accessory = accessory, unlock = unlock)
            }
        }.sortedBy { it.accessory.requiredLevel }
    }
    val maxFriendGroupSize = remember(snapshot.friendGroups) { snapshot.friendGroups.maxOfOrNull { it.memberUids.size } ?: 1 }
    val unlockedBadgeIds = remember(snapshot.completions, totalXp, levelStatus.level, streak, ownedFigures, ownedSceneries, ownedAccessories, maxFriendGroupSize) {
        ChallengeGameEngine.unlockedBadgeIds(
            totalXp = totalXp,
            level = levelStatus.level,
            streak = streak,
            completionCount = snapshot.completions.size,
            figureCount = ownedFigures.size,
            sceneryCount = ownedSceneries.size,
            accessoryCount = ownedAccessories.size,
            maxFriendGroupSize = maxFriendGroupSize
        )
    }
    val unlockedFigureIds = remember(snapshot.unlocks) { snapshot.unlocks.map { it.figureId }.toSet() }
    val unlocksByFigureId = remember(snapshot.unlocks) { snapshot.unlocks.associateBy { it.figureId } }
    val unlockedSceneryIds = remember(ownedSceneries) { ownedSceneries.map { it.scenery.id }.toSet() }
    val unlockedAccessoryIds = remember(snapshot.accessoryUnlocks) { snapshot.accessoryUnlocks.map { it.accessoryId }.toSet() }
    val equippedScenery = remember(snapshot.displaySettings.equippedSceneryId, ownedSceneries) {
        ownedSceneries.firstOrNull { it.scenery.id == snapshot.displaySettings.equippedSceneryId }?.scenery
            ?: ChallengeGameEngine.sceneryCatalog.first { it.id == "sunny_meadow" }
    }
    val equippedAccessories = remember(snapshot.displaySettings.equippedAccessoryIds, ownedAccessories) {
        ownedAccessories.filter { snapshot.displaySettings.equippedAccessoryIds.contains(it.accessory.id) }
    }
    val selectedFriendGroup = remember(snapshot.friendGroups, selectedFriendGroupId) {
        snapshot.friendGroups.firstOrNull { it.id == selectedFriendGroupId }
    }

    LaunchedEffect(page, selectedFriendsTab, snapshot.friendGroups) {
        if (page != ChallengePage.FRIENDS || selectedFriendsTab != 0) {
            friendsLoading = false
            return@LaunchedEffect
        }
        if (snapshot.friendGroups.isEmpty()) {
            friendLeaderboards = emptyMap()
            friendsLoading = false
            return@LaunchedEffect
        }
        friendsLoading = true
        val updated = mutableMapOf<String, List<ChallengeLeaderboardEntry>>()
        var remaining = snapshot.friendGroups.size
        snapshot.friendGroups.forEach { group ->
            ChallengeRepository.loadFriendLeaderboard(
                group = group,
                onSuccess = { entries ->
                    updated[group.id] = entries
                    remaining -= 1
                    if (remaining == 0) {
                        friendLeaderboards = updated.toMap()
                        friendsLoading = false
                        persistCache()
                    }
                },
                onFailure = {
                    remaining -= 1
                    if (remaining == 0) {
                        friendLeaderboards = updated.toMap()
                        friendsLoading = false
                        persistCache()
                    }
                }
            )
        }
    }

    LaunchedEffect(page, userId) {
        val safeUserId = userId ?: return@LaunchedEffect
        if (page == ChallengePage.FRIENDS && incomingFriendRequests.isEmpty() && acceptedFriends.isEmpty()) {
            ChallengeRepository.loadSocialGraph(
                userId = safeUserId,
                onSuccess = { requests, friends ->
                    incomingFriendRequests = requests
                    acceptedFriends = friends
                    persistCache()
                },
                onFailure = { }
            )
        }
    }

    fun saveGroup(title: String, description: String, icon: String) {
        val safeUserId = userId ?: return
        if (title.isBlank()) {
            toast(context, context.getString(R.string.challenge_group_name_required))
            return
        }
        ChallengeRepository.saveGroup(
            userId = safeUserId,
            group = ChallengeGroup(
                title = title.trim(),
                description = description.trim(),
                icon = icon
            ),
            onSuccess = {
                toast(context, context.getString(R.string.challenge_group_saved))
                onNavigateToPage(ChallengePage.MANAGE)
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun saveExistingGroup(group: ChallengeGroup, title: String, description: String, icon: String) {
        val safeUserId = userId ?: return
        if (title.isBlank()) {
            toast(context, context.getString(R.string.challenge_group_name_required))
            return
        }
        ChallengeRepository.saveGroup(
            userId = safeUserId,
            group = group.copy(title = title.trim(), description = description.trim(), icon = icon),
            onSuccess = {
                toast(context, context.getString(R.string.challenge_group_saved))
                editingGroup = null
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun saveTask(groupId: String, title: String, note: String, effort: Int, activeDays: List<Int>) {
        val safeUserId = userId ?: return
        if (groupId.isBlank()) {
            toast(context, context.getString(R.string.challenge_group_pick_required))
            return
        }
        if (title.isBlank()) {
            toast(context, context.getString(R.string.challenge_task_name_required))
            return
        }
        if (activeDays.isEmpty()) {
            toast(context, context.getString(R.string.challenge_days_required))
            return
        }
        ChallengeRepository.saveTask(
            userId = safeUserId,
            task = ChallengeTask(
                groupId = groupId,
                title = title.trim(),
                note = note.trim(),
                effort = effort,
                xpReward = ChallengeGameEngine.xpForEffort(effort),
                activeDays = activeDays.sorted()
            ),
            onSuccess = {
                toast(context, context.getString(R.string.challenge_task_saved))
                onNavigateToPage(ChallengePage.MANAGE)
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun saveExistingTask(task: ChallengeTask, groupId: String, title: String, note: String, effort: Int, activeDays: List<Int>) {
        val safeUserId = userId ?: return
        if (groupId.isBlank()) {
            toast(context, context.getString(R.string.challenge_group_pick_required))
            return
        }
        if (title.isBlank()) {
            toast(context, context.getString(R.string.challenge_task_name_required))
            return
        }
        if (activeDays.isEmpty()) {
            toast(context, context.getString(R.string.challenge_days_required))
            return
        }
        ChallengeRepository.saveTask(
            userId = safeUserId,
            task = task.copy(
                groupId = groupId,
                title = title.trim(),
                note = note.trim(),
                effort = effort,
                xpReward = ChallengeGameEngine.xpForEffort(effort),
                activeDays = activeDays.sorted()
            ),
            onSuccess = {
                toast(context, context.getString(R.string.challenge_task_saved))
                editingTask = null
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun deleteGroup(group: ChallengeGroup) {
        val safeUserId = userId ?: return
        ChallengeRepository.deleteGroup(
            userId = safeUserId,
            groupId = group.id,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_group_deleted))
                editingGroup = null
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun deleteTask(task: ChallengeTask) {
        val safeUserId = userId ?: return
        ChallengeRepository.deleteTask(
            userId = safeUserId,
            taskId = task.id,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_task_deleted))
                editingTask = null
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun toggleMission(taskUi: ChallengeTaskUi) {
        val safeUserId = userId ?: return
        if (taskUi.completedToday) {
            val completionId = taskUi.todayCompletionId ?: return
            ChallengeRepository.deleteCompletion(
                userId = safeUserId,
                completionId = completionId,
                onSuccess = {
                    toast(context, context.getString(R.string.challenge_completion_removed))
                    reload()
                },
                onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
            )
        } else {
            ChallengeRepository.saveCompletionForToday(
                userId = safeUserId,
                task = taskUi.task,
                onSuccess = {
                    toast(context, context.getString(R.string.challenge_reward_body, taskUi.task.title, taskUi.task.xpReward))
                    reload()
                },
                onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
            )
        }
    }

    fun unlockFigure(figure: ChallengeFigure) {
        val safeUserId = userId ?: return
        if (levelStatus.level < figure.requiredLevel) {
            toast(context, context.getString(R.string.challenge_shop_locked_toast, figure.requiredLevel))
            return
        }
        ChallengeRepository.unlockFigure(
            userId = safeUserId,
            figureId = figure.id,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_shop_unlocked, figure.name))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun upgradeFigure(ownedFigure: OwnedFigureUi) {
        val safeUserId = userId ?: return
        val nextLevel = ownedFigure.unlock.figureLevel + 1
        if (nextLevel > ChallengeGameEngine.maxFigureLevel()) {
            toast(context, context.getString(R.string.challenge_shop_max_level))
            return
        }
        val requiredAccountLevel = ChallengeGameEngine.requiredAccountLevelForFigureLevel(ownedFigure.figure, nextLevel)
        if (levelStatus.level < requiredAccountLevel) {
            toast(context, context.getString(R.string.challenge_shop_upgrade_locked_toast, requiredAccountLevel))
            return
        }
        ChallengeRepository.upgradeFigure(
            userId = safeUserId,
            figureId = ownedFigure.figure.id,
            newLevel = nextLevel,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_shop_upgraded, ownedFigure.figure.name, nextLevel))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun unlockScenery(scenery: ChallengeScenery) {
        val safeUserId = userId ?: return
        if (levelStatus.level < scenery.requiredLevel) {
            toast(context, context.getString(R.string.challenge_shop_locked_toast, scenery.requiredLevel))
            return
        }
        ChallengeRepository.unlockScenery(
            userId = safeUserId,
            sceneryId = scenery.id,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_scenery_unlocked, scenery.name))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun unlockAccessory(accessory: ChallengeAccessory) {
        val safeUserId = userId ?: return
        if (levelStatus.level < accessory.requiredLevel) {
            toast(context, context.getString(R.string.challenge_shop_locked_toast, accessory.requiredLevel))
            return
        }
        ChallengeRepository.unlockAccessory(
            userId = safeUserId,
            accessoryId = accessory.id,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_accessory_unlocked, accessory.name))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun equipScenery(scenery: ChallengeScenery) {
        val safeUserId = userId ?: return
        ChallengeRepository.equipScenery(
            userId = safeUserId,
            sceneryId = scenery.id,
            onSuccess = {
                toast(context, context.getString(R.string.challenge_scenery_equipped, scenery.name))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun toggleAccessoryEquip(accessory: ChallengeAccessory) {
        val safeUserId = userId ?: return
        val current = snapshot.displaySettings.equippedAccessoryIds
        val updated = if (current.contains(accessory.id)) {
            current - accessory.id
        } else {
            if (current.size >= ChallengeGameEngine.maxEquippedAccessories()) {
                toast(context, context.getString(R.string.challenge_accessory_limit, ChallengeGameEngine.maxEquippedAccessories()))
                return
            }
            current + accessory.id
        }
        ChallengeRepository.saveEquippedAccessories(
            userId = safeUserId,
            accessoryIds = updated,
            onSuccess = {
                toast(
                    context,
                    context.getString(
                        if (updated.contains(accessory.id)) R.string.challenge_accessory_equipped else R.string.challenge_accessory_unequipped,
                        accessory.name
                    )
                )
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun searchFriend() {
        val query = friendSearchInput.trim()
        if (query.isBlank()) {
            foundFriend = null
            return
        }
        ChallengeRepository.searchUserByUsername(
            username = query,
            onSuccess = { user ->
                foundFriend = user
                if (user == null) {
                    toast(context, context.getString(R.string.challenge_friends_search_none))
                }
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun sendFriendRequest(user: ChallengeUserPublic) {
        val currentUser = currentUserPublic ?: return
        if (user.uid == currentUser.uid) return
        ChallengeRepository.sendFriendRequest(
            fromUser = currentUser,
            toUser = user,
            onSuccess = {
                friendSearchInput = ""
                foundFriend = null
                toast(context, context.getString(R.string.challenge_friends_request_sent))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun createFriendGroup() {
        val currentUser = currentUserPublic ?: return
        val title = friendGroupName.trim()
        if (title.isBlank()) {
            toast(context, context.getString(R.string.challenge_friends_group_name_required))
            return
        }
        ChallengeRepository.saveFriendGroup(
            currentUser = currentUser,
            title = title,
            onSuccess = {
                friendGroupName = ""
                toast(context, context.getString(R.string.challenge_friends_group_created))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun respondToFriendRequest(request: ChallengeFriendRequest, accept: Boolean) {
        ChallengeRepository.respondToFriendRequest(
            requestId = request.id,
            accept = accept,
            onSuccess = {
                toast(
                    context,
                    context.getString(
                        if (accept) R.string.challenge_friends_request_accepted else R.string.challenge_friends_request_declined
                    )
                )
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun addFriendToSelectedGroup(friend: ChallengeUserPublic) {
        val safeUserId = userId ?: return
        val group = addFriendsGroup ?: return
        ChallengeRepository.addFriendToGroup(
            ownerId = safeUserId,
            group = group,
            friend = friend,
            onSuccess = {
                addFriendsGroup = null
                toast(context, context.getString(R.string.challenge_friends_member_added))
                reload()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    editingGroup?.let { group ->
        ChallengeGroupEditDialog(
            group = group,
            onDismiss = { editingGroup = null },
            onSave = { title, description, icon -> saveExistingGroup(group, title, description, icon) },
            onDelete = { deleteGroup(group) }
        )
    }

    editingTask?.let { task ->
        ChallengeTaskEditDialog(
            task = task,
            groups = snapshot.groups,
            onDismiss = { editingTask = null },
            onSave = { groupId, title, note, effort, activeDays ->
                saveExistingTask(task, groupId, title, note, effort, activeDays)
            },
            onDelete = { deleteTask(task) }
        )
    }
    addFriendsGroup?.let { group ->
        ChallengeAddFriendsToGroupDialog(
            group = group,
            friends = acceptedFriends.filterNot { friend -> group.memberUids.contains(friend.uid) },
            onDismiss = { addFriendsGroup = null },
            onAddFriend = ::addFriendToSelectedGroup
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                StreakBadge(streak = streak)
            }
        }
        if (loading) {
            item {
                ChallengeMessageCard(text = stringResource(R.string.challenge_loading))
            }
        } else if (!errorText.isNullOrBlank()) {
            item {
                ChallengeMessageCard(text = errorText.orEmpty(), isError = true)
            }
        } else {
            when (page) {
                ChallengePage.BOARD -> {
                    item {
                        ChallengeLevelCard(levelStatus = levelStatus)
                    }
                    item {
                        Text(
                            text = stringResource(R.string.challenge_today_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (activeMissions.isEmpty()) {
                        item {
                            ChallengeMessageCard(text = stringResource(R.string.challenge_today_empty))
                        }
                    } else {
                        item {
                            ActiveMissionGrid(
                                missions = activeMissions,
                                onToggle = ::toggleMission
                            )
                        }
                    }
                    item {
                        ChallengeSummaryRow(
                            completedToday = activeMissions.count { it.completedToday },
                            totalToday = activeMissions.size,
                            totalTasks = snapshot.tasks.size,
                            totalGroups = snapshot.groups.size
                        )
                    }
                }

                ChallengePage.MANAGE -> {
                    item {
                        ManagePageHeader(
                            onOpenGroupForm = { onNavigateToPage(ChallengePage.GROUP_FORM) },
                            onOpenTaskForm = { onNavigateToPage(ChallengePage.TASK_FORM) },
                            canCreateTask = snapshot.groups.isNotEmpty()
                        )
                    }
                    if (snapshot.groups.isEmpty()) {
                        item {
                            ChallengeMessageCard(text = stringResource(R.string.challenge_empty_groups_body))
                        }
                    } else {
                        items(snapshot.groups, key = { it.id }) { group ->
                            ChallengeManageGroupCard(
                                group = group,
                                tasks = tasksByGroupId[group.id].orEmpty(),
                                expanded = expandedGroupIds.contains(group.id),
                                onClick = { editingGroup = group },
                                onToggleExpanded = {
                                    expandedGroupIds = if (expandedGroupIds.contains(group.id)) {
                                        expandedGroupIds - group.id
                                    } else {
                                        expandedGroupIds + group.id
                                    }
                                },
                                onTaskClick = { editingTask = it }
                            )
                        }
                    }
                }

                ChallengePage.HISTORY -> {
                    item {
                        HistoryOverviewCard(
                            completionCount = snapshot.completions.size,
                            taskCount = snapshot.tasks.size,
                            totalXp = totalXp
                        )
                    }
                    if (historyStats.isEmpty()) {
                        item {
                            ChallengeMessageCard(text = stringResource(R.string.challenge_history_empty))
                        }
                    } else {
                        items(historyStats, key = { it.task.id }) { stat ->
                            ChallengeHistoryCard(stat = stat)
                        }
                    }
                }

                ChallengePage.SHOP -> {
                    item {
                        ShopHeader(
                            level = levelStatus.level,
                            unlockedCount = unlockedFigureIds.size,
                            sceneryCount = unlockedSceneryIds.size,
                            accessoryCount = unlockedAccessoryIds.size
                        )
                    }
                    item {
                        ShopTabs(
                            selectedTab = selectedShopTab,
                            onSelectTab = { selectedShopTab = it }
                        )
                    }
                    when (selectedShopTab) {
                        0 -> items(ChallengeGameEngine.shopCatalog, key = { it.id }) { figure ->
                            ChallengeShopCard(
                                figure = figure,
                                unlock = unlocksByFigureId[figure.id],
                                currentLevel = levelStatus.level,
                                onUnlock = { unlockFigure(figure) },
                                onUpgrade = { ownedFigure -> upgradeFigure(ownedFigure) }
                            )
                        }
                        1 -> items(ChallengeGameEngine.sceneryCatalog, key = { it.id }) { scenery ->
                            ChallengeSceneryShopCard(
                                scenery = scenery,
                                isUnlocked = unlockedSceneryIds.contains(scenery.id),
                                isEquipped = snapshot.displaySettings.equippedSceneryId == scenery.id,
                                currentLevel = levelStatus.level,
                                onUnlock = { unlockScenery(scenery) },
                                onEquip = { equipScenery(scenery) }
                            )
                        }
                        else -> items(ChallengeGameEngine.accessoryCatalog, key = { it.id }) { accessory ->
                            ChallengeAccessoryShopCard(
                                accessory = accessory,
                                isUnlocked = unlockedAccessoryIds.contains(accessory.id),
                                isEquipped = snapshot.displaySettings.equippedAccessoryIds.contains(accessory.id),
                                currentLevel = levelStatus.level,
                                onUnlock = { unlockAccessory(accessory) },
                                onToggleEquip = { toggleAccessoryEquip(accessory) }
                            )
                        }
                    }
                }

                ChallengePage.GALLERY -> {
                    item {
                        ChallengeGalleryHeader(
                            ownedCount = ownedFigures.size,
                            sceneryName = equippedScenery.name,
                            accessoryCount = equippedAccessories.size
                        )
                    }
                    item {
                        ChallengeScenerySceneCard(
                            scenery = equippedScenery,
                            accessories = equippedAccessories,
                            figures = ownedFigures
                        )
                    }
                    if (ownedFigures.isEmpty()) {
                        item {
                            ChallengeMessageCard(text = stringResource(R.string.challenge_gallery_empty))
                        }
                    } else {
                        items(ownedFigures, key = { it.figure.id }) { ownedFigure ->
                            ChallengeGalleryCard(ownedFigure = ownedFigure)
                        }
                    }
                }

                ChallengePage.FRIENDS -> {
                    item {
                        ChallengeFriendsHeader(
                            username = currentUserPublic?.username.orEmpty(),
                            selectedTab = selectedFriendsTab,
                            incomingRequestCount = incomingFriendRequests.size,
                            onSelectTab = {
                                selectedFriendsTab = it
                                if (it == 0 && selectedFriendGroupId != null) {
                                    selectedFriendGroupId = null
                                }
                            }
                        )
                    }
                    if (selectedFriendsTab == 0) {
                        if (selectedFriendGroup != null) {
                            item {
                                ChallengeFriendGroupDetailCard(
                                    group = selectedFriendGroup,
                                    entries = friendLeaderboards[selectedFriendGroup.id].orEmpty(),
                                    isOwner = selectedFriendGroup.createdByUid == currentUserPublic?.uid,
                                    onBack = { selectedFriendGroupId = null },
                                    onAddFriends = { addFriendsGroup = selectedFriendGroup }
                                )
                            }
                        } else if (friendsLoading) {
                            item { ChallengeMessageCard(text = stringResource(R.string.challenge_loading)) }
                        } else {
                            item {
                                ChallengeCreateGroupCard(
                                    groupName = friendGroupName,
                                    onGroupNameChange = { friendGroupName = it },
                                    onCreateGroup = ::createFriendGroup
                                )
                            }
                            if (snapshot.friendGroups.isEmpty()) {
                                item { ChallengeMessageCard(text = stringResource(R.string.challenge_friends_empty_groups)) }
                            }
                            items(snapshot.friendGroups, key = { it.id }) { group ->
                                ChallengeLeaderboardGroupCard(
                                    group = group,
                                    entries = friendLeaderboards[group.id].orEmpty(),
                                    onOpen = { selectedFriendGroupId = group.id }
                                )
                            }
                        }
                    } else {
                        item {
                            ChallengeFindFriendsCard(
                                searchValue = friendSearchInput,
                                onSearchValueChange = { friendSearchInput = it },
                                onSearch = ::searchFriend,
                                foundFriend = foundFriend,
                                currentUserId = currentUserPublic?.uid.orEmpty(),
                                acceptedFriends = acceptedFriends,
                                incomingRequests = incomingFriendRequests,
                                onAddFriend = ::sendFriendRequest,
                                onRespondToRequest = ::respondToFriendRequest
                            )
                        }
                    }
                }

                ChallengePage.BADGES -> {
                    item {
                        ChallengeBadgesHeader(
                            unlockedCount = unlockedBadgeIds.size,
                            totalCount = ChallengeGameEngine.badgeCatalog.size
                        )
                    }
                    item {
                        ChallengeBadgesGrid(unlockedBadgeIds = unlockedBadgeIds)
                    }
                }

                ChallengePage.GROUP_FORM -> {
                    item {
                        ChallengeGroupForm(
                            onBack = { onNavigateToPage(ChallengePage.MANAGE) },
                            onSave = ::saveGroup
                        )
                    }
                }

                ChallengePage.TASK_FORM -> {
                    item {
                        ChallengeTaskForm(
                            groups = snapshot.groups,
                            onBack = { onNavigateToPage(ChallengePage.MANAGE) },
                            onSave = ::saveTask
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.challenge_streak_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ChallengeLevelCard(levelStatus: ChallengeLevelStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = levelStatus.level.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = stringResource(R.string.challenge_level_label, levelStatus.level),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(ChallengeGameEngine.rankLabelRes(levelStatus.level)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.challenge_progress_label, levelStatus.currentLevelXp, levelStatus.xpToNextLevel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { levelStatus.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.challenge_total_xp_line, levelStatus.totalXp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveMissionGrid(
    missions: List<ChallengeTaskUi>,
    onToggle: (ChallengeTaskUi) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        missions.forEach { mission ->
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (mission.completedToday) 0.29f else 0.48f)
                    .clickable { onToggle(mission) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (mission.completedToday) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(if (mission.completedToday) 9.dp else 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mission.group?.icon ?: "📘",
                            style = if (mission.completedToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${mission.task.xpReward} XP",
                            style = if (mission.completedToday) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            color = if (mission.completedToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(if (mission.completedToday) 4.dp else 8.dp))
                    Text(
                        text = mission.task.title,
                        style = if (mission.completedToday) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (mission.completedToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(if (mission.completedToday) 4.dp else 8.dp))
                    Text(
                        text = stringResource(if (mission.completedToday) R.string.challenge_tile_done else R.string.challenge_tile_tap),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeSummaryRow(
    completedToday: Int,
    totalToday: Int,
    totalTasks: Int,
    totalGroups: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChallengeMiniStat(modifier = Modifier.weight(1f), value = "$completedToday/$totalToday", label = stringResource(R.string.challenge_done_today))
        ChallengeMiniStat(modifier = Modifier.weight(1f), value = totalTasks.toString(), label = stringResource(R.string.challenge_total_tasks))
        ChallengeMiniStat(modifier = Modifier.weight(1f), value = totalGroups.toString(), label = stringResource(R.string.challenge_groups_short))
    }
}

@Composable
private fun ChallengeMiniStat(modifier: Modifier, value: String, label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ManagePageHeader(
    onOpenGroupForm: () -> Unit,
    onOpenTaskForm: () -> Unit,
    canCreateTask: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenGroupForm, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.challenge_add_group))
                }
                OutlinedButton(onClick = onOpenTaskForm, enabled = canCreateTask, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.challenge_add_task))
                }
            }
        }
    }
}

@Composable
private fun ChallengeManageGroupCard(
    group: ChallengeGroup,
    tasks: List<ChallengeTask>,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggleExpanded: () -> Unit,
    onTaskClick: (ChallengeTask) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = group.icon, style = MaterialTheme.typography.headlineMedium)
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(text = group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (group.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.challenge_manage_task_count, tasks.size)) })
                Spacer(modifier = Modifier.width(8.dp))
                Card(
                    modifier = Modifier.clickable(onClick = onToggleExpanded),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (expanded) "\u02C4" else "\u02C5",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(14.dp))
                if (tasks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.challenge_empty_tasks_filtered_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    tasks.sortedBy { it.title.lowercase() }.forEachIndexed { index, task ->
                        ChallengeManageTaskInlineCard(task = task, group = group, onClick = { onTaskClick(task) })
                        if (index != tasks.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeManageTaskInlineCard(task: ChallengeTask, group: ChallengeGroup?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = group?.icon ?: "📘", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = task.title,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.challenge_manage_task_meta, group?.title ?: stringResource(R.string.challenge_unknown_group), task.xpReward),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatActiveDays(task.activeDays, LocalContext.current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = task.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun HistoryOverviewCard(completionCount: Int, taskCount: Int, totalXp: Int) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChallengeMiniStat(modifier = Modifier.weight(1f), value = completionCount.toString(), label = stringResource(R.string.challenge_history_runs))
            ChallengeMiniStat(modifier = Modifier.weight(1f), value = taskCount.toString(), label = stringResource(R.string.challenge_total_tasks))
            ChallengeMiniStat(modifier = Modifier.weight(1f), value = totalXp.toString(), label = stringResource(R.string.challenge_total_xp_title))
        }
    }
}

@Composable
private fun ChallengeHistoryCard(stat: ChallengeTaskStats) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val weekStatuses = remember(stat.completions, stat.task.activeDays) {
        val todayKey = ChallengeGameEngine.currentDayKey()
        ChallengeGameEngine.weekDaySequenceStartingSaturday().map { (dayOfWeek, dayKey) ->
            val isAssigned = stat.task.activeDays.contains(dayOfWeek)
            val isCompleted = stat.completions.any { it.dayKey == dayKey }
            when {
                isCompleted -> ChallengeDayStatus(dayLabel(dayOfWeek), "\u2713", ChallengeDayStatusKind.COMPLETED)
                !isAssigned -> ChallengeDayStatus(dayLabel(dayOfWeek), "\u2212", ChallengeDayStatusKind.NOT_ASSIGNED)
                dayKey > todayKey -> ChallengeDayStatus(dayLabel(dayOfWeek), "", ChallengeDayStatusKind.PENDING)
                else -> ChallengeDayStatus(dayLabel(dayOfWeek), "\u2715", ChallengeDayStatusKind.MISSED)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stat.group?.icon ?: "📘", style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(text = stat.task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = stringResource(R.string.challenge_history_group_meta, stat.group?.title ?: context.getString(R.string.challenge_unknown_group)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(onClick = {}, enabled = false, label = { Text("${stat.totalCount}") })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChallengeMiniStat(modifier = Modifier.weight(1f), value = stat.countSince(7).toString(), label = stringResource(R.string.challenge_history_week))
                ChallengeMiniStat(modifier = Modifier.weight(1f), value = stat.countSince(30).toString(), label = stringResource(R.string.challenge_history_month))
                ChallengeMiniStat(modifier = Modifier.weight(1f), value = "${stat.task.xpReward}", label = "XP")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "This week", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekStatuses.forEach { status ->
                    val statusColor = when (status.kind) {
                        ChallengeDayStatusKind.COMPLETED -> Color(0xFF2E9F5B)
                        ChallengeDayStatusKind.MISSED -> Color(0xFFD64545)
                        ChallengeDayStatusKind.PENDING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        ChallengeDayStatusKind.NOT_ASSIGNED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                    }
                    val fillColor = when (status.kind) {
                        ChallengeDayStatusKind.COMPLETED, ChallengeDayStatusKind.MISSED -> statusColor.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .border(width = 1.5.dp, color = statusColor, shape = CircleShape)
                                .background(fillColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = status.symbol,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stat.lastCompletedAt?.let {
                    stringResource(R.string.challenge_history_last_completed, formatter.format(Date(it)))
                } ?: stringResource(R.string.challenge_history_never_completed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatActiveDays(stat.task.activeDays, context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShopHeader(level: Int, unlockedCount: Int, sceneryCount: Int, accessoryCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_shop_level_line, level, unlockedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.challenge_shop_inventory_line, sceneryCount, accessoryCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShopTabs(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.challenge_shop_figures_title),
        stringResource(R.string.challenge_shop_sceneries_title),
        stringResource(R.string.challenge_shop_accessories_title)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedTab == index,
                onClick = { onSelectTab(index) },
                label = { Text(label) }
            )
        }
    }
}

private data class ChallengeDayStatus(
    val label: String,
    val symbol: String,
    val kind: ChallengeDayStatusKind
)

private enum class ChallengeDayStatusKind {
    COMPLETED,
    MISSED,
    PENDING,
    NOT_ASSIGNED
}

private fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    Calendar.SATURDAY -> "S"
    Calendar.SUNDAY -> "S"
    Calendar.MONDAY -> "M"
    Calendar.TUESDAY -> "T"
    Calendar.WEDNESDAY -> "W"
    Calendar.THURSDAY -> "T"
    Calendar.FRIDAY -> "F"
    else -> "?"
}

@Composable
private fun ChallengeShopCard(
    figure: ChallengeFigure,
    unlock: ChallengeShopUnlock?,
    currentLevel: Int,
    onUnlock: () -> Unit,
    onUpgrade: (OwnedFigureUi) -> Unit
) {
    val isUnlocked = unlock != null
    val canUnlock = currentLevel >= figure.requiredLevel
    val figureLevel = unlock?.figureLevel ?: 0
    val nextFigureLevel = (unlock?.figureLevel ?: 0) + 1
    val canUpgrade = unlock != null &&
        figureLevel < ChallengeGameEngine.maxFigureLevel() &&
        currentLevel >= ChallengeGameEngine.requiredAccountLevelForFigureLevel(figure, nextFigureLevel)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = figure.emoji, style = MaterialTheme.typography.headlineMedium)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(text = figure.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = figure.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (unlock == null) {
                        stringResource(R.string.challenge_shop_required_level, figure.requiredLevel)
                    } else {
                        stringResource(
                            R.string.challenge_shop_figure_level_line,
                            unlock.figureLevel,
                            ChallengeGameEngine.maxFigureLevel()
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canUnlock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unlock != null && unlock.figureLevel < ChallengeGameEngine.maxFigureLevel()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.challenge_shop_upgrade_requirement,
                            ChallengeGameEngine.requiredAccountLevelForFigureLevel(figure, nextFigureLevel),
                            nextFigureLevel
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canUpgrade) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 0.dp), horizontalArrangement = Arrangement.End) {
            if (unlock != null) {
                if (unlock.figureLevel >= ChallengeGameEngine.maxFigureLevel()) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.challenge_shop_maxed)) })
                } else {
                    OutlinedButton(onClick = { onUpgrade(OwnedFigureUi(figure, unlock)) }, enabled = canUpgrade) {
                        Text(
                            stringResource(
                                if (canUpgrade) R.string.challenge_shop_upgrade else R.string.challenge_shop_locked_button
                            )
                        )
                    }
                }
            } else {
                OutlinedButton(onClick = onUnlock, enabled = canUnlock) {
                    Text(
                        stringResource(
                            if (canUnlock) R.string.challenge_shop_unlock else R.string.challenge_shop_locked_button
                        )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun ChallengeSceneryShopCard(
    scenery: ChallengeScenery,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    currentLevel: Int,
    onUnlock: () -> Unit,
    onEquip: () -> Unit
) {
    val canUnlock = currentLevel >= scenery.requiredLevel
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(scenery.topColor), Color(scenery.bottomColor), Color(scenery.groundColor))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = scenery.accentEmoji, style = MaterialTheme.typography.headlineMedium)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(text = scenery.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = scenery.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.challenge_shop_required_level, scenery.requiredLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canUnlock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
            when {
                !isUnlocked -> OutlinedButton(onClick = onUnlock, enabled = canUnlock) {
                    Text(stringResource(if (canUnlock) R.string.challenge_shop_unlock else R.string.challenge_shop_locked_button))
                }
                isEquipped -> AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.challenge_shop_equipped)) })
                else -> OutlinedButton(onClick = onEquip) {
                    Text(stringResource(R.string.challenge_shop_equip))
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun ChallengeAccessoryShopCard(
    accessory: ChallengeAccessory,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    currentLevel: Int,
    onUnlock: () -> Unit,
    onToggleEquip: () -> Unit
) {
    val canUnlock = currentLevel >= accessory.requiredLevel
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = accessory.emoji, style = MaterialTheme.typography.headlineMedium)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(text = accessory.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = accessory.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.challenge_shop_required_level, accessory.requiredLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canUnlock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
            when {
                !isUnlocked -> OutlinedButton(onClick = onUnlock, enabled = canUnlock) {
                    Text(stringResource(if (canUnlock) R.string.challenge_shop_unlock else R.string.challenge_shop_locked_button))
                }
                isEquipped -> OutlinedButton(onClick = onToggleEquip) {
                    Text(stringResource(R.string.challenge_shop_unequip))
                }
                else -> OutlinedButton(onClick = onToggleEquip) {
                    Text(stringResource(R.string.challenge_shop_equip))
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun ChallengeFriendsHeader(
    username: String,
    selectedTab: Int,
    incomingRequestCount: Int,
    onSelectTab: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_friends_username_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "@$username",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.challenge_friends_username_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (incomingRequestCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.challenge_friends_request_count, incomingRequestCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    label = { Text(stringResource(R.string.challenge_friends_tab_leaderboard)) }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    label = { Text(stringResource(R.string.challenge_friends_tab_find)) }
                )
            }
        }
    }
}

@Composable
private fun ChallengeLeaderboardGroupCard(
    group: ChallengeFriendGroup,
    entries: List<ChallengeLeaderboardEntry>,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_friends_leaderboard_title, group.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = group.memberUsernames.joinToString("  ") { "@$it" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.challenge_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.challenge_friends_group_preview,
                        entries.first().username,
                        entries.first().totalXp
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LeaderboardStatChip(label: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChallengeFriendGroupDetailCard(
    group: ChallengeFriendGroup,
    entries: List<ChallengeLeaderboardEntry>,
    isOwner: Boolean,
    onBack: () -> Unit,
    onAddFriends: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.challenge_friends_back_to_groups))
                }
                if (isOwner) {
                    Button(onClick = onAddFriends) {
                        Text(stringResource(R.string.challenge_friends_add_friends))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.challenge_friends_leaderboard_title, group.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = group.memberUsernames.joinToString("  ") { "@$it" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.challenge_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. @${entry.username}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.preferredName.ifBlank { entry.username },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LeaderboardStatChip(stringResource(R.string.challenge_friends_stat_level, entry.level))
                                LeaderboardStatChip(stringResource(R.string.challenge_friends_stat_xp, entry.totalXp))
                                LeaderboardStatChip(stringResource(R.string.challenge_friends_stat_streak, entry.streak))
                                LeaderboardStatChip(stringResource(R.string.challenge_friends_stat_figures, entry.figureCount))
                                LeaderboardStatChip(stringResource(R.string.challenge_friends_stat_badges, entry.badgeCount))
                            }
                        }
                    }
                    if (index != entries.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ChallengeCreateGroupCard(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_friends_group_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = groupName,
                onValueChange = onGroupNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_friends_group_name_label)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth(), enabled = groupName.isNotBlank()) {
                Text(stringResource(R.string.challenge_friends_create_group))
            }
        }
    }
}

@Composable
private fun ChallengeFindFriendsCard(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    foundFriend: ChallengeUserPublic?,
    currentUserId: String,
    acceptedFriends: List<ChallengeUserPublic>,
    incomingRequests: List<ChallengeFriendRequest>,
    onAddFriend: (ChallengeUserPublic) -> Unit,
    onRespondToRequest: (ChallengeFriendRequest, Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_friends_find_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchValue,
                onValueChange = onSearchValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_friends_search_label)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.challenge_friends_search_button))
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (foundFriend == null) {
                Text(
                    text = stringResource(R.string.challenge_friends_empty_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "@${foundFriend.username}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = foundFriend.preferredName.ifBlank { foundFriend.username },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onAddFriend(foundFriend) },
                            enabled = foundFriend.uid != currentUserId,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.challenge_friends_send_request))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.challenge_friends_requests_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (incomingRequests.isEmpty()) {
                Text(
                    text = stringResource(R.string.challenge_friends_empty_requests),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                incomingRequests.forEach { request ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "@${request.fromUsername}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = request.fromPreferredName.ifBlank { request.fromUsername },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRespondToRequest(request, true) }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.challenge_friends_accept))
                                }
                                OutlinedButton(onClick = { onRespondToRequest(request, false) }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.challenge_friends_decline))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.challenge_friends_accepted_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (acceptedFriends.isEmpty()) {
                Text(
                    text = stringResource(R.string.challenge_friends_empty_accepted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                acceptedFriends.forEach { friend ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "@${friend.username}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = friend.preferredName.ifBlank { friend.username },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ChallengeAddFriendsToGroupDialog(
    group: ChallengeFriendGroup,
    friends: List<ChallengeUserPublic>,
    onDismiss: () -> Unit,
    onAddFriend: (ChallengeUserPublic) -> Unit
) {
    FixdDialog(
        title = stringResource(R.string.challenge_friends_add_to_group_title, group.title),
        onDismiss = onDismiss
    ) {
        Column {
            if (friends.isEmpty()) {
                Text(
                    text = stringResource(R.string.challenge_friends_no_available_friends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                friends.forEach { friend ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "@${friend.username}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = friend.preferredName.ifBlank { friend.username },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { onAddFriend(friend) }) {
                                Text(stringResource(R.string.challenge_friends_add_friends))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ChallengeBadgesHeader(
    unlockedCount: Int,
    totalCount: Int
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_badges_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.challenge_badges_summary, unlockedCount, totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChallengeBadgesGrid(
    unlockedBadgeIds: Set<String>
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChallengeGameEngine.badgeCatalog.forEach { badge ->
            val unlocked = unlockedBadgeIds.contains(badge.id)
            Card(
                modifier = Modifier.fillMaxWidth(0.48f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (unlocked) badge.emoji else "\u25CF",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (unlocked) badge.name else stringResource(R.string.challenge_badges_locked),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeGalleryHeader(ownedCount: Int, sceneryName: String, accessoryCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.challenge_gallery_count, ownedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.challenge_gallery_scene_line, sceneryName, accessoryCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChallengeScenerySceneCard(
    scenery: ChallengeScenery,
    accessories: List<OwnedAccessoryUi>,
    figures: List<OwnedFigureUi>
) {
    val displayFigures = figures.take(4)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(scenery.topColor), Color(scenery.bottomColor), Color(scenery.groundColor))
                    )
                )
        ) {
            Text(
                text = scenery.accentEmoji,
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
                style = MaterialTheme.typography.headlineLarge
            )
            if (accessories.isNotEmpty()) {
                Text(
                    text = accessories.joinToString("  ") { it.accessory.emoji },
                    modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(78.dp)
                    .background(Color(scenery.groundColor).copy(alpha = 0.55f))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                displayFigures.forEach { ownedFigure ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = ownedFigure.figure.emoji, style = MaterialTheme.typography.displayMedium)
                        Text(
                            text = "Lv ${ownedFigure.unlock.figureLevel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeGalleryCard(ownedFigure: OwnedFigureUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = stringResource(
                            R.string.challenge_shop_figure_level_line,
                            ownedFigure.unlock.figureLevel,
                            ChallengeGameEngine.maxFigureLevel()
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = ownedFigure.figure.emoji,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.displayLarge
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = ownedFigure.figure.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = ownedFigure.figure.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChallengeGroupForm(
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(ChallengeGameEngine.groupIcons.first()) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.challenge_back_to_manage))
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_group_name_label)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_group_description_label)) },
                minLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.challenge_group_icon_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeGameEngine.groupIcons.forEach { icon ->
                    FilterChip(
                        selected = selectedIcon == icon,
                        onClick = { selectedIcon = icon },
                        label = { Text(icon) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = { onSave(title, description, selectedIcon) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.challenge_save_group))
            }
        }
    }
}

@Composable
private fun ChallengeTaskForm(
    groups: List<ChallengeGroup>,
    onBack: () -> Unit,
    onSave: (String, String, String, Int, List<Int>) -> Unit
) {
    var selectedGroupId by remember(groups) { mutableStateOf(groups.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var effort by remember { mutableStateOf(2) }
    var activeDays by remember { mutableStateOf(setOf(2, 3, 4, 5, 6)) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp)) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.challenge_back_to_manage))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = stringResource(R.string.challenge_task_group_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    FilterChip(
                        selected = selectedGroupId == group.id,
                        onClick = { selectedGroupId = group.id },
                        label = { Text("${group.icon} ${group.title}") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_task_name_label)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_task_note_label)) },
                minLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.challenge_effort_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { option ->
                    FilterChip(
                        selected = effort == option,
                        onClick = { effort = option },
                        label = {
                            Text(
                                stringResource(
                                    R.string.challenge_effort_chip,
                                    stringResource(ChallengeGameEngine.effortLabelRes(option)),
                                    ChallengeGameEngine.xpForEffort(option)
                                )
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.challenge_task_days_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                challengeWeekdays().forEach { (day, label) ->
                    FilterChip(
                        selected = activeDays.contains(day),
                        onClick = {
                            activeDays = if (activeDays.contains(day)) activeDays - day else activeDays + day
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = { onSave(selectedGroupId, title, note, effort, activeDays.toList()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = groups.isNotEmpty()
            ) {
                Text(stringResource(R.string.challenge_save_task))
            }
        }
    }
}

@Composable
private fun ChallengeGroupEditDialog(
    group: ChallengeGroup,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(group.id) { mutableStateOf(group.title) }
    var description by remember(group.id) { mutableStateOf(group.description) }
    var selectedIcon by remember(group.id) { mutableStateOf(group.icon) }
    val trimmedTitle = title.trim()
    val trimmedDescription = description.trim()
    val canSave =
        trimmedTitle.isNotBlank() && (
            trimmedTitle != group.title ||
                trimmedDescription != group.description ||
                selectedIcon != group.icon
            )

    FixdDialog(
        title = stringResource(R.string.challenge_edit_group_title),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(trimmedTitle, trimmedDescription, selectedIcon) }
            ) {
                Text(stringResource(R.string.challenge_save_changes))
            }
        },
        secondaryAction = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.challenge_delete))
            }
        }
    ) {
        Column {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_group_name_label)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_group_description_label)) },
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeGameEngine.groupIcons.forEach { icon ->
                    FilterChip(
                        selected = selectedIcon == icon,
                        onClick = { selectedIcon = icon },
                        label = { Text(icon) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeTaskEditDialog(
    task: ChallengeTask,
    groups: List<ChallengeGroup>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, List<Int>) -> Unit,
    onDelete: () -> Unit
) {
    var selectedGroupId by remember(task.id, groups) { mutableStateOf(task.groupId) }
    var title by remember(task.id) { mutableStateOf(task.title) }
    var note by remember(task.id) { mutableStateOf(task.note) }
    var effort by remember(task.id) { mutableStateOf(task.effort) }
    var activeDays by remember(task.id) { mutableStateOf(task.activeDays.toSet()) }
    val trimmedTitle = title.trim()
    val trimmedNote = note.trim()
    val canSave =
        trimmedTitle.isNotBlank() &&
            activeDays.isNotEmpty() && (
                selectedGroupId != task.groupId ||
                    trimmedTitle != task.title ||
                    trimmedNote != task.note ||
                    effort != task.effort ||
                    activeDays.toSet() != task.activeDays.toSet()
                )

    FixdDialog(
        title = stringResource(R.string.challenge_edit_task_title),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(selectedGroupId, trimmedTitle, trimmedNote, effort, activeDays.toList()) }
            ) {
                Text(stringResource(R.string.challenge_save_changes))
            }
        },
        secondaryAction = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.challenge_delete))
            }
        }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    FilterChip(
                        selected = selectedGroupId == group.id,
                        onClick = { selectedGroupId = group.id },
                        label = { Text("${group.icon} ${group.title}") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_task_name_label)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.challenge_task_note_label)) },
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { option ->
                    FilterChip(
                        selected = effort == option,
                        onClick = { effort = option },
                        label = { Text(stringResource(R.string.challenge_effort_chip, stringResource(ChallengeGameEngine.effortLabelRes(option)), ChallengeGameEngine.xpForEffort(option))) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                challengeWeekdays().forEach { (day, label) ->
                    FilterChip(
                        selected = activeDays.contains(day),
                        onClick = {
                            activeDays = if (activeDays.contains(day)) activeDays - day else activeDays + day
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeMessageCard(text: String, isError: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun challengeWeekdays(): List<Pair<Int, String>> = listOf(
    Calendar.SUNDAY to "Sun",
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat"
)

private fun formatActiveDays(days: List<Int>, context: Context): String {
    val labels = challengeWeekdays().filter { days.contains(it.first) }.joinToString(", ") { it.second }
    return context.getString(R.string.challenge_active_days_line, labels)
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

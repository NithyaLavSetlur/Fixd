package com.example.fixd

object PremiumAccess {
    const val FREE_AVAILABLE_LIMIT = 4
    const val FREE_TAB_BAR_LIMIT = 3
    const val PREMIUM_TAB_BAR_LIMIT = 5

    fun maxAvailableProblems(isPremium: Boolean): Int {
        return if (isPremium) ProblemArea.entries.size else FREE_AVAILABLE_LIMIT
    }

    fun maxTabBarProblems(isPremium: Boolean): Int {
        return if (isPremium) PREMIUM_TAB_BAR_LIMIT else FREE_TAB_BAR_LIMIT
    }

    fun sanitizeAvailableProblems(problems: List<String>, isPremium: Boolean): List<String> {
        val sanitized = problems.mapNotNull { ProblemArea.fromName(it)?.name }.distinct()
        return sanitized.take(maxAvailableProblems(isPremium))
    }

    fun sanitizeTabBarProblems(
        tabBarProblems: List<String>,
        availableProblems: List<String>,
        isPremium: Boolean,
        allowEmpty: Boolean = false
    ): List<String> {
        val allowed = sanitizeAvailableProblems(availableProblems, isPremium).toSet()
        val sanitized = tabBarProblems.mapNotNull { ProblemArea.fromName(it)?.name }
            .distinct()
            .filter { it in allowed }
            .take(maxTabBarProblems(isPremium))
        return if (sanitized.isNotEmpty() || allowEmpty) sanitized else allowed.take(maxTabBarProblems(isPremium)).toList()
    }

    fun sanitizeProfile(profile: UserProfile): UserProfile {
        val available = sanitizeAvailableProblems(
            if (profile.availableProblems.isNotEmpty()) profile.availableProblems else profile.selectedProblems,
            profile.isPremium
        ).ifEmpty { listOf(ProblemArea.WAKE_UP.name) }
        val selected = sanitizeTabBarProblems(
            tabBarProblems = profile.selectedProblems,
            availableProblems = available,
            isPremium = profile.isPremium,
            allowEmpty = profile.selectedProblems.isEmpty()
        )
        return profile.copy(
            availableProblems = available,
            selectedProblems = selected
        )
    }
}

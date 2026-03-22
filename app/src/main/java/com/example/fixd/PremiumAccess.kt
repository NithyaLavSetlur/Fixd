package com.example.fixd

object PremiumAccess {
    const val FREE_AVAILABLE_LIMIT = 3
    const val FREE_TAB_BAR_LIMIT = 2
    const val PREMIUM_TAB_BAR_LIMIT = 5

    fun maxAvailableProblems(isPremium: Boolean): Int {
        return if (isPremium) ProblemArea.entries.size else FREE_AVAILABLE_LIMIT
    }

    fun maxTabBarProblems(isPremium: Boolean): Int {
        return if (isPremium) PREMIUM_TAB_BAR_LIMIT else FREE_TAB_BAR_LIMIT
    }

    fun sanitizeAvailableProblems(problems: List<String>, isPremium: Boolean): List<String> {
        return problems.distinct().take(maxAvailableProblems(isPremium))
    }

    fun sanitizeTabBarProblems(tabBarProblems: List<String>, availableProblems: List<String>, isPremium: Boolean): List<String> {
        val allowed = availableProblems.toSet()
        return tabBarProblems.distinct().filter { it in allowed }.take(maxTabBarProblems(isPremium))
    }
}

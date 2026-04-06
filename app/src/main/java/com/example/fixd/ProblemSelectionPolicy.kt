package com.example.fixd

object ProblemSelectionPolicy {
    fun activatedProblemsFromSelections(selected: List<ProblemArea>, isPremium: Boolean): List<String> {
        return PremiumAccess.sanitizeAvailableProblems(selected.map { it.name }, isPremium)
    }

    fun displayedProblemsForActivation(availableProblems: List<String>, isPremium: Boolean): List<String> {
        return PremiumAccess.sanitizeTabBarProblems(availableProblems, availableProblems, isPremium)
    }
}

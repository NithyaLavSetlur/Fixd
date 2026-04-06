package com.example.fixd

import com.google.firebase.auth.FirebaseUser

object PremiumEntitlement {
    private const val TESTER_HANDLE = "nithyasetlur@gmail.com"

    fun hasTesterPremium(user: FirebaseUser?, profile: UserProfile? = null): Boolean {
        if (matchesTesterEmail(user?.email)) return true
        val candidates = listOfNotNull(
            user?.displayName,
            user?.email?.substringBefore("@"),
            profile?.preferredName
        )
        return candidates.any { it.equals(TESTER_HANDLE, ignoreCase = true) }
    }

    private fun matchesTesterEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return normalizeGmail(email) == normalizeGmail(TESTER_HANDLE)
    }

    private fun normalizeGmail(email: String): String {
        val trimmed = email.trim().lowercase()
        val parts = trimmed.split("@", limit = 2)
        if (parts.size != 2) return trimmed
        val domain = when (parts[1]) {
            "googlemail.com" -> "gmail.com"
            else -> parts[1]
        }
        if (domain != "gmail.com") return "${
            parts[0]
        }@$domain"
        val local = parts[0]
            .substringBefore("+")
            .replace(".", "")
        return "$local@$domain"
    }

    fun applyEffectiveEntitlement(user: FirebaseUser?, profile: UserProfile?): UserProfile {
        val base = profile ?: UserProfile()
        val testerPremium = hasTesterPremium(user, base)
        val effectivePremium = base.isPremium || testerPremium
        val premiumSeed = if (testerPremium) {
            base.copy(
                isPremium = true,
                availableProblems = ProblemArea.entries.map { it.name },
                selectedProblems = base.selectedProblems,
                premiumSince = if (base.premiumSince > 0L) base.premiumSince else System.currentTimeMillis()
            )
        } else {
            base.copy(isPremium = effectivePremium)
        }
        return PremiumAccess.sanitizeProfile(premiumSeed)
    }
}

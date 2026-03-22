package com.example.fixd

data class UserProfile(
    val preferredName: String = "",
    val availableProblems: List<String> = emptyList(),
    val selectedProblems: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val premiumSince: Long = 0L
)

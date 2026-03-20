package com.example.fixd

data class UserProfile(
    val preferredName: String = "",
    val selectedProblems: List<String> = emptyList()
)

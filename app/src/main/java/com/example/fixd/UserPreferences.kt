package com.example.fixd

object UserPreferences {
    fun isGoogleUser(emailProviders: List<String>): Boolean {
        return emailProviders.contains("google.com")
    }
}

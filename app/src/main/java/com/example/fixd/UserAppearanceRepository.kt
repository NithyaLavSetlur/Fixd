package com.example.fixd

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object UserAppearanceRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val usersCollection by lazy { firestore.collection("users") }

    fun getAppearance(
        userId: String,
        onSuccess: (UserAppearanceSettings) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    UserAppearanceSettings(
                        themeMode = snapshot.getString("themeMode") ?: UserPreferences.THEME_SYSTEM,
                        themeSeedColor = (snapshot.getLong("themeSeedColor")
                            ?: ThemePaletteManager.DEFAULT_SEED_COLOR.toLong()).toInt()
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun saveThemeMode(
        userId: String,
        themeMode: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .set(mapOf("themeMode" to themeMode), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun saveThemeSeedColor(
        userId: String,
        themeSeedColor: Int,
        onSuccess: (UserAppearanceSettings) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .set(mapOf("themeSeedColor" to themeSeedColor.toLong()), SetOptions.merge())
            .addOnSuccessListener {
                onSuccess(UserAppearanceSettings(themeSeedColor = themeSeedColor))
            }
            .addOnFailureListener(onFailure)
    }
}

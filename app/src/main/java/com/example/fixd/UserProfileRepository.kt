package com.example.fixd

import com.google.firebase.firestore.FirebaseFirestore

object UserProfileRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val usersCollection by lazy { firestore.collection("users") }

    fun getProfile(
        userId: String,
        onSuccess: (UserProfile?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onSuccess(null)
                    return@addOnSuccessListener
                }

                onSuccess(
                    UserProfile(
                        preferredName = snapshot.getString("preferredName").orEmpty(),
                        availableProblems = (snapshot.get("availableProblems") as? List<String>)
                            ?: (snapshot.get("selectedProblems") as? List<String> ?: emptyList()),
                        selectedProblems = snapshot.get("selectedProblems") as? List<String> ?: emptyList(),
                        isPremium = snapshot.getBoolean("isPremium") ?: false,
                        premiumSince = snapshot.getLong("premiumSince") ?: 0L
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun saveProfile(
        userId: String,
        profile: UserProfile,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .set(
                mapOf(
                    "preferredName" to profile.preferredName,
                    "availableProblems" to profile.availableProblems,
                    "selectedProblems" to profile.selectedProblems,
                    "isPremium" to profile.isPremium,
                    "premiumSince" to profile.premiumSince
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun updatePremiumStatus(
        userId: String,
        isPremium: Boolean,
        premiumSince: Long,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .update(
                mapOf(
                    "isPremium" to isPremium,
                    "premiumSince" to premiumSince
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}

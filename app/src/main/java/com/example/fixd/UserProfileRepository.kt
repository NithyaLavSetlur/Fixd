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
                        selectedProblems = snapshot.get("selectedProblems") as? List<String> ?: emptyList()
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
                    "selectedProblems" to profile.selectedProblems
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}

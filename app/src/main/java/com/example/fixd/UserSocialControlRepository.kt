package com.example.fixd

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object UserSocialControlRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val usersCollection by lazy { firestore.collection("users") }

    fun getSettings(
        userId: String,
        onSuccess: (SocialControlSettings) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    SocialControlSettings(
                        appControlEnabled = snapshot.getBoolean("socialAppControlEnabled") ?: false,
                        floatingBubbleEnabled = snapshot.getBoolean("socialFloatingBubbleEnabled") ?: false,
                        instagramBlockReels = snapshot.getBoolean("socialInstagramBlockReels") ?: true,
                        instagramDisableDiscover = snapshot.getBoolean("socialInstagramDisableDiscover") ?: true,
                        youtubeBlockShorts = snapshot.getBoolean("socialYoutubeBlockShorts") ?: true
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun saveSettings(
        userId: String,
        settings: SocialControlSettings,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .set(
                mapOf(
                    "socialAppControlEnabled" to settings.appControlEnabled,
                    "socialFloatingBubbleEnabled" to settings.floatingBubbleEnabled,
                    "socialInstagramBlockReels" to settings.instagramBlockReels,
                    "socialInstagramDisableDiscover" to settings.instagramDisableDiscover,
                    "socialYoutubeBlockShorts" to settings.youtubeBlockShorts
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}

package com.example.fixd

import com.google.firebase.firestore.FirebaseFirestore

data class CountdownEntry(
    val id: String = "",
    val title: String = "",
    val targetAt: Long = 0L,
    val notifyAt: Long = targetAt,
    val createdAt: Long = System.currentTimeMillis()
)

object CountdownRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private fun countdowns(userId: String) =
        firestore.collection("users").document(userId).collection("countdowns")

    fun getCountdowns(
        userId: String,
        onSuccess: (List<CountdownEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        countdowns(userId)
            .orderBy("targetAt")
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.map { doc ->
                        CountdownEntry(
                            id = doc.id,
                            title = doc.getString("title").orEmpty(),
                            targetAt = doc.getLong("targetAt") ?: 0L,
                            notifyAt = doc.getLong("notifyAt") ?: (doc.getLong("targetAt") ?: 0L),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    }
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun saveCountdown(
        userId: String,
        countdown: CountdownEntry,
        onSuccess: (CountdownEntry) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val document = if (countdown.id.isBlank()) countdowns(userId).document() else countdowns(userId).document(countdown.id)
        val updated = countdown.copy(id = document.id)
        document.set(
            mapOf(
                "title" to updated.title,
                "targetAt" to updated.targetAt,
                "notifyAt" to updated.notifyAt,
                "createdAt" to updated.createdAt
            )
        )
            .addOnSuccessListener { onSuccess(updated) }
            .addOnFailureListener(onFailure)
    }

    fun deleteCountdown(
        userId: String,
        countdownId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        countdowns(userId)
            .document(countdownId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}

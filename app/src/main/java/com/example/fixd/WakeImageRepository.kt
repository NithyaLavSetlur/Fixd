package com.example.fixd

import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

object WakeImageRepository {
    private val storage by lazy { FirebaseStorage.getInstance() }

    fun uploadSubmissionImage(
        userId: String,
        alarmId: String,
        imageBytes: ByteArray,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        val reference = storage.reference
            .child("wakeSubmissions")
            .child(userId)
            .child(alarmId.ifBlank { "no_alarm" })
            .child(fileName)

        reference.putBytes(imageBytes)
            .continueWithTask { task ->
                val exception = task.exception
                if (exception != null) {
                    throw exception
                }
                reference.downloadUrl
            }
            .addOnSuccessListener { uri -> onSuccess(uri.toString()) }
            .addOnFailureListener(onFailure)
    }
}

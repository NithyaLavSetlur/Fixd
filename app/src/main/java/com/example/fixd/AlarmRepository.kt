package com.example.fixd

import com.google.firebase.firestore.FirebaseFirestore

object AlarmRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private fun alarms(userId: String) =
        firestore.collection("users").document(userId).collection("alarms")

    private fun submissions(userId: String) =
        firestore.collection("users").document(userId).collection("submissions")

    fun getAlarms(
        userId: String,
        onSuccess: (List<WakeAlarm>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        alarms(userId)
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.map { doc ->
                        WakeAlarm(
                            id = doc.id,
                            name = doc.getString("name").orEmpty(),
                            hour = (doc.getLong("hour") ?: 7L).toInt(),
                            minute = (doc.getLong("minute") ?: 0L).toInt(),
                            repeatDays = (doc.get("repeatDays") as? List<*>)?.mapNotNull {
                                (it as? Long)?.toInt()
                            } ?: listOf(1, 2, 3, 4, 5, 6, 7),
                            enabled = doc.getBoolean("enabled") ?: true,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    }
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun saveAlarm(
        userId: String,
        alarm: WakeAlarm,
        onSuccess: (WakeAlarm) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val document = if (alarm.id.isBlank()) alarms(userId).document() else alarms(userId).document(alarm.id)
        val updated = alarm.copy(id = document.id)
        document.set(
            mapOf(
                "hour" to updated.hour,
                "minute" to updated.minute,
                "name" to updated.name,
                "repeatDays" to updated.repeatDays,
                "enabled" to updated.enabled,
                "createdAt" to updated.createdAt
            )
        )
            .addOnSuccessListener { onSuccess(updated) }
            .addOnFailureListener(onFailure)
    }

    fun deleteAlarm(
        userId: String,
        alarmId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        alarms(userId)
            .document(alarmId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun saveSubmission(
        userId: String,
        submission: WakeSubmission,
        onSuccess: (WakeSubmission) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val document = if (submission.id.isBlank()) submissions(userId).document() else submissions(userId).document(submission.id)
        val updated = submission.copy(id = document.id)
        document.set(
                mapOf(
                    "alarmId" to updated.alarmId,
                    "type" to updated.type,
                    "text" to updated.text,
                    "imagePath" to updated.imagePath,
                    "verdict" to updated.verdict,
                    "feedback" to updated.feedback,
                    "alarmHour" to updated.alarmHour,
                    "alarmMinute" to updated.alarmMinute,
                    "triggeredAt" to updated.triggeredAt,
                    "completedAt" to updated.completedAt,
                    "responseDurationMs" to updated.responseDurationMs,
                    "wakeStatus" to updated.wakeStatus,
                    "createdAt" to updated.createdAt
                )
            )
            .addOnSuccessListener { onSuccess(updated) }
            .addOnFailureListener(onFailure)
    }

    fun updateSubmissionWakeStatus(
        userId: String,
        submissionId: String,
        wakeStatus: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        submissions(userId)
            .document(submissionId)
            .update("wakeStatus", wakeStatus)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun getSuccessfulSubmissions(
        userId: String,
        onSuccess: (List<WakeSubmission>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        submissions(userId)
            .whereEqualTo("verdict", "passed")
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.map { doc ->
                        WakeSubmission(
                            id = doc.id,
                            alarmId = doc.getString("alarmId").orEmpty(),
                            type = doc.getString("type").orEmpty(),
                            text = doc.getString("text").orEmpty(),
                            imagePath = doc.getString("imagePath").orEmpty(),
                            verdict = doc.getString("verdict").orEmpty(),
                            feedback = doc.getString("feedback").orEmpty(),
                            alarmHour = (doc.getLong("alarmHour") ?: 0L).toInt(),
                            alarmMinute = (doc.getLong("alarmMinute") ?: 0L).toInt(),
                            triggeredAt = doc.getLong("triggeredAt") ?: 0L,
                            completedAt = doc.getLong("completedAt") ?: 0L,
                            responseDurationMs = doc.getLong("responseDurationMs") ?: 0L,
                            wakeStatus = doc.getString("wakeStatus") ?: "pending",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    }.sortedByDescending { it.createdAt }
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun getRecentSubmissions(
        userId: String,
        limit: Long = 10,
        onSuccess: (List<WakeSubmission>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        submissions(userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.map { doc ->
                        WakeSubmission(
                            id = doc.id,
                            alarmId = doc.getString("alarmId").orEmpty(),
                            type = doc.getString("type").orEmpty(),
                            text = doc.getString("text").orEmpty(),
                            imagePath = doc.getString("imagePath").orEmpty(),
                            verdict = doc.getString("verdict").orEmpty(),
                            feedback = doc.getString("feedback").orEmpty(),
                            alarmHour = (doc.getLong("alarmHour") ?: 0L).toInt(),
                            alarmMinute = (doc.getLong("alarmMinute") ?: 0L).toInt(),
                            triggeredAt = doc.getLong("triggeredAt") ?: 0L,
                            completedAt = doc.getLong("completedAt") ?: 0L,
                            responseDurationMs = doc.getLong("responseDurationMs") ?: 0L,
                            wakeStatus = doc.getString("wakeStatus") ?: "pending",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    }
                )
            }
            .addOnFailureListener(onFailure)
    }
}

package com.example.fixd

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object WakeValidationApi {
    fun validate(
        endpoint: String,
        text: String,
        imageBytes: ByteArray?,
        onSuccess: (WakeValidationResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Thread {
            try {
                val tokenTask = FirebaseAuth.getInstance().currentUser?.getIdToken(false)
                val idToken = if (tokenTask != null) Tasks.await(tokenTask).token.orEmpty() else ""
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                if (idToken.isNotBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer $idToken")
                }
                connection.doOutput = true

                val payload = JSONObject().apply {
                    put("text", text)
                    put("imageBase64", imageBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val responseStream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException(extractErrorMessage(responseText))
                }
                val json = JSONObject(responseText)
                onSuccess(
                    WakeValidationResult(
                        passed = json.optBoolean("passed", false),
                        feedback = json.optString("feedback", "")
                    )
                )
            } catch (exception: Exception) {
                onFailure(exception)
            }
        }.start()
    }

    private fun extractErrorMessage(responseText: String): String {
        if (responseText.isBlank()) {
            return "Validation request failed."
        }

        return try {
            val json = JSONObject(responseText)
            json.optString("feedback").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: responseText
        } catch (_: JSONException) {
            responseText
        }
    }

    fun compressImage(path: String): ByteArray {
        val bitmap = BitmapFactory.decodeFile(path)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, output)
            output.toByteArray()
        }
    }

    fun localValidate(text: String, imageBytes: ByteArray?): WakeValidationResult {
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            return WakeValidationResult(
                passed = true,
                feedback = "Photo accepted."
            )
        }

        val normalized = text.trim()
        val wordCount = normalized
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }

        val passed = normalized.length >= 12 && wordCount >= 3
        return if (passed) {
            WakeValidationResult(
                passed = true,
                feedback = "Text accepted."
            )
        } else {
            WakeValidationResult(
                passed = false,
                feedback = "Write a clearer plan, goal, or affirmation using at least 3 words."
            )
        }
    }
}

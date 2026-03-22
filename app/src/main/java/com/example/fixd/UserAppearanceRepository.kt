package com.example.fixd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

object UserAppearanceRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
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
                        paletteId = snapshot.getString("paletteId") ?: ThemePaletteManager.PALETTE_OCEAN,
                        backgroundStoragePath = snapshot.getString("backgroundStoragePath").orEmpty(),
                        backgroundBrightness = (snapshot.getLong("backgroundBrightness")
                            ?: AppBackgroundManager.DEFAULT_BRIGHTNESS.toLong()).toInt(),
                        backgroundBlur = (snapshot.getLong("backgroundBlur")
                            ?: AppBackgroundManager.DEFAULT_BLUR.toLong()).toInt()
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

    fun savePaletteId(
        userId: String,
        paletteId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(userId)
            .set(mapOf("paletteId" to paletteId), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun saveBackground(
        userId: String,
        imageBytes: ByteArray,
        brightness: Int,
        blur: Int,
        onSuccess: (UserAppearanceSettings) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storagePath = "users/$userId/backgrounds/app-background.jpg"
        storage.reference.child(storagePath)
            .putBytes(imageBytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("Background upload failed.")
                }
                usersCollection.document(userId).set(
                    mapOf(
                        "backgroundStoragePath" to storagePath,
                        "backgroundBrightness" to brightness,
                        "backgroundBlur" to blur
                    ),
                    SetOptions.merge()
                )
            }
            .addOnSuccessListener {
                onSuccess(
                    UserAppearanceSettings(
                        backgroundStoragePath = storagePath,
                        backgroundBrightness = brightness,
                        backgroundBlur = blur
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun clearBackground(
        userId: String,
        storagePath: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val clearedFields = mapOf(
            "backgroundStoragePath" to "",
            "backgroundBrightness" to AppBackgroundManager.DEFAULT_BRIGHTNESS,
            "backgroundBlur" to AppBackgroundManager.DEFAULT_BLUR
        )

        val clearFirestore = {
            usersCollection.document(userId)
                .set(clearedFields, SetOptions.merge())
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener(onFailure)
        }

        if (storagePath.isBlank()) {
            clearFirestore()
            return
        }

        storage.reference.child(storagePath)
            .delete()
            .addOnSuccessListener { clearFirestore() }
            .addOnFailureListener { clearFirestore() }
    }

    fun compressForUpload(imageBytes: ByteArray, maxSize: Int = 1600): ByteArray {
        val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes
        val scale = minOf(maxSize / original.width.toFloat(), maxSize / original.height.toFloat(), 1f)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else {
            original
        }

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
            output.toByteArray()
        }
    }
}

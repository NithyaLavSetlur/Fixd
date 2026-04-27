package com.example.fixd

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object UserProfileRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val usersCollection by lazy { firestore.collection("users") }
    private val usernameIndexCollection by lazy { firestore.collection("usernameIndex") }
    private val userPublicProfilesCollection by lazy { firestore.collection("userPublicProfiles") }

    private data class UsernameIndexRecord(
        val uid: String,
        val email: String,
        val username: String,
        val usernameLower: String
    )

    private fun userDocumentMap(profile: UserProfile) = mapOf(
        "preferredName" to profile.preferredName,
        "username" to profile.username,
        "email" to profile.email,
        "availableProblems" to profile.availableProblems,
        "selectedProblems" to profile.selectedProblems,
        "isPremium" to profile.isPremium,
        "premiumSince" to profile.premiumSince
    )

    private fun publicProfileDocumentMap(profile: UserProfile) = mapOf(
        "preferredName" to profile.preferredName,
        "username" to profile.username,
        "usernameLower" to normalizeUsername(profile.username)
    )

    fun normalizeUsername(username: String): String {
        return username.trim().lowercase()
    }

    fun isUsernameValid(username: String): Boolean {
        val normalized = normalizeUsername(username)
        return normalized.length in 3..20 && normalized.matches(Regex("^[a-z0-9._]+$"))
    }

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
                        username = snapshot.getString("username").orEmpty(),
                        email = snapshot.getString("email").orEmpty(),
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
                userDocumentMap(profile),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                if (profile.username.isNotBlank() && isUsernameValid(profile.username)) {
                    userPublicProfilesCollection.document(userId)
                        .set(
                            publicProfileDocumentMap(profile),
                            SetOptions.merge()
                        )
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onSuccess() }
                } else {
                    onSuccess()
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun saveProfileWithUsername(
        userId: String,
        previousUsername: String?,
        profile: UserProfile,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedUsername = profile.username.trim()
        if (!isUsernameValid(trimmedUsername)) {
            onFailure(IllegalArgumentException("Username must be 3-20 characters and use only letters, numbers, dots, or underscores."))
            return
        }

        val normalizedUsername = normalizeUsername(trimmedUsername)
        val previousNormalized = previousUsername?.takeIf { it.isNotBlank() }?.let(::normalizeUsername)
        val userRef = usersCollection.document(userId)
        val usernameRef = usernameIndexCollection.document(normalizedUsername)
        val profileToSave = profile.copy(username = trimmedUsername)

        firestore.runTransaction { transaction ->
            val usernameSnapshot = transaction.get(usernameRef)
            val takenBy = usernameSnapshot.getString("uid")
            if (takenBy != null && takenBy != userId) {
                throw IllegalStateException("That username is already taken.")
            }

            if (previousNormalized != null && previousNormalized != normalizedUsername) {
                val oldRef = usernameIndexCollection.document(previousNormalized)
                val oldSnapshot = transaction.get(oldRef)
                if (oldSnapshot.getString("uid") == userId) {
                    transaction.delete(oldRef)
                }
            }

            transaction.set(
                usernameRef,
                mapOf(
                    "uid" to userId,
                    "email" to profileToSave.email,
                    "username" to profileToSave.username,
                    "usernameLower" to normalizedUsername
                ),
                SetOptions.merge()
            )
            transaction.set(userRef, userDocumentMap(profileToSave), SetOptions.merge())
            transaction.set(
                userPublicProfilesCollection.document(userId),
                publicProfileDocumentMap(profileToSave),
                SetOptions.merge()
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun resolveEmailForLogin(
        login: String,
        onSuccess: (String?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedLogin = login.trim()
        if (trimmedLogin.contains("@")) {
            onSuccess(trimmedLogin)
            return
        }
        val normalized = normalizeUsername(trimmedLogin)
        usernameIndexCollection.document(normalized)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.getString("email"))
            }
            .addOnFailureListener(onFailure)
    }

    fun ensureUsernameForUser(
        userId: String,
        email: String,
        preferredName: String,
        currentProfile: UserProfile?,
        onSuccess: (UserProfile) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val existingProfile = currentProfile ?: UserProfile()
        if (existingProfile.username.isNotBlank()) {
            val updated = if (existingProfile.email == email) existingProfile else existingProfile.copy(email = email)
            if (updated != existingProfile) {
                saveProfile(userId, updated, onSuccess = { onSuccess(updated) }, onFailure = onFailure)
            } else {
                onSuccess(existingProfile)
            }
            return
        }

        fun sanitizeBase(source: String): String {
            val cleaned = source.trim().lowercase()
                .replace(Regex("[^a-z0-9._]"), "")
                .trim('.','_')
            return cleaned.ifBlank { "fixduser" }.take(16)
        }

        fun attempt(base: String, suffix: Int) {
            val candidate = buildString {
                append(base.take(16))
                if (suffix > 0) append(suffix)
            }.take(20)

            saveProfileWithUsername(
                userId = userId,
                previousUsername = null,
                profile = existingProfile.copy(
                    preferredName = existingProfile.preferredName.ifBlank { preferredName },
                    username = candidate,
                    email = email
                ),
                onSuccess = {
                    onSuccess(
                        existingProfile.copy(
                            preferredName = existingProfile.preferredName.ifBlank { preferredName },
                            username = candidate,
                            email = email
                        )
                    )
                },
                onFailure = { error ->
                    if (error is IllegalStateException && error.message?.contains("already taken") == true) {
                        attempt(base, suffix + 1)
                    } else {
                        onFailure(error)
                    }
                }
            )
        }

        val base = sanitizeBase(existingProfile.preferredName.ifBlank { preferredName.ifBlank { email.substringBefore("@") } })
        attempt(base, 0)
    }

    fun getEffectiveProfile(
        user: com.google.firebase.auth.FirebaseUser,
        onSuccess: (UserProfile) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                onSuccess(PremiumEntitlement.applyEffectiveEntitlement(user, profile))
            },
            onFailure = onFailure
        )
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

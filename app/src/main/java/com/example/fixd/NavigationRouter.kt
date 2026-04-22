package com.example.fixd

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

object NavigationRouter {
    fun authIntent(context: Context): Intent = Intent(context, AuthActivity::class.java)
    fun dashboardIntent(context: Context): Intent = Intent(context, DashboardActivity::class.java)
    fun selectionIntent(context: Context): Intent = Intent(context, ProblemSelectionActivity::class.java)

    fun routeSignedInUser(
        context: Context,
        onSuccess: (Intent) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onSuccess(authIntent(context))
            return
        }

        user.reload()
            .addOnSuccessListener {
                val providers = user.providerData.mapNotNull { it.providerId }
                if (!UserPreferences.isGoogleUser(providers) && !user.isEmailVerified) {
                    FirebaseAuth.getInstance().signOut()
                    onSuccess(authIntent(context))
                    return@addOnSuccessListener
                }

                UserProfileRepository.getProfile(
                    userId = user.uid,
                    onSuccess = { rawProfile ->
                        val effectiveProfile = PremiumEntitlement.applyEffectiveEntitlement(user, rawProfile)
                        val hasActivatedTabs = rawProfile?.availableProblems?.isNotEmpty() == true ||
                            rawProfile?.selectedProblems?.isNotEmpty() == true
                        val next = if (hasActivatedTabs || effectiveProfile.isPremium) {
                            dashboardIntent(context)
                        } else {
                            selectionIntent(context)
                        }
                        onSuccess(next)
                    },
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { exception ->
                if (exception is FirebaseAuthInvalidUserException) {
                    FirebaseAuth.getInstance().signOut()
                    onSuccess(authIntent(context))
                } else {
                    onFailure(exception)
                }
            }
    }
}

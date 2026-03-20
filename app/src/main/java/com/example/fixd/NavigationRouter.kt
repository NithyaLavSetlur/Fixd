package com.example.fixd

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

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

        val providers = user.providerData.mapNotNull { it.providerId }
        if (!UserPreferences.isGoogleUser(providers) && !user.isEmailVerified) {
            FirebaseAuth.getInstance().signOut()
            onSuccess(authIntent(context))
            return
        }

        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                val next = if (profile?.selectedProblems?.isNotEmpty() == true) {
                    dashboardIntent(context)
                } else {
                    selectionIntent(context)
                }
                onSuccess(next)
            },
            onFailure = onFailure
        )
    }
}

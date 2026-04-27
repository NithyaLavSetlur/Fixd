package com.example.fixd

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

object NavigationRouter {
    fun authIntent(context: Context): Intent = Intent(context, AuthActivity::class.java)
    fun dashboardIntent(context: Context): Intent = Intent(context, DashboardActivity::class.java)
    fun selectionIntent(context: Context): Intent = Intent(context, ProblemSelectionActivity::class.java)

    fun launchIntent(context: Context): Intent {
        val user = FirebaseAuth.getInstance().currentUser ?: return authIntent(context)
        val providers = user.providerData.mapNotNull { it.providerId }
        if (!UserPreferences.isGoogleUser(providers) && !user.isEmailVerified) {
            FirebaseAuth.getInstance().signOut()
            return authIntent(context)
        }
        return dashboardIntent(context)
    }
}

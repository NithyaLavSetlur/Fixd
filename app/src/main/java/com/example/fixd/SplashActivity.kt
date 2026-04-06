package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        ThemePaletteManager.applyToActivity(this)

        Handler(Looper.getMainLooper()).postDelayed({
            ThemePaletteManager.loadSignedInUserAppearance(this) {
                NavigationRouter.routeSignedInUser(
                    context = this,
                    onSuccess = { nextActivity ->
                        startActivity(nextActivity)
                        finish()
                    },
                    onFailure = {
                        Toast.makeText(this, R.string.firebase_not_ready, Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, AuthActivity::class.java))
                        finish()
                    }
                )
            }
        }, 1500)
    }
}

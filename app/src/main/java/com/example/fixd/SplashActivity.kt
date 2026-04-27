package com.example.fixd

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchIntent = NavigationRouter.launchIntent(this).apply {
            if (this.component?.className == DashboardActivity::class.java.name) {
                intent.extras?.let { putExtras(it) }
            }
        }
        startActivity(launchIntent)
        finish()
    }
}

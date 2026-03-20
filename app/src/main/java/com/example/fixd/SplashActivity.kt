package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
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
        }, 1500)
    }
}

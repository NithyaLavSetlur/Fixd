package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.fixd.databinding.ActivityAuthBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private var googleSignInClient: GoogleSignInClient? = null
    private var isSignUpMode = false
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                setLoading(false)
                toast(R.string.google_sign_in_cancelled)
                return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken

                if (idToken.isNullOrBlank()) {
                    setLoading(false)
                    toast(R.string.google_sign_in_not_ready)
                    return@registerForActivityResult
                }

                firebaseAuthWithGoogle(idToken)
            } catch (exception: ApiException) {
                setLoading(false)
                toast(exception.localizedMessage ?: getString(R.string.google_sign_in_not_ready))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppBackgroundManager.applyToActivity(this)

        val firebaseApp = FirebaseApp.initializeApp(this)
        if (firebaseApp == null) {
            toast(R.string.firebase_not_ready)
            binding.primaryActionButton.isEnabled = false
            binding.toggleModeButton.isEnabled = false
            return
        }

        auth = FirebaseAuth.getInstance(firebaseApp)

        binding.primaryActionButton.setOnClickListener {
            if (isSignUpMode) signUp() else signIn()
        }
        binding.googleSignInButton.setOnClickListener {
            launchGoogleSignIn()
        }
        binding.resendVerificationButton.setOnClickListener {
            resendVerificationEmail()
        }

        binding.toggleModeButton.setOnClickListener {
            isSignUpMode = !isSignUpMode
            renderMode()
        }

        renderMode()
    }

    private fun renderMode() {
        TransitionManager.beginDelayedTransition(
            binding.authCard,
            AutoTransition().apply { duration = 220 }
        )
        binding.nameLayout.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.confirmPasswordLayout.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.primaryActionButton.text = getString(if (isSignUpMode) R.string.sign_up else R.string.sign_in)
        binding.toggleModeButton.text = getString(
            if (isSignUpMode) R.string.switch_to_signin else R.string.switch_to_signup
        )
        hideVerificationPrompt()
    }

    private fun signIn() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        if (email.isBlank() || password.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.reload()?.addOnCompleteListener {
                        if (user?.isEmailVerified == true) {
                            hideVerificationPrompt()
                            openDashboard()
                        } else {
                            auth.signOut()
                            showVerificationPrompt(R.string.auth_email_not_verified)
                        }
                    }
                } else {
                    toast(task.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                }
            }
    }

    private fun signUp() {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

        if (name.isBlank()) {
            toast(R.string.profile_name_required)
            return
        }

        if (email.isBlank() || password.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        if (password != confirmPassword) {
            toast(R.string.auth_password_mismatch)
            return
        }

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    )?.addOnCompleteListener {
                        auth.currentUser?.sendEmailVerification()?.addOnCompleteListener {
                            auth.signOut()
                            setLoading(false)
                            isSignUpMode = false
                            renderMode()
                            binding.emailEditText.setText(email)
                            binding.passwordEditText.text?.clear()
                            binding.confirmPasswordEditText.text?.clear()
                            showVerificationPrompt(R.string.auth_email_verification_sent)
                        }
                    }
                } else {
                    setLoading(false)
                    toast(task.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.primaryActionButton.isEnabled = !isLoading
        binding.googleSignInButton.isEnabled = !isLoading
        binding.toggleModeButton.isEnabled = !isLoading
    }

    private fun launchGoogleSignIn() {
        val webClientId = getGoogleWebClientId()
        if (webClientId == null) {
            toast(R.string.google_sign_in_not_ready)
            return
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        setLoading(true)
        googleSignInClient = GoogleSignIn.getClient(this, options)
        googleSignInClient?.signOut()?.addOnCompleteListener {
            val intent = googleSignInClient?.signInIntent
            if (intent == null) {
                setLoading(false)
                toast(R.string.google_sign_in_not_ready)
            } else {
                googleSignInLauncher.launch(intent)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    hideVerificationPrompt()
                    openDashboard()
                } else {
                    toast(task.exception?.localizedMessage ?: getString(R.string.google_sign_in_not_ready))
                }
            }
    }

    private fun getGoogleWebClientId(): String? {
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (resId == 0) return null

        return getString(resId).takeUnless { it.isBlank() }
    }

    private fun openDashboard() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    private fun resendVerificationEmail() {
        val email = binding.emailEditText.text.toString().trim()
        if (email.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        setLoading(true)
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { methodsTask ->
                if (!methodsTask.isSuccessful) {
                    setLoading(false)
                    toast(methodsTask.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                    return@addOnCompleteListener
                }

                val methods = methodsTask.result?.signInMethods.orEmpty()
                if (!methods.contains("password")) {
                    setLoading(false)
                    toast(R.string.auth_email_verification_required)
                    return@addOnCompleteListener
                }

                val password = binding.passwordEditText.text.toString().trim()
                if (password.isBlank()) {
                    setLoading(false)
                    toast(R.string.auth_missing_fields)
                    return@addOnCompleteListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { signInTask ->
                        if (!signInTask.isSuccessful) {
                            setLoading(false)
                            toast(signInTask.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                            return@addOnCompleteListener
                        }

                        val user = auth.currentUser
                        user?.sendEmailVerification()?.addOnCompleteListener {
                            auth.signOut()
                            setLoading(false)
                            showVerificationPrompt(R.string.auth_email_verification_sent)
                        }
                    }
            }
    }

    private fun showVerificationPrompt(messageRes: Int) {
        binding.verificationMessageText.visibility = View.VISIBLE
        binding.resendVerificationButton.visibility = View.VISIBLE
        binding.verificationMessageText.text = getString(messageRes)
    }

    private fun hideVerificationPrompt() {
        binding.verificationMessageText.visibility = View.GONE
        binding.resendVerificationButton.visibility = View.GONE
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

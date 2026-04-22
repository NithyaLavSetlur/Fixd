package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var googleSignInClient: GoogleSignInClient? = null

    private var isSignUpMode by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var nameInput by mutableStateOf("")
    private var emailInput by mutableStateOf("")
    private var passwordInput by mutableStateOf("")
    private var confirmPasswordInput by mutableStateOf("")
    private var verificationMessage by mutableStateOf<String?>(null)
    private var firebaseReady by mutableStateOf(true)

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                updateLoading(false)
                toast(R.string.google_sign_in_cancelled)
                return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken

                if (idToken.isNullOrBlank()) {
                    updateLoading(false)
                    toast(R.string.google_sign_in_not_ready)
                    return@registerForActivityResult
                }

                firebaseAuthWithGoogle(idToken)
            } catch (exception: ApiException) {
                updateLoading(false)
                toast(exception.localizedMessage ?: getString(R.string.google_sign_in_not_ready))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val firebaseApp = FirebaseApp.initializeApp(this)
        if (firebaseApp == null) {
            firebaseReady = false
            toast(R.string.firebase_not_ready)
        } else {
            auth = FirebaseAuth.getInstance(firebaseApp)
        }

        setContent {
            FixdComposeTheme {
                AuthScreen(
                    isSignUpMode = isSignUpMode,
                    isLoading = isLoading,
                    firebaseReady = firebaseReady,
                    name = nameInput,
                    email = emailInput,
                    password = passwordInput,
                    confirmPassword = confirmPasswordInput,
                    verificationMessage = verificationMessage,
                    palette = ThemePaletteManager.currentPalette(this),
                    onNameChange = { nameInput = it },
                    onEmailChange = { emailInput = it },
                    onPasswordChange = { passwordInput = it },
                    onConfirmPasswordChange = { confirmPasswordInput = it },
                    onPrimaryAction = { if (isSignUpMode) signUp() else signIn() },
                    onGoogleSignIn = { launchGoogleSignIn() },
                    onToggleMode = {
                        isSignUpMode = !isSignUpMode
                        verificationMessage = null
                    },
                    onResendVerification = { resendVerificationEmail() }
                )
            }
        }

        ThemePaletteManager.applyToActivity(this)
    }

    private fun signIn() {
        val email = emailInput.trim()
        val password = passwordInput.trim()

        if (email.isBlank() || password.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        updateLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                updateLoading(false)
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
        val name = nameInput.trim()
        val email = emailInput.trim()
        val password = passwordInput.trim()
        val confirmPassword = confirmPasswordInput.trim()

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

        updateLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    )?.addOnCompleteListener {
                        auth.currentUser?.sendEmailVerification()?.addOnCompleteListener {
                            auth.signOut()
                            updateLoading(false)
                            isSignUpMode = false
                            verificationMessage = getString(R.string.auth_email_verification_sent)
                            emailInput = email
                            passwordInput = ""
                            confirmPasswordInput = ""
                        }
                    }
                } else {
                    updateLoading(false)
                    toast(task.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                }
            }
    }

    private fun updateLoading(loading: Boolean) {
        isLoading = loading
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

        updateLoading(true)
        googleSignInClient = GoogleSignIn.getClient(this, options)
        googleSignInClient?.signOut()?.addOnCompleteListener {
            val intent = googleSignInClient?.signInIntent
            if (intent == null) {
                updateLoading(false)
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
                updateLoading(false)
                if (task.isSuccessful) {
                    hideVerificationPrompt()
                    val isNewGoogleUser = task.result?.additionalUserInfo?.isNewUser == true
                    if (isNewGoogleUser) {
                        startActivity(NavigationRouter.selectionIntent(this))
                        finish()
                    } else {
                        openDashboard()
                    }
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
        val email = emailInput.trim()
        if (email.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        updateLoading(true)
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { methodsTask ->
                if (!methodsTask.isSuccessful) {
                    updateLoading(false)
                    toast(methodsTask.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                    return@addOnCompleteListener
                }

                val methods = methodsTask.result?.signInMethods.orEmpty()
                if (!methods.contains("password")) {
                    updateLoading(false)
                    toast(R.string.auth_email_verification_required)
                    return@addOnCompleteListener
                }

                val password = passwordInput.trim()
                if (password.isBlank()) {
                    updateLoading(false)
                    toast(R.string.auth_missing_fields)
                    return@addOnCompleteListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { signInTask ->
                        if (!signInTask.isSuccessful) {
                            updateLoading(false)
                            toast(signInTask.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                            return@addOnCompleteListener
                        }

                        val user = auth.currentUser
                        user?.sendEmailVerification()?.addOnCompleteListener {
                            auth.signOut()
                            updateLoading(false)
                            showVerificationPrompt(R.string.auth_email_verification_sent)
                        }
                    }
            }
    }

    private fun showVerificationPrompt(messageRes: Int) {
        verificationMessage = getString(messageRes)
    }

    private fun hideVerificationPrompt() {
        verificationMessage = null
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AuthScreen(
    isSignUpMode: Boolean,
    isLoading: Boolean,
    firebaseReady: Boolean,
    name: String,
    email: String,
    password: String,
    confirmPassword: String,
    verificationMessage: String?,
    palette: GeneratedPalette,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onToggleMode: () -> Unit,
    onResendVerification: () -> Unit
) {
    val scrollState = rememberScrollState()
    val heroBrush = Brush.verticalGradient(
        colors = listOf(
            Color(palette.surface),
            Color(palette.gradientMid).copy(alpha = 0.2f),
            Color(palette.primaryDark).copy(alpha = 0.3f)
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(heroBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(palette.primary).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_fixd_panda_logo_monochrome),
                                contentDescription = stringResource(id = R.string.app_name),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(72.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = stringResource(id = R.string.auth_welcome),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(id = R.string.auth_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (isSignUpMode) {
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.name_label)) },
                                singleLine = true,
                                enabled = firebaseReady && !isLoading
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(id = R.string.email_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = firebaseReady && !isLoading
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(id = R.string.password_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = firebaseReady && !isLoading
                        )

                        if (isSignUpMode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = onConfirmPasswordChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.confirm_password_label)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                enabled = firebaseReady && !isLoading
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onPrimaryAction,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = firebaseReady && !isLoading
                        ) {
                            Text(stringResource(id = if (isSignUpMode) R.string.sign_up else R.string.sign_in))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onGoogleSignIn,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = firebaseReady && !isLoading
                        ) {
                            Text(stringResource(id = R.string.continue_with_google))
                        }

                        if (isLoading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }

                        if (!verificationMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = verificationMessage,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onResendVerification,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = firebaseReady && !isLoading
                            ) {
                                Text(stringResource(id = R.string.auth_resend_verification))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = onToggleMode,
                            enabled = firebaseReady && !isLoading
                        ) {
                            Text(
                                text = stringResource(
                                    id = if (isSignUpMode) R.string.switch_to_signin else R.string.switch_to_signup
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

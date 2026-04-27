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
    enum class AuthFlowMode {
        STANDARD,
        GOOGLE_USERNAME
    }

    private lateinit var auth: FirebaseAuth
    private var googleSignInClient: GoogleSignInClient? = null

    private var authFlowMode by mutableStateOf(AuthFlowMode.STANDARD)
    private var isSignUpMode by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var nameInput by mutableStateOf("")
    private var usernameInput by mutableStateOf("")
    private var emailInput by mutableStateOf("")
    private var passwordInput by mutableStateOf("")
    private var confirmPasswordInput by mutableStateOf("")
    private var verificationMessage by mutableStateOf<String?>(null)
    private var firebaseReady by mutableStateOf(true)
    private var pendingGoogleProfile by mutableStateOf<UserProfile?>(null)
    private var pendingGoogleIsNewUser by mutableStateOf(false)

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
                    authFlowMode = authFlowMode,
                    isSignUpMode = isSignUpMode,
                    isLoading = isLoading,
                    firebaseReady = firebaseReady,
                    name = nameInput,
                    username = usernameInput,
                    email = emailInput,
                    password = passwordInput,
                    confirmPassword = confirmPasswordInput,
                    verificationMessage = verificationMessage,
                    palette = ThemePaletteManager.currentPalette(this),
                    onNameChange = { nameInput = it },
                    onUsernameChange = { usernameInput = it },
                    onEmailChange = { emailInput = it },
                    onPasswordChange = { passwordInput = it },
                    onConfirmPasswordChange = { confirmPasswordInput = it },
                    onPrimaryAction = { if (isSignUpMode) signUp() else signIn() },
                    onGoogleUsernameSubmit = { completeGoogleUsernameSetup() },
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
        val login = emailInput.trim()
        val password = passwordInput.trim()

        if (login.isBlank() || password.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        updateLoading(true)
        UserProfileRepository.resolveEmailForLogin(
            login = login,
            onSuccess = { resolvedEmail ->
                if (resolvedEmail.isNullOrBlank()) {
                    updateLoading(false)
                    toast(R.string.auth_login_not_found)
                    return@resolveEmailForLogin
                }
                auth.signInWithEmailAndPassword(resolvedEmail, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.reload()?.addOnCompleteListener {
                                if (user?.isEmailVerified == true) {
                                    ensureUsernameForCurrentUser(allowAutoGenerate = true) { success ->
                                        updateLoading(false)
                                        if (success) {
                                            hideVerificationPrompt()
                                            openDashboard()
                                        }
                                    }
                                } else {
                                    updateLoading(false)
                                    auth.signOut()
                                    showVerificationPrompt(R.string.auth_email_not_verified)
                                }
                            }
                        } else {
                            updateLoading(false)
                            toast(task.exception?.localizedMessage ?: getString(R.string.firebase_not_ready))
                        }
                    }
            },
            onFailure = {
                updateLoading(false)
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    private fun signUp() {
        val name = nameInput.trim()
        val username = usernameInput.trim()
        val email = emailInput.trim()
        val password = passwordInput.trim()
        val confirmPassword = confirmPasswordInput.trim()

        if (name.isBlank()) {
            toast(R.string.profile_name_required)
            return
        }

        if (username.isBlank()) {
            toast(R.string.auth_username_required)
            return
        }

        if (!UserProfileRepository.isUsernameValid(username)) {
            toast(R.string.auth_username_invalid)
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
                    val createdUser = auth.currentUser
                    createdUser?.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    )?.addOnCompleteListener {
                        val user = auth.currentUser
                        if (user == null) {
                            updateLoading(false)
                            toast(R.string.firebase_not_ready)
                            return@addOnCompleteListener
                        }
                        UserProfileRepository.saveProfileWithUsername(
                            userId = user.uid,
                            previousUsername = null,
                            profile = UserProfile(
                                preferredName = name,
                                username = username,
                                email = email
                            ),
                            onSuccess = {
                                user.sendEmailVerification().addOnCompleteListener {
                                    auth.signOut()
                                    updateLoading(false)
                                    isSignUpMode = false
                                    verificationMessage = getString(R.string.auth_email_verification_sent)
                                    usernameInput = username
                                    emailInput = email
                                    passwordInput = ""
                                    confirmPasswordInput = ""
                                }
                            },
                            onFailure = { error ->
                                user.delete()
                                auth.signOut()
                                updateLoading(false)
                                if (error.message?.contains("already taken") == true) {
                                    toast(R.string.auth_username_taken)
                                } else {
                                    toast(error.localizedMessage ?: getString(R.string.firebase_not_ready))
                                }
                            }
                        )
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
                if (task.isSuccessful) {
                    hideVerificationPrompt()
                    val isNewGoogleUser = task.result?.additionalUserInfo?.isNewUser == true
                    handleGoogleUserAfterAuth(isNewGoogleUser)
                } else {
                    updateLoading(false)
                    toast(task.exception?.localizedMessage ?: getString(R.string.google_sign_in_not_ready))
                }
            }
    }

    private fun handleGoogleUserAfterAuth(isNewGoogleUser: Boolean) {
        val user = auth.currentUser
        if (user == null) {
            updateLoading(false)
            toast(R.string.firebase_not_ready)
            return
        }
        val email = user.email
        if (email.isNullOrBlank()) {
            updateLoading(false)
            toast(R.string.google_sign_in_not_ready)
            return
        }
        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                val resolvedProfile = (profile ?: UserProfile()).copy(
                    preferredName = profile?.preferredName?.ifBlank { user.displayName.orEmpty() } ?: user.displayName.orEmpty(),
                    email = email
                )
                if (resolvedProfile.username.isBlank()) {
                    pendingGoogleProfile = resolvedProfile
                    pendingGoogleIsNewUser = isNewGoogleUser
                    nameInput = resolvedProfile.preferredName
                    usernameInput = ""
                    emailInput = email
                    passwordInput = ""
                    confirmPasswordInput = ""
                    authFlowMode = AuthFlowMode.GOOGLE_USERNAME
                    updateLoading(false)
                } else {
                    updateLoading(false)
                    openPostAuthDestination(resolvedProfile, isNewGoogleUser)
                }
            },
            onFailure = {
                updateLoading(false)
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    private fun ensureUsernameForCurrentUser(
        allowAutoGenerate: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false)
            return
        }
        val email = user.email
        if (email.isNullOrBlank()) {
            onComplete(true)
            return
        }
        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                if (!allowAutoGenerate && (profile?.username.orEmpty().isBlank())) {
                    onComplete(true)
                    return@getProfile
                }
                UserProfileRepository.ensureUsernameForUser(
                    userId = user.uid,
                    email = email,
                    preferredName = user.displayName.orEmpty(),
                    currentProfile = profile,
                    onSuccess = { onComplete(true) },
                    onFailure = {
                        toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
                        onComplete(false)
                    }
                )
            },
            onFailure = {
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
                onComplete(false)
            }
        )
    }

    private fun completeGoogleUsernameSetup() {
        val user = auth.currentUser
        val email = user?.email
        val baseProfile = pendingGoogleProfile
        val username = usernameInput.trim()

        if (user == null || email.isNullOrBlank() || baseProfile == null) {
            toast(R.string.firebase_not_ready)
            return
        }
        if (username.isBlank()) {
            toast(R.string.auth_username_required)
            return
        }
        if (!UserProfileRepository.isUsernameValid(username)) {
            toast(R.string.auth_username_invalid)
            return
        }

        updateLoading(true)
        UserProfileRepository.saveProfileWithUsername(
            userId = user.uid,
            previousUsername = baseProfile.username.ifBlank { null },
            profile = baseProfile.copy(
                preferredName = baseProfile.preferredName.ifBlank { user.displayName.orEmpty() },
                username = username,
                email = email
            ),
            onSuccess = {
                val savedProfile = baseProfile.copy(
                    preferredName = baseProfile.preferredName.ifBlank { user.displayName.orEmpty() },
                    username = username,
                    email = email
                )
                pendingGoogleProfile = null
                authFlowMode = AuthFlowMode.STANDARD
                updateLoading(false)
                openPostAuthDestination(savedProfile, pendingGoogleIsNewUser)
            },
            onFailure = { error ->
                updateLoading(false)
                if (error.message?.contains("already taken") == true) {
                    toast(R.string.auth_username_taken)
                } else {
                    toast(error.localizedMessage ?: getString(R.string.firebase_not_ready))
                }
            }
        )
    }

    private fun openPostAuthDestination(profile: UserProfile, isNewGoogleUser: Boolean) {
        val needsSelection = isNewGoogleUser || profile.availableProblems.isEmpty() || profile.selectedProblems.isEmpty()
        if (needsSelection) {
            startActivity(NavigationRouter.selectionIntent(this))
        } else {
            openDashboard()
            return
        }
        finish()
    }

    private fun getGoogleWebClientId(): String? {
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (resId == 0) return null

        return getString(resId).takeUnless { it.isBlank() }
    }

    private fun openDashboard() {
        startActivity(NavigationRouter.dashboardIntent(this))
        finish()
    }

    private fun resendVerificationEmail() {
        val login = emailInput.trim()
        if (login.isBlank()) {
            toast(R.string.auth_missing_fields)
            return
        }

        updateLoading(true)
        UserProfileRepository.resolveEmailForLogin(
            login = login,
            onSuccess = { resolvedEmail ->
                if (resolvedEmail.isNullOrBlank()) {
                    updateLoading(false)
                    toast(R.string.auth_login_not_found)
                    return@resolveEmailForLogin
                }
                auth.fetchSignInMethodsForEmail(resolvedEmail)
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

                        auth.signInWithEmailAndPassword(resolvedEmail, password)
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
            },
            onFailure = {
                updateLoading(false)
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
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
    authFlowMode: AuthActivity.AuthFlowMode,
    isSignUpMode: Boolean,
    isLoading: Boolean,
    firebaseReady: Boolean,
    name: String,
    username: String,
    email: String,
    password: String,
    confirmPassword: String,
    verificationMessage: String?,
    palette: GeneratedPalette,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onGoogleUsernameSubmit: () -> Unit,
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
                            text = stringResource(
                                id = if (authFlowMode == AuthActivity.AuthFlowMode.GOOGLE_USERNAME) {
                                    R.string.auth_google_username_title
                                } else {
                                    R.string.auth_welcome
                                }
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        if (authFlowMode == AuthActivity.AuthFlowMode.GOOGLE_USERNAME) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.auth_google_username_body),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = onUsernameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.username_label)) },
                                singleLine = true,
                                enabled = firebaseReady && !isLoading
                            )
                        } else if (isSignUpMode) {
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.name_label)) },
                                singleLine = true,
                                enabled = firebaseReady && !isLoading
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = onUsernameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.username_label)) },
                                singleLine = true,
                                enabled = firebaseReady && !isLoading
                            )
                        }

                        if (authFlowMode != AuthActivity.AuthFlowMode.GOOGLE_USERNAME) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = onEmailChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = if (isSignUpMode) R.string.email_label else R.string.email_or_username_label)) },
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
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = if (authFlowMode == AuthActivity.AuthFlowMode.GOOGLE_USERNAME) onGoogleUsernameSubmit else onPrimaryAction,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = firebaseReady && !isLoading
                        ) {
                            Text(
                                stringResource(
                                    id = if (authFlowMode == AuthActivity.AuthFlowMode.GOOGLE_USERNAME) {
                                        R.string.auth_google_username_continue
                                    } else if (isSignUpMode) {
                                        R.string.sign_up
                                    } else {
                                        R.string.sign_in
                                    }
                                )
                            )
                        }

                        if (authFlowMode != AuthActivity.AuthFlowMode.GOOGLE_USERNAME) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onGoogleSignIn,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = firebaseReady && !isLoading
                            ) {
                                Text(stringResource(id = R.string.continue_with_google))
                            }
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

                        if (authFlowMode != AuthActivity.AuthFlowMode.GOOGLE_USERNAME) {
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
}

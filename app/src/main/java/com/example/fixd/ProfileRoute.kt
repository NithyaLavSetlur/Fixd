package com.example.fixd

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

@Composable
fun ProfileRoute(
    onProfileUpdated: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var currentProfile by remember { mutableStateOf(UserProfile()) }
    var displayName by remember { mutableStateOf("") }
    var emailSummary by remember { mutableStateOf("") }
    var verificationStatus by remember { mutableStateOf("") }
    var isGoogleUser by remember { mutableStateOf(false) }
    var showCredentialsSection by remember { mutableStateOf(false) }
    var focusBodyText by remember { mutableStateOf("") }
    val selectedProblemsState = remember { mutableStateListOf<ProblemArea>() }
    val availableProblemsState = remember { mutableStateListOf<ProblemArea>() }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = auth.currentUser ?: return@LaunchedEffect
        val providers = user.providerData.mapNotNull { it.providerId }
        isGoogleUser = UserPreferences.isGoogleUser(providers)
        emailSummary = user.email ?: context.getString(R.string.guest_label)
        verificationStatus = context.getString(
            if (user.isEmailVerified || isGoogleUser) R.string.profile_verified
            else R.string.profile_unverified
        )
        showCredentialsSection = true
        displayName = user.displayName.orEmpty()

        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                currentProfile = profile
                displayName = profile.preferredName.ifBlank { user.displayName.orEmpty() }
                syncProfileSelections(profile, availableProblemsState, selectedProblemsState)
                focusBodyText = context.getString(
                    if (profile.isPremium) R.string.profile_tab_display_body_premium
                    else R.string.profile_tab_display_body_free
                )
            },
            onFailure = {
                currentProfile = UserProfile()
                displayName = user.displayName.orEmpty()
                syncProfileSelections(currentProfile, availableProblemsState, selectedProblemsState)
                focusBodyText = context.getString(R.string.profile_tab_display_body_free)
                toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready))
            }
        )
    }

    fun toggleProblem(area: ProblemArea, checked: Boolean) {
        val selected = selectedProblemsState.toMutableList()
        if (checked) {
            if (selected.size >= PremiumAccess.maxTabBarProblems(currentProfile.isPremium)) {
                toast(
                    context,
                    context.getString(
                        if (currentProfile.isPremium) R.string.profile_display_limit_premium
                        else R.string.profile_display_limit_free
                    )
                )
                return
            }
            if (area !in selected) selected += area
        } else {
            if (selected.size <= 1 && area in selected) {
                toast(context, context.getString(R.string.profile_display_one_required))
                return
            }
            selected.remove(area)
        }
        selectedProblemsState.clear()
        selectedProblemsState.addAll(selected)
    }

    fun saveName(userId: String) {
        val user = auth.currentUser ?: return
        val newDisplayName = displayName.trim()
        if (newDisplayName.isBlank()) {
            toast(context, context.getString(R.string.profile_name_required))
            return
        }
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(newDisplayName).build())
            .addOnSuccessListener {
                UserProfileRepository.saveProfile(
                    userId = userId,
                    profile = currentProfile.copy(preferredName = newDisplayName),
                    onSuccess = {
                        currentProfile = currentProfile.copy(preferredName = newDisplayName)
                        toast(context, context.getString(R.string.profile_name_saved))
                        onProfileUpdated()
                    },
                    onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
                )
            }
            .addOnFailureListener { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
    }

    fun saveFocusAreas(userId: String) {
        val user = auth.currentUser ?: return
        val sanitizedDisplayName = displayName.trim().ifBlank { user.displayName.orEmpty() }
        if (selectedProblemsState.isEmpty()) {
            toast(context, context.getString(R.string.profile_display_one_required))
            return
        }
        val tabBarProblems = selectedProblemsState.map { it.name }
        val availableProblems = if (currentProfile.isPremium) {
            PremiumAccess.sanitizeAvailableProblems((currentProfile.availableProblems + tabBarProblems).distinct(), true)
        } else {
            currentProfile.availableProblems
        }
        UserProfileRepository.saveProfile(
            userId = userId,
            profile = PremiumAccess.sanitizeProfile(
                currentProfile.copy(
                    preferredName = sanitizedDisplayName,
                    availableProblems = availableProblems,
                    selectedProblems = tabBarProblems
                )
            ),
            onSuccess = {
                currentProfile = currentProfile.copy(
                    preferredName = sanitizedDisplayName,
                    availableProblems = availableProblems,
                    selectedProblems = tabBarProblems
                )
                toast(context, context.getString(R.string.profile_saved))
                onProfileUpdated()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun sendVerificationEmail() {
        val user = auth.currentUser ?: return
        user.sendEmailVerification()
            .addOnSuccessListener { toast(context, context.getString(R.string.profile_verification_sent)) }
            .addOnFailureListener { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
    }

    fun updateCredentials(
        user: FirebaseUser,
        currentPassword: String,
        newEmail: String,
        newPassword: String,
        onSuccess: () -> Unit
    ) {
        if (!user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnSuccessListener { toast(context, context.getString(R.string.profile_verify_before_change)) }
                .addOnFailureListener { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
            return
        }
        if (currentPassword.isBlank()) {
            toast(context, context.getString(R.string.profile_current_password_required))
            return
        }
        if (newEmail.isBlank() && newPassword.isBlank()) {
            toast(context, context.getString(R.string.profile_no_credential_changes))
            return
        }
        if (newEmail.isBlank() && newPassword.isNotBlank() && newPassword.length < 6) {
            toast(context, context.getString(R.string.profile_new_password_too_short))
            return
        }
        if (newEmail.isNotBlank() && newEmail == user.email) {
            toast(context, context.getString(R.string.profile_no_credential_changes))
            return
        }
        val email = user.email ?: return
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                when {
                    newEmail.isNotBlank() -> user.verifyBeforeUpdateEmail(newEmail)
                        .addOnSuccessListener {
                            toast(context, context.getString(R.string.profile_email_update_sent))
                            onSuccess()
                        }
                        .addOnFailureListener { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
                    newPassword.isNotBlank() -> user.updatePassword(newPassword)
                        .addOnSuccessListener {
                            toast(context, context.getString(R.string.profile_password_updated))
                            onSuccess()
                        }
                        .addOnFailureListener { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
                    else -> toast(context, context.getString(R.string.profile_no_credential_changes))
                }
            }
            .addOnFailureListener { toast(context, it.localizedMessage ?: context.getString(R.string.profile_reauth_failed)) }
    }

    val userId = auth.currentUser?.uid.orEmpty()
    val credentialsBody = stringResource(
        id = if (isGoogleUser) R.string.profile_credentials_body_google else R.string.profile_credentials_body
    )

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(modifier = Modifier.height(18.dp)) }
        item {
            ProfileCard(title = stringResource(id = R.string.profile_personal_title)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(id = R.string.name_label)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { saveName(userId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.profile_save_name))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(id = R.string.profile_email_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = emailSummary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = verificationStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isGoogleUser && verificationStatus == context.getString(R.string.profile_unverified)) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(onClick = { sendVerificationEmail() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(id = R.string.profile_send_verification))
                    }
                }
            }
        }
        item {
            ProfileCard(title = stringResource(id = R.string.profile_focus_title)) {
                Text(
                    text = focusBodyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProblemArea.entries.forEach { area ->
                    val enabled = currentProfile.isPremium || area in availableProblemsState
                    val checked = area in selectedProblemsState
                    ProfileProblemRow(
                        label = stringResource(id = area.titleRes),
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = { toggleProblem(area, it) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { saveFocusAreas(userId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.profile_save_focus))
                }
            }
        }
        item {
            if (showCredentialsSection) {
                ProfileCard(title = stringResource(id = R.string.profile_credentials_title)) {
                    Text(
                        text = credentialsBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isGoogleUser) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { showChangeEmailDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.profile_change_email))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { showChangePasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.profile_change_password))
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showChangeEmailDialog) {
        ProfileCredentialDialog(
            title = stringResource(id = R.string.profile_change_email_title),
            secondaryLabel = stringResource(id = R.string.profile_new_email),
            confirmLabel = stringResource(id = R.string.profile_update_action),
            onDismiss = { showChangeEmailDialog = false },
            onConfirm = { currentPassword, newValue ->
                val user = auth.currentUser ?: return@ProfileCredentialDialog
                updateCredentials(user, currentPassword, newValue, "") { showChangeEmailDialog = false }
            }
        )
    }
    if (showChangePasswordDialog) {
        ProfileCredentialDialog(
            title = stringResource(id = R.string.profile_change_password_title),
            secondaryLabel = stringResource(id = R.string.profile_new_password),
            confirmLabel = stringResource(id = R.string.profile_update_action),
            isPasswordDialog = true,
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { currentPassword, newValue ->
                val user = auth.currentUser ?: return@ProfileCredentialDialog
                updateCredentials(user, currentPassword, "", newValue) { showChangePasswordDialog = false }
            }
        )
    }
}

private fun syncProfileSelections(
    profile: UserProfile,
    availableProblemsState: MutableList<ProblemArea>,
    selectedProblemsState: MutableList<ProblemArea>
) {
    val availableSelections = if (profile.isPremium) {
        ProblemArea.entries.toSet()
    } else {
        profile.availableProblems.mapNotNull { ProblemArea.fromName(it) }.toSet()
    }
    val displayedSelections = profile.selectedProblems.mapNotNull { ProblemArea.fromName(it) }.toSet()
    availableProblemsState.clear()
    availableProblemsState.addAll(ProblemArea.entries.filter { it in availableSelections })
    selectedProblemsState.clear()
    selectedProblemsState.addAll(displayedSelections)
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ProfileProblemRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileCredentialDialog(
    title: String,
    secondaryLabel: String,
    confirmLabel: String,
    isPasswordDialog: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(id = R.string.profile_current_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(secondaryLabel) },
                    visualTransformation = if (isPasswordDialog) PasswordVisualTransformation() else VisualTransformation.None,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentPassword.trim(), newValue.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

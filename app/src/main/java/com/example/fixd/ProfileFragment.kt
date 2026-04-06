package com.example.fixd

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class ProfileFragment : Fragment() {

    interface Host {
        fun onProfileUpdated()
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var currentProfile = UserProfile()
    private val displayCheckboxes by lazy {
        mapOf(
            ProblemArea.WAKE_UP to binding.wakeUpCheckbox,
            ProblemArea.SLEEP_SCHEDULE to binding.sleepScheduleCheckbox,
            ProblemArea.TIME_MANAGEMENT to binding.timeManagementCheckbox,
            ProblemArea.TRANSPORT to binding.transportCheckbox,
            ProblemArea.SOCIAL_MEDIA_DISTRACTION to binding.socialMediaDistractionCheckbox,
            ProblemArea.TO_DO to binding.placeholderCheckbox,
            ProblemArea.BREATHE to binding.breatheCheckbox
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemePaletteManager.applyToView(binding.root, ThemePaletteManager.currentPalette(requireContext()))
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val providers = user.providerData.mapNotNull { it.providerId }
        val isGoogleUser = UserPreferences.isGoogleUser(providers)

        binding.emailSummary.text = user.email ?: getString(R.string.guest_label)
        binding.verificationStatus.text = getString(
            if (user.isEmailVerified || isGoogleUser) R.string.profile_verified
            else R.string.profile_unverified
        )

        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                currentProfile = profile
                binding.nameEditText.setText(profile?.preferredName?.ifBlank { user.displayName.orEmpty() } ?: user.displayName.orEmpty())
                bindProblemSelections(profile)
                setupSelectionLimit()
                updateFocusCopy()
            },
            onFailure = {
                currentProfile = UserProfile()
                binding.nameEditText.setText(user.displayName ?: "")
                setupSelectionLimit()
                updateFocusCopy()
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )

        binding.saveProfileButton.setOnClickListener { saveFocusAreas(user.uid) }
        binding.saveNameButton.setOnClickListener { saveName(user.uid) }

        binding.credentialsSection.isVisible = true
        binding.credentialsBody.text = getString(
            if (isGoogleUser) R.string.profile_credentials_body_google
            else R.string.profile_credentials_body
        )
        binding.changeEmailButton.isVisible = !isGoogleUser
        binding.changePasswordButton.isVisible = !isGoogleUser
        binding.sendVerificationButton.isVisible = !isGoogleUser && !user.isEmailVerified
        binding.sendVerificationButton.setOnClickListener {
            user.sendEmailVerification()
                .addOnSuccessListener { toast(R.string.profile_verification_sent) }
                .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        }

        binding.changeEmailButton.setOnClickListener { showChangeEmailDialog() }
        binding.changePasswordButton.setOnClickListener { showChangePasswordDialog() }
    }

    private fun bindProblemSelections(profile: UserProfile?) {
        val availableSelections = if (profile?.isPremium == true) {
            ProblemArea.entries.toSet()
        } else {
            profile?.availableProblems.orEmpty().mapNotNull { ProblemArea.fromName(it) }.toSet()
        }
        val displayedSelections = profile?.selectedProblems.orEmpty().mapNotNull { ProblemArea.fromName(it) }.toSet()
        displayCheckboxes.forEach { (area, checkbox) ->
            checkbox.isEnabled = area in availableSelections
            checkbox.alpha = if (checkbox.isEnabled) 1f else 0.38f
            checkbox.isChecked = area in displayedSelections
        }
    }

    private fun setupSelectionLimit() {
        displayCheckboxes.values.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                val enabledChecked = displayCheckboxes.values.count { it.isEnabled && it.isChecked }
                if (!isChecked && enabledChecked == 0) {
                    (buttonView as CheckBox).isChecked = true
                    toast(R.string.profile_display_one_required)
                    return@setOnCheckedChangeListener
                }
                if (isChecked && enabledChecked > PremiumAccess.maxTabBarProblems(currentProfile.isPremium)) {
                    (buttonView as CheckBox).isChecked = false
                    toast(
                        if (currentProfile.isPremium) R.string.profile_display_limit_premium
                        else R.string.profile_display_limit_free
                    )
                }
            }
        }
    }

    private fun updateFocusCopy() {
        binding.focusBody.text = getString(
            if (currentProfile.isPremium) R.string.profile_tab_display_body_premium
            else R.string.profile_tab_display_body_free
        )
    }

    private fun saveName(userId: String) {
        val user = auth.currentUser ?: return
        val displayName = binding.nameEditText.text?.toString()?.trim().orEmpty()
        if (displayName.isBlank()) {
            toast(R.string.profile_name_required)
            return
        }

        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build())
            .addOnSuccessListener {
                UserProfileRepository.saveProfile(
                    userId = userId,
                    profile = currentProfile.copy(preferredName = displayName),
                    onSuccess = {
                        toast(R.string.profile_name_saved)
                        (activity as? Host)?.onProfileUpdated()
                    },
                    onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                )
            }
            .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
    }

    private fun saveFocusAreas(userId: String) {
        val user = auth.currentUser ?: return
        val displayName = binding.nameEditText.text?.toString()?.trim().orEmpty().ifBlank {
            user.displayName.orEmpty()
        }
        val tabBarProblems = displayCheckboxes
            .filter { (_, checkbox) -> checkbox.isEnabled && checkbox.isChecked }
            .keys
            .map { it.name }
        if (tabBarProblems.isEmpty()) {
            toast(R.string.profile_display_one_required)
            return
        }
        val availableProblems = if (currentProfile.isPremium) {
            PremiumAccess.sanitizeAvailableProblems(
                (currentProfile.availableProblems + tabBarProblems).distinct(),
                true
            )
        } else {
            currentProfile.availableProblems
        }

        UserProfileRepository.saveProfile(
            userId = userId,
            profile = PremiumAccess.sanitizeProfile(currentProfile.copy(
                preferredName = displayName,
                availableProblems = availableProblems,
                selectedProblems = tabBarProblems
            )),
            onSuccess = {
                toast(R.string.profile_saved)
                (activity as? Host)?.onProfileUpdated()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun showChangeEmailDialog() {
        val user = auth.currentUser ?: return
        val dialogView = layoutInflater.inflate(R.layout.view_change_email_dialog, null)
        val currentPasswordEditText = dialogView.findViewById<EditText>(R.id.currentPasswordEditText)
        val newEmailEditText = dialogView.findViewById<EditText>(R.id.newEmailEditText)
        val dialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fixd_Dialog)
            .setTitle(R.string.profile_change_email_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_update_action, null)
            .show()
        ThemePaletteManager.applyToDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            updateCredentials(
                user = user,
                currentPassword = currentPasswordEditText.text?.toString()?.trim().orEmpty(),
                newEmail = newEmailEditText.text?.toString()?.trim().orEmpty(),
                newPassword = ""
            ) { dialog.dismiss() }
        }
    }

    private fun showChangePasswordDialog() {
        val user = auth.currentUser ?: return
        val dialogView = layoutInflater.inflate(R.layout.view_change_password_dialog, null)
        val currentPasswordEditText = dialogView.findViewById<EditText>(R.id.currentPasswordEditText)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.newPasswordEditText)
        val dialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fixd_Dialog)
            .setTitle(R.string.profile_change_password_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_update_action, null)
            .show()
        ThemePaletteManager.applyToDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            updateCredentials(
                user = user,
                currentPassword = currentPasswordEditText.text?.toString()?.trim().orEmpty(),
                newEmail = "",
                newPassword = newPasswordEditText.text?.toString()?.trim().orEmpty()
            ) { dialog.dismiss() }
        }
    }

    private fun updateCredentials(
        user: com.google.firebase.auth.FirebaseUser,
        currentPassword: String,
        newEmail: String,
        newPassword: String,
        onSuccess: () -> Unit
    ) {
        if (!user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnSuccessListener { toast(R.string.profile_verify_before_change) }
                .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
            return
        }

        if (currentPassword.isBlank()) {
            toast(R.string.profile_current_password_required)
            return
        }

        if (newEmail.isBlank() && newPassword.isBlank()) {
            toast(R.string.profile_no_credential_changes)
            return
        }
        if (newEmail.isBlank() && newPassword.isNotBlank() && newPassword.length < 6) {
            toast(R.string.profile_new_password_too_short)
            return
        }
        if (newEmail.isNotBlank() && newEmail == user.email) {
            toast(R.string.profile_no_credential_changes)
            return
        }

        val email = user.email ?: return
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                when {
                    newEmail.isNotBlank() -> user.verifyBeforeUpdateEmail(newEmail)
                        .addOnSuccessListener {
                            toast(R.string.profile_email_update_sent)
                            onSuccess()
                        }
                        .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                    newPassword.isNotBlank() -> user.updatePassword(newPassword)
                        .addOnSuccessListener {
                            toast(R.string.profile_password_updated)
                            onSuccess()
                        }
                        .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                    else -> toast(R.string.profile_no_credential_changes)
                }
            }
            .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.profile_reauth_failed)) }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

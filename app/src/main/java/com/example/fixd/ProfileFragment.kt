package com.example.fixd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val providers = user.providerData.mapNotNull { it.providerId }
        val isGoogleUser = UserPreferences.isGoogleUser(providers)

        binding.emailSummary.text = user.email ?: getString(R.string.guest_label)
        binding.verificationStatus.text = getString(
            if (user.isEmailVerified || isGoogleUser) R.string.profile_verified
            else R.string.profile_unverified
        )

        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                currentProfile = profile ?: UserProfile()
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

        binding.credentialsSection.isVisible = !isGoogleUser
        binding.sendVerificationButton.isVisible = !isGoogleUser && !user.isEmailVerified
        binding.sendVerificationButton.setOnClickListener {
            user.sendEmailVerification()
                .addOnSuccessListener { toast(R.string.profile_verification_sent) }
                .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        }

        binding.saveCredentialsButton.setOnClickListener {
            if (isGoogleUser) return@setOnClickListener
            updateCredentials()
        }
    }

    private fun bindProblemSelections(profile: UserProfile?) {
        val currentSelections = profile?.availableProblems.orEmpty().mapNotNull { ProblemArea.fromName(it) }.toSet()
        binding.wakeUpCheckbox.isChecked = ProblemArea.WAKE_UP in currentSelections || currentSelections.isEmpty()
        configureWakeOnlySelection()
    }

    private fun setupSelectionLimit() {
        binding.wakeUpCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!isChecked) {
                (buttonView as CheckBox).isChecked = true
                toast(R.string.profile_focus_wake_only)
            }
        }
    }

    private fun updateFocusCopy() {
        binding.focusBody.text = getString(R.string.profile_focus_wake_only_body)
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
        val availableProblems = listOf(ProblemArea.WAKE_UP.name)
        val tabBarProblems = listOf(ProblemArea.WAKE_UP.name)

        UserProfileRepository.saveProfile(
            userId = userId,
            profile = currentProfile.copy(
                preferredName = displayName,
                availableProblems = availableProblems,
                selectedProblems = tabBarProblems
            ),
            onSuccess = {
                toast(R.string.profile_saved)
                (activity as? Host)?.onProfileUpdated()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun configureWakeOnlySelection() {
        listOf(
            binding.sleepScheduleCheckbox,
            binding.timeManagementCheckbox,
            binding.transportCheckbox,
            binding.socialMediaDistractionCheckbox,
            binding.placeholderCheckbox
        ).forEach { checkbox ->
            checkbox.isChecked = false
            checkbox.isEnabled = false
            checkbox.alpha = 0.45f
        }
        binding.wakeUpCheckbox.isEnabled = true
        binding.wakeUpCheckbox.alpha = 1f
    }

    private fun updateCredentials() {
        val user = auth.currentUser ?: return
        if (!user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnSuccessListener { toast(R.string.profile_verify_before_change) }
                .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
            return
        }

        val currentPassword = binding.currentPasswordEditText.text?.toString()?.trim().orEmpty()
        if (currentPassword.isBlank()) {
            toast(R.string.profile_current_password_required)
            return
        }

        val email = user.email ?: return
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                val newEmail = binding.newEmailEditText.text?.toString()?.trim().orEmpty()
                val newPassword = binding.newPasswordEditText.text?.toString()?.trim().orEmpty()

                when {
                    newEmail.isNotBlank() -> user.verifyBeforeUpdateEmail(newEmail)
                        .addOnSuccessListener { toast(R.string.profile_email_update_sent) }
                        .addOnFailureListener { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                    newPassword.isNotBlank() -> user.updatePassword(newPassword)
                        .addOnSuccessListener { toast(R.string.profile_password_updated) }
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

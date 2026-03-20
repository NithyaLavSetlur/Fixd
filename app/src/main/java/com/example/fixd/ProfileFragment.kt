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
                binding.nameEditText.setText(profile?.preferredName?.ifBlank { user.displayName.orEmpty() } ?: user.displayName.orEmpty())
                bindProblemSelections(profile)
                setupSelectionLimit()
            },
            onFailure = {
                binding.nameEditText.setText(user.displayName ?: "")
                setupSelectionLimit()
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )

        binding.saveProfileButton.setOnClickListener {
            saveFocusAreas(user.uid)
        }

        binding.saveNameButton.setOnClickListener {
            saveName(user.uid)
        }

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
        val currentSelections = profile?.selectedProblems.orEmpty().mapNotNull { ProblemArea.fromName(it) }.toSet()
        binding.wakeUpCheckbox.isChecked = ProblemArea.WAKE_UP in currentSelections
        binding.sleepScheduleCheckbox.isChecked = ProblemArea.SLEEP_SCHEDULE in currentSelections
        binding.timeManagementCheckbox.isChecked = ProblemArea.TIME_MANAGEMENT in currentSelections
        binding.transportCheckbox.isChecked = ProblemArea.TRANSPORT in currentSelections
        binding.placeholderCheckbox.isChecked = ProblemArea.PLACEHOLDER in currentSelections
        configureAvailableSelections()
    }

    private fun setupSelectionLimit() {
        val checkboxes = listOf(
            binding.wakeUpCheckbox,
            binding.sleepScheduleCheckbox,
            binding.timeManagementCheckbox,
            binding.transportCheckbox,
            binding.placeholderCheckbox
        )
        checkboxes.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked && selectedCount() > 2) {
                    buttonView as CheckBox
                    buttonView.isChecked = false
                    toast(R.string.selection_max_two)
                }
            }
        }
    }

    private fun configureAvailableSelections() {
        listOf(
            binding.sleepScheduleCheckbox,
            binding.timeManagementCheckbox,
            binding.transportCheckbox,
            binding.placeholderCheckbox
        ).forEach { checkbox ->
            checkbox.isChecked = false
            checkbox.isEnabled = false
            checkbox.alpha = 0.45f
        }
    }

    private fun selectedCount(): Int {
        return listOf(
            binding.wakeUpCheckbox,
            binding.sleepScheduleCheckbox,
            binding.timeManagementCheckbox,
            binding.transportCheckbox,
            binding.placeholderCheckbox
        ).count { it.isChecked }
    }

    private fun saveName(userId: String) {
        val user = auth.currentUser ?: return
        val displayName = binding.nameEditText.text?.toString()?.trim().orEmpty()
        if (displayName.isBlank()) {
            toast(R.string.profile_name_required)
            return
        }

        val currentSelections = buildList {
            if (binding.wakeUpCheckbox.isChecked) add(ProblemArea.WAKE_UP)
            if (binding.sleepScheduleCheckbox.isChecked) add(ProblemArea.SLEEP_SCHEDULE)
            if (binding.timeManagementCheckbox.isChecked) add(ProblemArea.TIME_MANAGEMENT)
            if (binding.transportCheckbox.isChecked) add(ProblemArea.TRANSPORT)
            if (binding.placeholderCheckbox.isChecked) add(ProblemArea.PLACEHOLDER)
        }

        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build())
            .addOnSuccessListener {
                UserProfileRepository.saveProfile(
                    userId = userId,
                    profile = UserProfile(
                        preferredName = displayName,
                        selectedProblems = currentSelections.map { it.name }
                    ),
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
        val selectedProblems = buildList {
            if (binding.wakeUpCheckbox.isChecked) add(ProblemArea.WAKE_UP)
            if (binding.sleepScheduleCheckbox.isChecked) add(ProblemArea.SLEEP_SCHEDULE)
            if (binding.timeManagementCheckbox.isChecked) add(ProblemArea.TIME_MANAGEMENT)
            if (binding.transportCheckbox.isChecked) add(ProblemArea.TRANSPORT)
            if (binding.placeholderCheckbox.isChecked) add(ProblemArea.PLACEHOLDER)
        }

        if (selectedProblems.size > 2) {
            toast(R.string.selection_choose_two_or_less)
            return
        }

        UserProfileRepository.saveProfile(
            userId = userId,
            profile = UserProfile(
                preferredName = displayName,
                selectedProblems = selectedProblems.map { it.name }
            ),
            onSuccess = {
                toast(R.string.profile_saved)
                (activity as? Host)?.onProfileUpdated()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
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

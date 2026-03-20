package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fixd.databinding.ActivityProblemSelectionBinding
import com.google.firebase.auth.FirebaseAuth

class ProblemSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProblemSelectionBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                bindExistingSelection(profile)
                configureAvailableSelections()
            },
            onFailure = {
                configureAvailableSelections()
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )

        binding.continueButton.setOnClickListener {
            saveSelections(user.uid)
        }
        binding.skipButton.setOnClickListener {
            Toast.makeText(this, R.string.selection_choose_two, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindExistingSelection(profile: UserProfile?) {
        val currentSelections = profile?.selectedProblems.orEmpty()
            .mapNotNull { ProblemArea.fromName(it) }
            .toSet()
        binding.wakeUpCheckbox.isChecked = ProblemArea.WAKE_UP in currentSelections
        binding.sleepScheduleCheckbox.isChecked = ProblemArea.SLEEP_SCHEDULE in currentSelections
        binding.timeManagementCheckbox.isChecked = ProblemArea.TIME_MANAGEMENT in currentSelections
        binding.transportCheckbox.isChecked = ProblemArea.TRANSPORT in currentSelections
        binding.placeholderCheckbox.isChecked = ProblemArea.PLACEHOLDER in currentSelections
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

    private fun saveSelections(userId: String) {
        val selectedProblems = buildList {
            if (binding.wakeUpCheckbox.isChecked) add(ProblemArea.WAKE_UP)
            if (binding.sleepScheduleCheckbox.isChecked) add(ProblemArea.SLEEP_SCHEDULE)
            if (binding.timeManagementCheckbox.isChecked) add(ProblemArea.TIME_MANAGEMENT)
            if (binding.transportCheckbox.isChecked) add(ProblemArea.TRANSPORT)
            if (binding.placeholderCheckbox.isChecked) add(ProblemArea.PLACEHOLDER)
        }

        if (selectedProblems.isEmpty() || selectedProblems.size > 2) {
            Toast.makeText(this, R.string.selection_choose_two, Toast.LENGTH_SHORT).show()
            return
        }

        val currentName = auth.currentUser?.displayName.orEmpty()
        UserProfileRepository.saveProfile(
            userId = userId,
            profile = UserProfile(
                preferredName = currentName,
                selectedProblems = selectedProblems.map { it.name }
            ),
            onSuccess = {
                LocalAlarmCache.saveAlarms(this, emptyList())
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            },
            onFailure = {
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

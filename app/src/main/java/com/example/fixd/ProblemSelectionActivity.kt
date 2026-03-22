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
    private var isPremium = false

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProblemSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppBackgroundManager.applyToActivity(this)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                AppBackgroundManager.updateSettings(settings)
                ThemePaletteManager.syncFromAppearance(this)
                AppBackgroundManager.applyToActivity(this)
            },
            onFailure = { }
        )

        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { profile ->
                isPremium = profile?.isPremium == true
                bindExistingSelection(profile)
                setupSelectionLimit()
            },
            onFailure = {
                isPremium = false
                setupSelectionLimit()
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )

        binding.continueButton.setOnClickListener {
            saveSelections(user.uid)
        }
    }

    private fun bindExistingSelection(profile: UserProfile?) {
        val currentSelections = profile?.availableProblems.orEmpty()
            .mapNotNull { ProblemArea.fromName(it) }
            .toSet()
        binding.wakeUpCheckbox.isChecked = ProblemArea.WAKE_UP in currentSelections || currentSelections.isEmpty()
        configureWakeOnlySelection()
    }

    private fun setupSelectionLimit() {
        binding.wakeUpCheckbox.setOnCheckedChangeListener { buttonView, checked ->
            if (!checked) {
                (buttonView as? android.widget.CheckBox)?.isChecked = true
                Toast.makeText(this, R.string.selection_wake_only, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSelections(userId: String) {
        val selectedProblems = listOf(ProblemArea.WAKE_UP)

        val currentName = auth.currentUser?.displayName.orEmpty()
        UserProfileRepository.getProfile(
            userId = userId,
            onSuccess = { profile ->
                val premium = profile?.isPremium == true
                val availableProblems = listOf(ProblemArea.WAKE_UP.name)
                val tabBarProblems = listOf(ProblemArea.WAKE_UP.name)
                UserProfileRepository.saveProfile(
                    userId = userId,
                    profile = UserProfile(
                        preferredName = currentName,
                        availableProblems = availableProblems,
                        selectedProblems = tabBarProblems,
                        isPremium = premium,
                        premiumSince = profile?.premiumSince ?: 0L
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
            },
            onFailure = {
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

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
    private val activationCheckboxes by lazy {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProblemSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemePaletteManager.applyToActivity(this)

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
                ThemePaletteManager.updateSettings(settings)
                ThemePaletteManager.syncFromAppearance(this)
            },
            onFailure = { }
        )

        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                isPremium = profile.isPremium
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
        activationCheckboxes.forEach { (area, checkbox) ->
            checkbox.isChecked = area in currentSelections
        }
    }

    private fun setupSelectionLimit() {
        activationCheckboxes.values.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { buttonView, checked ->
                if (!checked && selectedAreas().isEmpty()) {
                    (buttonView as? android.widget.CheckBox)?.isChecked = true
                    Toast.makeText(this, R.string.selection_choose_at_least_one, Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }

                val maxAllowed = PremiumAccess.maxAvailableProblems(isPremium)
                if (checked && selectedAreas().size > maxAllowed) {
                    (buttonView as? android.widget.CheckBox)?.isChecked = false
                    Toast.makeText(
                        this,
                        if (isPremium) R.string.selection_choose_one_to_six else R.string.selection_max_four_available,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveSelections(userId: String) {
        val selectedProblems = selectedAreas()
        if (!isPremium && selectedProblems.size != PremiumAccess.FREE_AVAILABLE_LIMIT) {
            toast(getString(R.string.selection_choose_exactly_four))
            return
        }
        if (isPremium && selectedProblems.isEmpty()) {
            toast(getString(R.string.selection_choose_at_least_one))
            return
        }

        val currentName = auth.currentUser?.displayName.orEmpty()
        val user = auth.currentUser ?: return
        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                val premium = profile.isPremium
                val availableProblems = ProblemSelectionPolicy.activatedProblemsFromSelections(selectedProblems, premium)
                val tabBarProblems = ProblemSelectionPolicy.displayedProblemsForActivation(availableProblems, premium)
                UserProfileRepository.saveProfile(
                    userId = userId,
                    profile = UserProfile(
                        preferredName = currentName,
                        availableProblems = availableProblems,
                        selectedProblems = tabBarProblems,
                        isPremium = premium,
                        premiumSince = profile.premiumSince
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

    private fun selectedAreas(): List<ProblemArea> {
        return activationCheckboxes.filterValues { it.isChecked }.keys.toList()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

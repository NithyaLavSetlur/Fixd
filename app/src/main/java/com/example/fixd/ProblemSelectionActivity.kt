package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

class ProblemSelectionActivity : AppCompatActivity() {

    private enum class SelectionStep {
        ACTIVATE,
        DISPLAY
    }

    private lateinit var auth: FirebaseAuth
    private var isPremium by mutableStateOf(false)
    private var activatedAreas by mutableStateOf(setOf<ProblemArea>())
    private var displayedAreas by mutableStateOf(setOf<ProblemArea>())
    private var currentStep by mutableStateOf(SelectionStep.ACTIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        setContent {
            FixdComposeTheme {
                ProblemSelectionScreen()
            }
        }
        ThemePaletteManager.applyToActivity(this)

        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                ThemePaletteManager.updateSettings(settings)
                UserPreferences.saveThemeMode(this, settings.themeMode)
                UserPreferences.saveThemeSeedColor(this, settings.themeSeedColor)
                UserPreferences.applyThemeMode(settings.themeMode)
            },
            onFailure = { }
        )

        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = { rawProfile ->
                val effectiveProfile = PremiumEntitlement.applyEffectiveEntitlement(user, rawProfile)
                isPremium = effectiveProfile.isPremium

                val existingActivated = rawProfile?.availableProblems
                    ?.mapNotNull { ProblemArea.fromName(it) }
                    ?.toSet()
                    .orEmpty()
                val initialActivated = when {
                    existingActivated.isNotEmpty() -> existingActivated
                    effectiveProfile.isPremium -> ProblemArea.entries.toSet()
                    else -> emptySet()
                }
                activatedAreas = initialActivated

                val existingDisplayed = rawProfile?.selectedProblems
                    ?.mapNotNull { ProblemArea.fromName(it) }
                    ?.filter { it in initialActivated }
                    ?.toSet()
                    .orEmpty()
                displayedAreas = if (existingDisplayed.isNotEmpty()) {
                    existingDisplayed
                } else {
                    initialActivated.take(requiredDisplayCount()).toSet()
                }
            },
            onFailure = {
                isPremium = PremiumEntitlement.hasTesterPremium(user)
                activatedAreas = if (isPremium) ProblemArea.entries.toSet() else emptySet()
                displayedAreas = activatedAreas.take(requiredDisplayCount()).toSet()
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    @Composable
    private fun ProblemSelectionScreen() {
        val isDisplayStep = currentStep == SelectionStep.DISPLAY
        val progress = if (isDisplayStep) 1f else 0.5f
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = stringResource(
                        if (isDisplayStep) R.string.selection_display_title else R.string.selection_title
                    ),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        ProblemArea.entries.forEach { area ->
                            val checked = if (isDisplayStep) area in displayedAreas else area in activatedAreas
                            val enabled = !isDisplayStep || area in activatedAreas
                            ProblemSelectionRow(
                                area = area,
                                checked = checked,
                                enabled = enabled,
                                onToggle = {
                                    if (isDisplayStep) toggleDisplayedArea(area) else toggleActivatedArea(area)
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        if (currentStep == SelectionStep.ACTIVATE) {
                            continueFromActivationStep()
                        } else {
                            auth.currentUser?.uid?.let(::saveSelections)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (currentStep == SelectionStep.ACTIVATE) R.string.selection_continue
                            else R.string.selection_finish
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun ProblemSelectionRow(
        area: ProblemArea,
        checked: Boolean,
        enabled: Boolean,
        onToggle: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
            Text(
                text = stringResource(area.titleRes),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    private fun toggleActivatedArea(area: ProblemArea) {
        val current = activatedAreas.toMutableSet()
        if (area in current) {
            if (current.size == 1) {
                toast(getString(R.string.selection_choose_at_least_one))
                return
            }
            current.remove(area)
        } else {
            val maxAllowed = PremiumAccess.maxAvailableProblems(isPremium)
            if (current.size >= maxAllowed) {
                toast(
                    if (isPremium) getString(R.string.selection_choose_one_to_six)
                    else getString(R.string.selection_max_four_available)
                )
                return
            }
            current.add(area)
        }
        activatedAreas = current
        displayedAreas = displayedAreas.filter { it in current }.toSet()
            .ifEmpty { current.take(requiredDisplayCount()).toSet() }
    }

    private fun toggleDisplayedArea(area: ProblemArea) {
        if (area !in activatedAreas) return
        val current = displayedAreas.toMutableSet()
        if (area in current) {
            current.remove(area)
        } else {
            val required = requiredDisplayCount()
            if (current.size >= required) {
                toast(
                    getString(
                        R.string.selection_choose_exactly_displayed,
                        required
                    )
                )
                return
            }
            current.add(area)
        }
        displayedAreas = current
    }

    private fun continueFromActivationStep() {
        val selectedProblems = activatedAreas.toList()
        if (!isPremium && selectedProblems.size != PremiumAccess.FREE_AVAILABLE_LIMIT) {
            toast(getString(R.string.selection_choose_exactly_four))
            return
        }
        if (isPremium && selectedProblems.isEmpty()) {
            toast(getString(R.string.selection_choose_at_least_one))
            return
        }

        val requiredDisplayCount = requiredDisplayCount()
        displayedAreas = displayedAreas.filter { it in activatedAreas }.toSet()
            .ifEmpty { activatedAreas.take(requiredDisplayCount).toSet() }
        currentStep = SelectionStep.DISPLAY
    }

    private fun requiredDisplayCount(): Int {
        return PremiumAccess.maxTabBarProblems(isPremium)
    }

    private fun saveSelections(userId: String) {
        val selectedProblems = activatedAreas.toList()
        val selectedDisplayProblems = displayedAreas.toList()

        if (!isPremium && selectedProblems.size != PremiumAccess.FREE_AVAILABLE_LIMIT) {
            toast(getString(R.string.selection_choose_exactly_four))
            return
        }
        if (isPremium && selectedProblems.isEmpty()) {
            toast(getString(R.string.selection_choose_at_least_one))
            return
        }

        val requiredDisplayCount = requiredDisplayCount()
        if (selectedDisplayProblems.size != requiredDisplayCount) {
            toast(getString(R.string.selection_choose_exactly_displayed, requiredDisplayCount))
            return
        }

        val currentName = auth.currentUser?.displayName.orEmpty()
        val user = auth.currentUser ?: return
        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                val premium = profile.isPremium
                val availableProblems = ProblemSelectionPolicy.activatedProblemsFromSelections(selectedProblems, premium)
                val tabBarProblems = PremiumAccess.sanitizeTabBarProblems(
                    tabBarProblems = selectedDisplayProblems.map { it.name },
                    availableProblems = availableProblems,
                    isPremium = premium
                )
                UserProfileRepository.saveProfile(
                    userId = userId,
                    profile = UserProfile(
                        preferredName = currentName,
                        username = profile.username,
                        email = user.email.orEmpty(),
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
                    onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                )
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

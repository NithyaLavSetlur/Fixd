package com.example.fixd

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var currentAppearance = UserAppearanceSettings()
    private var pendingSeedColor = ThemePaletteManager.DEFAULT_SEED_COLOR

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return

        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                currentAppearance = settings
                pendingSeedColor = settings.themeSeedColor
                ThemePaletteManager.updateSettings(settings)
                syncCurrentThemeSelection(settings.themeMode)
                bindColorPreview(settings.themeSeedColor)
                bindListeners(user.uid)
            },
            onFailure = {
                syncCurrentThemeSelection(UserPreferences.THEME_SYSTEM)
                bindColorPreview(pendingSeedColor)
                bindListeners(user.uid)
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    private fun bindListeners(userId: String) {
        binding.themeModeGroup.setOnCheckedChangeListener(themeCheckedListener)
        binding.colorWheel.onColorChanged = { color ->
            pendingSeedColor = color
            bindColorPreview(color)
        }
        binding.applyPaletteButton.setOnClickListener {
            applyPalette(userId)
        }
    }

    private fun syncCurrentThemeSelection(themeMode: String) {
        binding.themeModeGroup.check(
            when (themeMode) {
                UserPreferences.THEME_LIGHT -> R.id.themeLight
                UserPreferences.THEME_DARK -> R.id.themeDark
                else -> R.id.themeSystem
            }
        )
    }

    private val themeCheckedListener = RadioGroup.OnCheckedChangeListener { _, checkedId ->
        val newMode = when (checkedId) {
            R.id.themeLight -> UserPreferences.THEME_LIGHT
            R.id.themeDark -> UserPreferences.THEME_DARK
            else -> UserPreferences.THEME_SYSTEM
        }
        if (newMode == currentAppearance.themeMode) return@OnCheckedChangeListener
        val user = auth.currentUser ?: return@OnCheckedChangeListener

        UserAppearanceRepository.saveThemeMode(
            userId = user.uid,
            themeMode = newMode,
            onSuccess = {
                currentAppearance = currentAppearance.copy(themeMode = newMode)
                ThemePaletteManager.updateSettings(currentAppearance)
                UserPreferences.saveThemeMode(requireContext(), newMode)
                UserPreferences.applyThemeMode(newMode)
                requireActivity().recreate()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun bindColorPreview(seedColor: Int) {
        binding.colorWheel.selectedColor = seedColor
        binding.selectedColorLabel.text = getString(
            R.string.settings_palette_selected,
            String.format("#%06X", 0xFFFFFF and seedColor)
        )

        val palette = ThemePaletteManager.paletteFor(
            currentAppearance.copy(themeSeedColor = seedColor),
            UserPreferences.isDarkMode(requireContext())
        )
        tintSwatch(binding.swatchPrimary, palette.primary)
        tintSwatch(binding.swatchSecondary, palette.secondary)
        tintSwatch(binding.swatchSurface, palette.surface)
        tintSwatch(binding.swatchCard, palette.card)
    }

    private fun tintSwatch(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * resources.displayMetrics.density
            setColor(color)
        }
    }

    private fun applyPalette(userId: String) {
        UserAppearanceRepository.saveThemeSeedColor(
            userId = userId,
            themeSeedColor = pendingSeedColor,
            onSuccess = { saved ->
                currentAppearance = currentAppearance.copy(themeSeedColor = saved.themeSeedColor)
                ThemePaletteManager.updateSettings(currentAppearance)
                requireActivity().recreate()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.fixd

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var currentThemeMode: String = UserPreferences.THEME_SYSTEM
    private var selectedBackgroundUri: Uri? = null
    private var currentBackgroundSettings = UserAppearanceSettings()

    private val pickBackgroundLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            selectedBackgroundUri = uri
            updateBackgroundPreview()
        }

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
                currentThemeMode = settings.themeMode
                currentBackgroundSettings = settings
                AppBackgroundManager.updateSettings(settings)
                selectedBackgroundUri = null
                binding.backgroundBrightnessSeekBar.progress =
                    (settings.backgroundBrightness - 35).coerceIn(0, 145)
                binding.backgroundBlurSeekBar.progress = settings.backgroundBlur.coerceIn(0, 25)
                refreshBackgroundLabels()
                syncCurrentThemeSelection()
                updateBackgroundPreview()
                bindListeners(user.uid)
            },
            onFailure = {
                syncCurrentThemeSelection()
                refreshBackgroundLabels()
                updateBackgroundPreview()
                bindListeners(user.uid)
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    private fun bindListeners(userId: String) {
        binding.themeModeGroup.setOnCheckedChangeListener(themeCheckedListener)
        binding.backgroundBrightnessSeekBar.setOnSeekBarChangeListener(backgroundSeekListener)
        binding.backgroundBlurSeekBar.setOnSeekBarChangeListener(backgroundSeekListener)
        binding.chooseBackgroundButton.setOnClickListener {
            pickBackgroundLauncher.launch(arrayOf("image/*"))
        }
        binding.applyBackgroundButton.setOnClickListener {
            applyBackgroundSettings(userId)
        }
        binding.removeBackgroundButton.setOnClickListener {
            removeBackground(userId)
        }
    }

    private fun syncCurrentThemeSelection() {
        binding.themeModeGroup.check(
            when (currentThemeMode) {
                UserPreferences.THEME_LIGHT -> R.id.themeLight
                UserPreferences.THEME_DARK -> R.id.themeDark
                else -> R.id.themeSystem
            }
        )
    }

    private val themeCheckedListener = RadioGroup.OnCheckedChangeListener { _, checkedId ->
        val user = auth.currentUser ?: return@OnCheckedChangeListener
        val newMode = when (checkedId) {
            R.id.themeLight -> UserPreferences.THEME_LIGHT
            R.id.themeDark -> UserPreferences.THEME_DARK
            else -> UserPreferences.THEME_SYSTEM
        }
        if (newMode == currentThemeMode) return@OnCheckedChangeListener

        UserAppearanceRepository.saveThemeMode(
            userId = user.uid,
            themeMode = newMode,
            onSuccess = {
                currentThemeMode = newMode
                AppBackgroundManager.updateSettings(
                    AppBackgroundManager.currentSettings().copy(themeMode = newMode)
                )
                UserPreferences.saveThemeMode(requireContext(), newMode)
                UserPreferences.applyThemeMode(newMode)
                ThemePaletteManager.syncFromAppearance(requireContext())
                requireActivity().recreate()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private val backgroundSeekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            refreshBackgroundLabels()
            updateBackgroundPreview()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun refreshBackgroundLabels() {
        binding.backgroundBrightnessLabel.text = getString(
            R.string.profile_background_brightness,
            currentBrightness()
        )
        binding.backgroundBlurLabel.text = getString(
            R.string.profile_background_blur,
            currentBlur()
        )
    }

    private fun currentBrightness(): Int = binding.backgroundBrightnessSeekBar.progress + 35

    private fun currentBlur(): Int = binding.backgroundBlurSeekBar.progress

    private fun updateBackgroundPreview() {
        val uri = selectedBackgroundUri
        if (uri != null) {
            binding.backgroundPreviewHint.text =
                uri.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.profile_background_choose)
            AppBackgroundManager.renderPreview(
                requireContext(),
                binding.backgroundPreviewImage,
                AppBackgroundSettings(uri = uri.toString(), brightness = currentBrightness(), blur = currentBlur())
            )
            return
        }

        if (currentBackgroundSettings.hasBackground()) {
            binding.backgroundPreviewHint.text = getString(R.string.profile_background_saved)
            AppBackgroundManager.updateSettings(
                currentBackgroundSettings.copy(
                    backgroundBrightness = currentBrightness(),
                    backgroundBlur = currentBlur()
                )
            )
            AppBackgroundManager.applyToActivity(requireActivity())
            binding.backgroundPreviewImage.setImageDrawable(null)
            return
        }

        binding.backgroundPreviewImage.setImageDrawable(null)
        binding.backgroundPreviewHint.text = getString(R.string.profile_background_empty)
    }

    private fun applyBackgroundSettings(userId: String) {
        val uri = selectedBackgroundUri ?: run {
            toast(R.string.profile_background_required)
            return
        }

        val imageBytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run {
            toast(R.string.profile_background_required)
            return
        }
        val uploadBytes = UserAppearanceRepository.compressForUpload(imageBytes)
        val matchedPalette = ThemePaletteManager.nearestPalette(uploadBytes)

        UserAppearanceRepository.saveBackground(
            userId = userId,
            imageBytes = uploadBytes,
            brightness = currentBrightness(),
            blur = currentBlur(),
            onSuccess = { saved ->
                UserAppearanceRepository.savePaletteId(
                    userId = userId,
                    paletteId = matchedPalette.id,
                    onSuccess = {
                        currentBackgroundSettings = currentBackgroundSettings.copy(
                            paletteId = matchedPalette.id,
                            backgroundStoragePath = saved.backgroundStoragePath,
                            backgroundBrightness = saved.backgroundBrightness,
                            backgroundBlur = saved.backgroundBlur
                        )
                        AppBackgroundManager.updateSettings(currentBackgroundSettings)
                        AppBackgroundManager.applyToActivity(requireActivity())
                        ThemePaletteManager.syncFromAppearance(requireContext())
                        selectedBackgroundUri = null
                        updateBackgroundPreview()
                        requireActivity().recreate()
                    },
                    onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                )
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun removeBackground(userId: String) {
        UserAppearanceRepository.clearBackground(
            userId = userId,
            storagePath = currentBackgroundSettings.backgroundStoragePath,
            onSuccess = {
                UserAppearanceRepository.savePaletteId(
                    userId = userId,
                    paletteId = ThemePaletteManager.PALETTE_OCEAN,
                    onSuccess = {
                        currentBackgroundSettings = currentBackgroundSettings.copy(
                            paletteId = ThemePaletteManager.PALETTE_OCEAN,
                            backgroundStoragePath = "",
                            backgroundBrightness = AppBackgroundManager.DEFAULT_BRIGHTNESS,
                            backgroundBlur = AppBackgroundManager.DEFAULT_BLUR
                        )
                        AppBackgroundManager.updateSettings(currentBackgroundSettings)
                        selectedBackgroundUri = null
                        binding.backgroundBrightnessSeekBar.progress = AppBackgroundManager.DEFAULT_BRIGHTNESS - 35
                        binding.backgroundBlurSeekBar.progress = AppBackgroundManager.DEFAULT_BLUR
                        refreshBackgroundLabels()
                        updateBackgroundPreview()
                        ThemePaletteManager.syncFromAppearance(requireContext())
                        requireActivity().recreate()
                    },
                    onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                )
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

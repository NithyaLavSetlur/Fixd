package com.example.fixd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentPremiumBinding
import com.google.firebase.auth.FirebaseAuth

class PremiumFragment : Fragment() {
    private var _binding: FragmentPremiumBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var currentProfile = UserProfile()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemePaletteManager.applyToView(binding.root, ThemePaletteManager.currentPalette(requireContext()))
        auth = FirebaseAuth.getInstance()
        binding.purchaseButton.setOnClickListener { }
        binding.restoreButton.setOnClickListener { }
        loadProfile()
    }

    private fun loadProfile() {
        val user = auth.currentUser ?: return
        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                currentProfile = profile
                renderProfileState()
            },
            onFailure = {
                currentProfile = UserProfile()
                renderProfileState()
            }
        )
    }

    private fun renderProfileState() {
        binding.statusChip.text = getString(
            if (currentProfile.isPremium) R.string.premium_status_active else R.string.premium_status_free
        )
        binding.priceText.text = getString(R.string.premium_placeholder_price)
        binding.purchaseButton.isEnabled = false
        binding.restoreButton.isEnabled = false
        binding.purchaseButton.text = getString(
            if (currentProfile.isPremium) R.string.premium_already_unlocked else R.string.premium_placeholder_button
        )
        binding.restoreButton.text = getString(R.string.premium_placeholder_secondary)
        binding.statusText.text = if (currentProfile.isPremium) {
            getString(R.string.premium_active_summary)
        } else {
            getString(R.string.premium_placeholder_body)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

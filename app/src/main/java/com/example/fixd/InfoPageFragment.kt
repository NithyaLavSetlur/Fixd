package com.example.fixd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentInfoPageBinding

class InfoPageFragment : Fragment() {

    private var _binding: FragmentInfoPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfoPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemePaletteManager.applyToView(binding.root, ThemePaletteManager.currentPalette(requireContext()))
        binding.pageTitle.text = requireArguments().getString(ARG_TITLE)
        binding.pageBody.text = requireArguments().getString(ARG_BODY)

        val cta = requireArguments().getString(ARG_CTA)
        binding.primaryButton.isVisible = !cta.isNullOrBlank()
        binding.primaryButton.text = cta
        binding.primaryButton.isEnabled = requireArguments().getBoolean(ARG_CTA_ENABLED, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_BODY = "body"
        private const val ARG_CTA = "cta"
        private const val ARG_CTA_ENABLED = "cta_enabled"

        fun newInstance(
            title: String,
            body: String,
            cta: String? = null,
            ctaEnabled: Boolean = true
        ): InfoPageFragment {
            return InfoPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_BODY, body)
                    putString(ARG_CTA, cta)
                    putBoolean(ARG_CTA_ENABLED, ctaEnabled)
                }
            }
        }
    }
}

package com.example.fixd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentHomeBinding
import com.example.fixd.databinding.ViewFocusCardBinding

class HomeFragment : Fragment() {

    interface Host {
        fun openHomeArea(area: ProblemArea)
        fun openTabDisplayChooser()
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selectedProblems = requireArguments()
            .getStringArrayList(ARG_PROBLEMS)
            .orEmpty()
            .mapNotNull { ProblemArea.fromName(it) }
        val palette = ThemePaletteManager.currentPalette(requireContext())
        ThemePaletteManager.applyToView(binding.root, palette)

        binding.focusList.removeAllViews()
        val isEmptyState = selectedProblems.isEmpty()
        binding.emptyStateGroup.visibility = if (isEmptyState) View.VISIBLE else View.GONE
        selectedProblems.forEach { area ->
            val itemView = layoutInflater.inflate(R.layout.view_focus_card, binding.focusList, false)
            val itemBinding = ViewFocusCardBinding.bind(itemView)
            itemBinding.focusIcon.setImageResource(area.iconRes)
            itemBinding.focusTitle.setText(area.titleRes)
            itemBinding.focusSubtitle.setText(area.subtitleRes)
            itemBinding.root.setOnClickListener {
                (activity as? Host)?.openHomeArea(area)
            }
            ThemePaletteManager.applyToView(itemView, palette)
            binding.focusList.addView(itemView)
        }
        binding.chooseTabsButton.setOnClickListener {
            (activity as? Host)?.openTabDisplayChooser()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PROBLEMS = "problems"

        fun newInstance(selectedProblems: List<ProblemArea>): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_PROBLEMS, ArrayList(selectedProblems.map { it.name }))
                }
            }
        }
    }
}

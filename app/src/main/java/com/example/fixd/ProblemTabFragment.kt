package com.example.fixd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentProblemTabBinding

class ProblemTabFragment : Fragment() {

    private var _binding: FragmentProblemTabBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProblemTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val areaName = requireArguments().getString(ARG_AREA_NAME).orEmpty()
        val area = ProblemArea.fromName(areaName) ?: ProblemArea.PLACEHOLDER

        if (area == ProblemArea.WAKE_UP) {
            childFragmentManager.beginTransaction()
                .replace(R.id.wakeUpContainer, WakeUpFragment())
                .commit()
            binding.problemContentGroup.visibility = View.GONE
            binding.wakeUpContainer.visibility = View.VISIBLE
            return
        }

        binding.problemIcon.setImageResource(area.iconRes)
        binding.sectionTitle.setText(area.titleRes)
        binding.sectionSubtitle.setText(area.subtitleRes)
        binding.problemContentGroup.visibility = View.VISIBLE
        binding.wakeUpContainer.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_AREA_NAME = "area_name"

        fun newInstance(area: ProblemArea): ProblemTabFragment {
            return ProblemTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_AREA_NAME, area.name)
                }
            }
        }
    }
}

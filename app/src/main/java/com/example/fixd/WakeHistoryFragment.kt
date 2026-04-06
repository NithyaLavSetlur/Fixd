package com.example.fixd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentWakeHistoryBinding
import com.example.fixd.databinding.ViewHistoryTileBinding
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.max

class WakeHistoryFragment : Fragment() {

    private var _binding: FragmentWakeHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var allSubmissions: List<WakeSubmission> = emptyList()
    private var spinnerRenderReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWakeHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setupFilters()
        loadHistory()
    }

    private fun setupFilters() {
        val palette = ThemePaletteManager.currentPalette(requireContext())
        binding.typeFilterSpinner.adapter = buildAdapter(R.array.wake_history_type_filters, palette)
        binding.timeFilterSpinner.adapter = buildAdapter(R.array.wake_history_time_filters, palette)
        binding.durationFilterSpinner.adapter = buildAdapter(R.array.wake_history_duration_filters, palette)
        binding.statusFilterSpinner.adapter = buildAdapter(R.array.wake_history_status_filters, palette)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnerRenderReady) return
                renderHistory()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.typeFilterSpinner.onItemSelectedListener = listener
        binding.timeFilterSpinner.onItemSelectedListener = listener
        binding.durationFilterSpinner.onItemSelectedListener = listener
        binding.statusFilterSpinner.onItemSelectedListener = listener
        spinnerRenderReady = true
    }

    private fun buildAdapter(arrayRes: Int, palette: GeneratedPalette): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(arrayRes).toList()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).applySpinnerColors(palette, collapsed = true)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).applySpinnerColors(palette, collapsed = false)
            }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun View.applySpinnerColors(palette: GeneratedPalette, collapsed: Boolean): View {
        (this as? TextView)?.let { textView ->
            textView.setTextColor(palette.text)
            textView.setBackgroundColor(if (collapsed) palette.card else palette.surface)
            val horizontal = if (collapsed) dp(12) else dp(16)
            val vertical = if (collapsed) dp(10) else dp(12)
            textView.setPadding(horizontal, vertical, horizontal, vertical)
        }
        return this
    }

    private fun loadHistory() {
        val user = auth.currentUser ?: return
        AlarmRepository.getSuccessfulSubmissions(
            userId = user.uid,
            onSuccess = { submissions ->
                allSubmissions = submissions
                renderHistory()
            },
            onFailure = {
                binding.historyEmptyText.isVisible = true
                binding.historyEmptyText.text = it.localizedMessage ?: getString(R.string.firebase_not_ready)
                binding.historyCountText.text = ""
            }
        )
    }

    private fun renderHistory() {
        val palette = ThemePaletteManager.currentPalette(requireContext())
        val filtered = allSubmissions.filter(::matchesSelectedFilters)
        binding.historyRows.removeAllViews()
        binding.historyCountText.text = resources.getQuantityString(
            R.plurals.wake_history_result_count,
            filtered.size,
            filtered.size
        )
        binding.historyEmptyText.isVisible = filtered.isEmpty()
        if (filtered.isEmpty()) return

        filtered.chunked(2).forEach { rowItems ->
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            rowItems.forEachIndexed { index, submission ->
                val tileBinding = ViewHistoryTileBinding.inflate(layoutInflater, rowLayout, false)
                tileBinding.tileDate.text = WakeSubmissionUi.formatDate(WakeSubmissionUi.primaryTimestamp(submission))
                tileBinding.tileAlarmTime.text = getString(
                    R.string.wake_history_tile_alarm_time,
                    WakeSubmissionUi.formatAlarmTime(submission.alarmHour, submission.alarmMinute)
                )
                tileBinding.tileType.text = WakeSubmissionUi.typeLabel(requireContext(), submission)
                tileBinding.tileWakeStatus.text = WakeSubmissionUi.wakeStatusLabel(requireContext(), submission)
                WakeSubmissionUi.bindWakeStatus(tileBinding.tileWakeStatus, submission, palette)
                tileBinding.root.setOnClickListener {
                    WakeSubmissionUi.showDetails(
                        context = requireContext(),
                        inflater = layoutInflater,
                        userId = auth.currentUser?.uid.orEmpty(),
                        submission = submission
                    ) {
                        allSubmissions = allSubmissions.map { existing ->
                            if (existing.id == it.id) it else existing
                        }
                        renderHistory()
                    }
                }

                val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (index == 0 && rowItems.size > 1) dp(6) else 0
                    marginStart = if (index == 1) dp(6) else 0
                }
                ThemePaletteManager.applyToView(tileBinding.root, palette)
                rowLayout.addView(tileBinding.root, params)
            }

            if (rowItems.size == 1) {
                rowLayout.addView(View(requireContext()), LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = dp(6)
                })
            }

            binding.historyRows.addView(rowLayout)
        }
    }

    private fun matchesSelectedFilters(submission: WakeSubmission): Boolean {
        val typeMatches = when (binding.typeFilterSpinner.selectedItemPosition) {
            1 -> submission.type == "text"
            2 -> submission.type == "image"
            else -> true
        }

        val now = System.currentTimeMillis()
        val ageMillis = max(0L, now - WakeSubmissionUi.primaryTimestamp(submission))
        val timeMatches = when (binding.timeFilterSpinner.selectedItemPosition) {
            1 -> ageMillis <= 7L * 24L * 60L * 60L * 1000L
            2 -> ageMillis <= 30L * 24L * 60L * 60L * 1000L
            3 -> ageMillis <= 365L * 24L * 60L * 60L * 1000L
            else -> true
        }

        val durationSeconds = max(0L, submission.responseDurationMs / 1000L)
        val durationMatches = when (binding.durationFilterSpinner.selectedItemPosition) {
            1 -> durationSeconds <= 10L
            2 -> durationSeconds in 11L..20L
            3 -> durationSeconds in 21L..30L
            4 -> durationSeconds in 31L..40L
            5 -> durationSeconds in 41L..50L
            6 -> durationSeconds >= 51L
            else -> true
        }

        val statusMatches = when (binding.statusFilterSpinner.selectedItemPosition) {
            1 -> submission.wakeStatus == "awake"
            2 -> submission.wakeStatus == "asleep"
            3 -> submission.wakeStatus == "pending"
            else -> true
        }

        return typeMatches && timeMatches && durationMatches && statusMatches
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

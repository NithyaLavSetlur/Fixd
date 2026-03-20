package com.example.fixd

import android.Manifest
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentWakeUpBinding
import com.example.fixd.databinding.ViewAlarmCardBinding
import com.example.fixd.databinding.ViewAlarmEditorBinding
import com.example.fixd.databinding.ViewSubmissionCardBinding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class WakeUpFragment : Fragment() {

    private var _binding: FragmentWakeUpBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWakeUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        NotificationHelper.ensureChannels(requireContext())
        requestNotificationPermissionIfNeeded()
        renderExactAlarmState()

        binding.createAlarmButton.setOnClickListener {
            if (!canScheduleExactAlarms()) {
                startActivity(android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return@setOnClickListener
            }
            showAlarmEditor()
        }

        loadWakeData()
    }

    private fun loadWakeData() {
        loadAlarms()
        loadHistory()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        val alarmManager = requireContext().getSystemService(android.app.AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun renderExactAlarmState() {
        binding.exactAlarmStatus.text = getString(
            if (canScheduleExactAlarms()) R.string.wake_exact_alarm_enabled
            else R.string.wake_exact_alarm_disabled
        )
    }

    private fun showAlarmEditor(existingAlarm: WakeAlarm? = null) {
        val dialogBinding = ViewAlarmEditorBinding.inflate(layoutInflater)
        var selectedHour = existingAlarm?.hour ?: 7
        var selectedMinute = existingAlarm?.minute ?: 0
        dialogBinding.nameEditText.setText(existingAlarm?.name.orEmpty())
        setRepeatDaySelection(dialogBinding, existingAlarm?.repeatDays ?: listOf(2, 3, 4, 5, 6))
        dialogBinding.timeButton.text = formatTime(selectedHour, selectedMinute)

        dialogBinding.timeButton.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText(R.string.wake_picker_title)
                .build()
            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                dialogBinding.timeButton.text = formatTime(selectedHour, selectedMinute)
            }
            picker.show(parentFragmentManager, "wake_time_picker")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingAlarm == null) R.string.wake_editor_title else R.string.wake_editor_edit_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.wake_editor_save, null)
            .setNeutralButton(
                if (existingAlarm == null) null else getString(R.string.wake_editor_delete),
                null
            )
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val alarmName = dialogBinding.nameEditText.text?.toString()?.trim().orEmpty()
                val repeatDays = collectRepeatDays(dialogBinding)

                if (alarmName.isBlank()) {
                    toast(R.string.wake_alarm_name_required)
                    return@setOnClickListener
                }
                if (repeatDays.isEmpty()) {
                    toast(R.string.wake_alarm_repeat_required)
                    return@setOnClickListener
                }

                val user = auth.currentUser ?: return@setOnClickListener
                val alarm = (existingAlarm ?: WakeAlarm()).copy(
                    name = alarmName,
                    hour = selectedHour,
                    minute = selectedMinute,
                    repeatDays = repeatDays
                )
                AlarmRepository.saveAlarm(
                    userId = user.uid,
                    alarm = alarm,
                    onSuccess = { saved ->
                        AlarmScheduler.cancel(requireContext(), saved.id)
                        if (saved.enabled) {
                            AlarmScheduler.schedule(requireContext(), saved)
                        }
                        toast(if (existingAlarm == null) R.string.wake_alarm_saved else R.string.wake_alarm_updated)
                        dialog.dismiss()
                        loadWakeData()
                    },
                    onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                )
            }

            if (existingAlarm != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val user = auth.currentUser ?: return@setOnClickListener
                    AlarmRepository.deleteAlarm(
                        userId = user.uid,
                        alarmId = existingAlarm.id,
                        onSuccess = {
                            AlarmScheduler.cancel(requireContext(), existingAlarm.id)
                            toast(R.string.wake_alarm_deleted)
                            dialog.dismiss()
                            loadWakeData()
                        },
                        onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                    )
                }
            }
        }

        dialog.show()
    }

    private fun loadAlarms() {
        val user = auth.currentUser ?: return
        AlarmRepository.getAlarms(
            userId = user.uid,
            onSuccess = loadSuccess@{ alarms ->
                LocalAlarmCache.saveAlarms(requireContext(), alarms)
                binding.alarmList.removeAllViews()
                if (alarms.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    return@loadSuccess
                }

                binding.emptyState.visibility = View.GONE
                alarms.forEach { alarm ->
                    val itemView = layoutInflater.inflate(R.layout.view_alarm_card, binding.alarmList, false)
                    val itemBinding = ViewAlarmCardBinding.bind(itemView)
                    itemBinding.alarmName.text = alarm.name
                    itemBinding.alarmTime.text = formatTime(alarm.hour, alarm.minute)
                    itemBinding.alarmDays.text = formatAlarmSchedule(alarm.repeatDays)
                    itemBinding.alarmState.text = getString(
                        if (alarm.enabled) R.string.wake_alarm_active else R.string.wake_alarm_inactive
                    )
                    itemBinding.alarmSwitch.isChecked = alarm.enabled
                    itemBinding.alarmSwitch.setOnCheckedChangeListener { _, checked ->
                        val updated = alarm.copy(enabled = checked)
                        AlarmRepository.saveAlarm(
                            userId = user.uid,
                            alarm = updated,
                            onSuccess = {
                                val refreshed = alarms.map { existing ->
                                    if (existing.id == it.id) it else existing
                                }
                                LocalAlarmCache.saveAlarms(requireContext(), refreshed)
                                if (checked) AlarmScheduler.schedule(requireContext(), it)
                                else AlarmScheduler.cancel(requireContext(), it.id)
                                renderExactAlarmState()
                            },
                            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
                        )
                    }
                    itemBinding.root.setOnClickListener {
                        showAlarmEditor(alarm)
                    }
                    binding.alarmList.addView(itemView)
                }
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun loadHistory() {
        val user = auth.currentUser ?: return
        AlarmRepository.getSuccessfulSubmissions(
            userId = user.uid,
            onSuccess = { submissions ->
                binding.historyList.removeAllViews()
                binding.historyEmpty.visibility = if (submissions.isEmpty()) View.VISIBLE else View.GONE
                submissions.forEach { submission ->
                    val itemView = layoutInflater.inflate(R.layout.view_submission_card, binding.historyList, false)
                    val itemBinding = ViewSubmissionCardBinding.bind(itemView)
                    itemBinding.submissionType.text = getString(
                        if (submission.type == "image") R.string.wake_history_image else R.string.wake_history_text
                    )
                    itemBinding.alarmMeta.text = getString(
                        R.string.wake_history_alarm_meta,
                        formatTime(submission.alarmHour, submission.alarmMinute),
                        formatDuration(submission.responseDurationMs)
                    )
                    itemBinding.feedback.text = submission.feedback

                    if (submission.type == "image" && submission.imagePath.isNotBlank()) {
                        val file = File(submission.imagePath)
                        if (file.exists()) {
                            itemBinding.submissionPreview.visibility = View.VISIBLE
                            itemBinding.submissionText.visibility = View.GONE
                            itemBinding.submissionPreview.setImageBitmap(
                                BitmapFactory.decodeFile(file.absolutePath)
                            )
                        } else {
                            itemBinding.submissionPreview.visibility = View.GONE
                            itemBinding.submissionText.visibility = View.VISIBLE
                            itemBinding.submissionText.text = getString(R.string.wake_history_image_unavailable)
                        }
                    } else {
                        itemBinding.submissionPreview.visibility = View.GONE
                        itemBinding.submissionText.visibility = View.VISIBLE
                        itemBinding.submissionText.text = submission.text
                    }
                    binding.historyList.addView(itemView)
                }
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val safeHour = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour >= 12) "PM" else "AM"
        return String.format(Locale.getDefault(), "%d:%02d %s", safeHour, minute, suffix)
    }

    private fun setRepeatDaySelection(binding: ViewAlarmEditorBinding, days: List<Int>) {
        val daySet = days.toSet()
        binding.sundayCheckbox.isChecked = 1 in daySet
        binding.mondayCheckbox.isChecked = 2 in daySet
        binding.tuesdayCheckbox.isChecked = 3 in daySet
        binding.wednesdayCheckbox.isChecked = 4 in daySet
        binding.thursdayCheckbox.isChecked = 5 in daySet
        binding.fridayCheckbox.isChecked = 6 in daySet
        binding.saturdayCheckbox.isChecked = 7 in daySet
    }

    private fun collectRepeatDays(binding: ViewAlarmEditorBinding): List<Int> = buildList {
        if (binding.sundayCheckbox.isChecked) add(1)
        if (binding.mondayCheckbox.isChecked) add(2)
        if (binding.tuesdayCheckbox.isChecked) add(3)
        if (binding.wednesdayCheckbox.isChecked) add(4)
        if (binding.thursdayCheckbox.isChecked) add(5)
        if (binding.fridayCheckbox.isChecked) add(6)
        if (binding.saturdayCheckbox.isChecked) add(7)
    }

    private fun formatAlarmSchedule(days: List<Int>): String {
        val normalizedDays = days.sorted().distinct()
        return when (normalizedDays) {
            listOf(1, 2, 3, 4, 5, 6, 7) -> getString(R.string.wake_alarm_every_day)
            listOf(2, 3, 4, 5, 6) -> getString(R.string.wake_alarm_weekdays)
            listOf(1, 7) -> getString(R.string.wake_alarm_weekends)
            else -> formatDays(normalizedDays)
        }
    }

    private fun formatDays(days: List<Int>): String {
        val labels = mapOf(
            1 to getString(R.string.day_sun),
            2 to getString(R.string.day_mon),
            3 to getString(R.string.day_tue),
            4 to getString(R.string.day_wed),
            5 to getString(R.string.day_thu),
            6 to getString(R.string.day_fri),
            7 to getString(R.string.day_sat)
        )
        return days.sorted().mapNotNull { labels[it] }.joinToString(" | ")
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            getString(R.string.wake_duration_minutes_seconds, minutes, seconds)
        } else {
            getString(R.string.wake_duration_seconds, seconds)
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

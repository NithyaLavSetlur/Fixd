package com.example.fixd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.auth.FirebaseAuth

@Composable
fun WakeRoute(
    pendingCreateAlarm: Boolean,
    onCreateAlarmConsumed: () -> Unit,
    onShowFullHistory: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var alarms by remember { mutableStateOf<List<WakeAlarm>>(emptyList()) }
    var submissions by remember { mutableStateOf<List<WakeSubmission>>(emptyList()) }
    var exactAlarmEnabled by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var editingAlarm by remember { mutableStateOf<WakeAlarm?>(null) }
    var creatingAlarm by remember { mutableStateOf(false) }
    var selectedSubmission by remember { mutableStateOf<WakeSubmission?>(null) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)

    fun loadWakeData() {
        val user = auth.currentUser ?: return
        AlarmRepository.getAlarms(
            userId = user.uid,
            onSuccess = { loaded ->
                alarms = loaded
                exactAlarmEnabled = canScheduleExactAlarms(latestContext)
                LocalAlarmCache.saveAlarms(latestContext, loaded)
            },
            onFailure = { toast(latestContext, it.localizedMessage ?: latestContext.getString(R.string.firebase_not_ready)) }
        )
        AlarmRepository.getSuccessfulSubmissions(
            userId = user.uid,
            onSuccess = { loaded ->
                submissions = loaded
                WakeSubmissionCache.saveSubmissions(latestContext, loaded)
                WakeWidgetUpdater.updateAll(latestContext)
            },
            onFailure = { toast(latestContext, it.localizedMessage ?: latestContext.getString(R.string.firebase_not_ready)) }
        )
    }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        loadWakeData()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmEnabled = canScheduleExactAlarms(latestContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingCreateAlarm) {
        if (pendingCreateAlarm) {
            creatingAlarm = true
            onCreateAlarmConsumed()
        }
    }

    fun saveAlarm(existingAlarm: WakeAlarm?, name: String, hour: Int, minute: Int, repeatDays: List<Int>) {
        if (name.isBlank()) {
            toast(context, context.getString(R.string.wake_alarm_name_required))
            return
        }
        if (repeatDays.isEmpty()) {
            toast(context, context.getString(R.string.wake_alarm_repeat_required))
            return
        }
        val user = auth.currentUser ?: return
        val alarm = (existingAlarm ?: WakeAlarm()).copy(name = name, hour = hour, minute = minute, repeatDays = repeatDays)
        AlarmRepository.saveAlarm(
            userId = user.uid,
            alarm = alarm,
            onSuccess = { saved ->
                AlarmScheduler.cancel(context, saved.id)
                if (saved.enabled) AlarmScheduler.schedule(context, saved)
                creatingAlarm = false
                editingAlarm = null
                toast(context, context.getString(if (existingAlarm == null) R.string.wake_alarm_saved else R.string.wake_alarm_updated))
                loadWakeData()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun deleteAlarm(alarm: WakeAlarm) {
        val user = auth.currentUser ?: return
        AlarmRepository.deleteAlarm(
            userId = user.uid,
            alarmId = alarm.id,
            onSuccess = {
                AlarmScheduler.cancel(context, alarm.id)
                creatingAlarm = false
                editingAlarm = null
                toast(context, context.getString(R.string.wake_alarm_deleted))
                loadWakeData()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun toggleAlarm(alarm: WakeAlarm, checked: Boolean) {
        val user = auth.currentUser ?: return
        val updated = alarm.copy(enabled = checked)
        AlarmRepository.saveAlarm(
            userId = user.uid,
            alarm = updated,
            onSuccess = { saved ->
                alarms = alarms.map { existing -> if (existing.id == saved.id) saved else existing }
                LocalAlarmCache.saveAlarms(context, alarms)
                if (checked) AlarmScheduler.schedule(context, saved) else AlarmScheduler.cancel(context, saved.id)
                exactAlarmEnabled = canScheduleExactAlarms(context)
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun updateSubmissionWakeStatus(submission: WakeSubmission, wakeStatus: String) {
        val userId = auth.currentUser?.uid ?: return
        AlarmRepository.updateSubmissionWakeStatus(
            userId = userId,
            submissionId = submission.id,
            wakeStatus = wakeStatus,
            onSuccess = {
                val updated = submission.copy(wakeStatus = wakeStatus)
                submissions = submissions.map { existing -> if (existing.id == updated.id) updated else existing }
                selectedSubmission = updated
                WakeSubmissionCache.upsertSubmission(context, updated)
                WakeWidgetUpdater.updateAll(context)
                if (wakeStatus != "pending") {
                    WakeFollowUpScheduler.cancel(context, userId, submission.id)
                }
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    val palette = ThemePaletteManager.currentPalette(context)
    val streak = WakeStatsCalculator.calculate(submissions).currentStreak

    if (creatingAlarm || editingAlarm != null) {
        WakeAlarmEditorDialog(
            initialAlarm = editingAlarm,
            onDismiss = {
                creatingAlarm = false
                editingAlarm = null
            },
            onDelete = editingAlarm?.let { alarm -> { deleteAlarm(alarm) } },
            onSave = { name, hour, minute, repeatDays ->
                saveAlarm(editingAlarm, name, hour, minute, repeatDays)
            }
        )
    }
    selectedSubmission?.let { submission ->
        WakeSubmissionDetailDialog(
            submission = submission,
            onDismiss = { selectedSubmission = null },
            onSetWakeStatus = { status -> updateSubmissionWakeStatus(submission, status) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.wake_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            WakeSectionCard(stringResource(R.string.wake_streak_title), stringResource(R.string.wake_streak_subtitle)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(palette.primary), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (streak > 0) streak.toString() else "0",
                            color = Color(ThemePaletteManager.readableTextColorOn(palette.primary, palette)),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = if (streak == 1) stringResource(R.string.wake_streak_day) else stringResource(R.string.wake_streak_days, streak),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(if (exactAlarmEnabled) R.string.wake_exact_alarm_enabled else R.string.wake_exact_alarm_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            WakeSectionCard(
                stringResource(R.string.wake_create_alarm),
                stringResource(if (exactAlarmEnabled) R.string.wake_intro else R.string.wake_exact_alarm_disabled)
            ) {
                Button(
                    onClick = {
                        if (!canScheduleExactAlarms(context)) {
                            context.startActivity(android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } else {
                            creatingAlarm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.wake_create_alarm))
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.wake_create_alarm),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (alarms.isEmpty()) {
            item { WakeEmptyCard(stringResource(R.string.wake_empty)) }
        } else {
            items(alarms, key = { it.id }) { alarm ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingAlarm = alarm },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(alarm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(WakeSubmissionUi.formatAlarmTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formatAlarmSchedule(context, alarm.repeatDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(if (alarm.enabled) R.string.wake_alarm_active else R.string.wake_alarm_inactive),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (alarm.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = alarm.enabled, onCheckedChange = { toggleAlarm(alarm, it) })
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wake_history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (submissions.isEmpty()) {
            item { WakeEmptyCard(stringResource(R.string.wake_history_empty)) }
        } else {
            items(submissions.take(2), key = { it.id }) { submission ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSubmission = submission },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(WakeSubmissionUi.typeLabel(context, submission), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = context.getString(
                                R.string.wake_history_alarm_meta,
                                WakeSubmissionUi.formatAlarmTime(submission.alarmHour, submission.alarmMinute),
                                WakeSubmissionUi.formatDuration(context, submission.responseDurationMs)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = context.getString(
                                R.string.wake_history_date_label,
                                WakeSubmissionUi.formatDate(WakeSubmissionUi.primaryTimestamp(submission))
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(submission.feedback, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onShowFullHistory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wake_history_view_full))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun WakeHistoryRoute() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var allSubmissions by remember { mutableStateOf<List<WakeSubmission>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableIntStateOf(0) }
    var timeFilter by remember { mutableIntStateOf(0) }
    var durationFilter by remember { mutableIntStateOf(0) }
    var statusFilter by remember { mutableIntStateOf(0) }
    var selectedSubmission by remember { mutableStateOf<WakeSubmission?>(null) }

    LaunchedEffect(Unit) {
        val user = auth.currentUser ?: return@LaunchedEffect
        AlarmRepository.getSuccessfulSubmissions(
            userId = user.uid,
            onSuccess = {
                errorText = null
                allSubmissions = it
            },
            onFailure = {
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
                allSubmissions = emptyList()
            }
        )
    }

    fun updateSubmissionWakeStatus(submission: WakeSubmission, wakeStatus: String) {
        val userId = auth.currentUser?.uid ?: return
        AlarmRepository.updateSubmissionWakeStatus(
            userId = userId,
            submissionId = submission.id,
            wakeStatus = wakeStatus,
            onSuccess = {
                val updated = submission.copy(wakeStatus = wakeStatus)
                allSubmissions = allSubmissions.map { existing -> if (existing.id == updated.id) updated else existing }
                selectedSubmission = updated
                WakeSubmissionCache.upsertSubmission(context, updated)
                WakeWidgetUpdater.updateAll(context)
                if (wakeStatus != "pending") {
                    WakeFollowUpScheduler.cancel(context, userId, submission.id)
                }
            },
            onFailure = {
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    selectedSubmission?.let { submission ->
        WakeSubmissionDetailDialog(
            submission = submission,
            onDismiss = { selectedSubmission = null },
            onSetWakeStatus = { status -> updateSubmissionWakeStatus(submission, status) }
        )
    }

    val filtered = allSubmissions.filter { submission ->
        val typeMatches = when (typeFilter) {
            1 -> submission.type == "text"
            2 -> submission.type == "image"
            else -> true
        }
        val now = System.currentTimeMillis()
        val ageMillis = maxOf(0L, now - WakeSubmissionUi.primaryTimestamp(submission))
        val timeMatches = when (timeFilter) {
            1 -> ageMillis <= 7L * 24L * 60L * 60L * 1000L
            2 -> ageMillis <= 30L * 24L * 60L * 60L * 1000L
            3 -> ageMillis <= 365L * 24L * 60L * 60L * 1000L
            else -> true
        }
        val durationSeconds = maxOf(0L, submission.responseDurationMs / 1000L)
        val durationMatches = when (durationFilter) {
            1 -> durationSeconds <= 10L
            2 -> durationSeconds in 11L..20L
            3 -> durationSeconds in 21L..30L
            4 -> durationSeconds in 31L..40L
            5 -> durationSeconds in 41L..50L
            6 -> durationSeconds >= 51L
            else -> true
        }
        val statusMatches = when (statusFilter) {
            1 -> submission.wakeStatus == "awake"
            2 -> submission.wakeStatus == "asleep"
            3 -> submission.wakeStatus == "pending"
            else -> true
        }
        typeMatches && timeMatches && durationMatches && statusMatches
    }

    val typeOptions = context.resources.getStringArray(R.array.wake_history_type_filters).toList()
    val timeOptions = context.resources.getStringArray(R.array.wake_history_time_filters).toList()
    val durationOptions = context.resources.getStringArray(R.array.wake_history_duration_filters).toList()
    val statusOptions = context.resources.getStringArray(R.array.wake_history_status_filters).toList()

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.wake_history_full_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wake_history_full_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            WakeHistoryFilter("Type", typeOptions, typeFilter) { typeFilter = it }
            WakeHistoryFilter("Time", timeOptions, timeFilter) { timeFilter = it }
            WakeHistoryFilter("Duration", durationOptions, durationFilter) { durationFilter = it }
            WakeHistoryFilter("Status", statusOptions, statusFilter) { statusFilter = it }
        }
        item {
            Text(
                text = context.resources.getQuantityString(R.plurals.wake_history_result_count, filtered.size, filtered.size),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (!errorText.isNullOrBlank()) {
            item {
                Text(text = errorText.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (filtered.isEmpty()) {
            item {
                Text(text = stringResource(R.string.wake_history_full_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(filtered, key = { it.id }) { submission ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSubmission = submission },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = WakeSubmissionUi.formatDate(WakeSubmissionUi.primaryTimestamp(submission)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = context.getString(R.string.wake_history_tile_alarm_time, WakeSubmissionUi.formatAlarmTime(submission.alarmHour, submission.alarmMinute)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(WakeSubmissionUi.typeLabel(context, submission), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(WakeSubmissionUi.wakeStatusLabel(context, submission), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun WakeSectionCard(
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun WakeEmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WakeHistoryFilter(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = options.getOrElse(selectedIndex) { options.firstOrNull().orEmpty() },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
            options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}

private fun formatAlarmSchedule(context: Context, days: List<Int>): String {
    val normalizedDays = days.sorted().distinct()
    return when (normalizedDays) {
        listOf(1, 2, 3, 4, 5, 6, 7) -> context.getString(R.string.wake_alarm_every_day)
        listOf(2, 3, 4, 5, 6) -> context.getString(R.string.wake_alarm_weekdays)
        listOf(1, 7) -> context.getString(R.string.wake_alarm_weekends)
        else -> normalizedDays.mapNotNull {
            when (it) {
                1 -> context.getString(R.string.day_sun)
                2 -> context.getString(R.string.day_mon)
                3 -> context.getString(R.string.day_tue)
                4 -> context.getString(R.string.day_wed)
                5 -> context.getString(R.string.day_thu)
                6 -> context.getString(R.string.day_fri)
                7 -> context.getString(R.string.day_sat)
                else -> null
            }
        }.joinToString(" | ")
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
